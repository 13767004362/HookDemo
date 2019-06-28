# **Android插件化之ClassLoader加载插件Dex方案**

目前盛行的插件化方案中处理dex，有两种：

- 合并式的Dex,单个ClassLoader加载方案
- 多个ClassLoader加载dex方案


这里介绍，合并式dex的单个ClassLoader加载方案。


## **合并式的Dex加载方案**

### **android 7.0 Framework 中Dex加载源码追踪**


从Activity中获取ClassLoader对象，最终会走到`ContextImpl#getClassLoader()`。

[ContextImpl类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ContextImpl.java)

getClassLoader()：
```
@Override
    public ClassLoader getClassLoader() {
        return mPackageInfo != null ?
                mPackageInfo.getClassLoader() : ClassLoader.getSystemClassLoader();
    }
```
接下来，查看ClassLoader对象如何从LoadedApk中获取。

[LoadedApk类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/LoadedApk.java) 

getClassLoader()：
```
  public ClassLoader getClassLoader() {
        synchronized (this) {
            if (mClassLoader == null) {
                createOrUpdateClassLoaderLocked(null /*addedPaths*/);
            }
            return mClassLoader;
        }
    }
```

若是ClassLoader对象会为空，则会创建新的ClassLoader对象。

接下来，继续查看，createOrUpdateClassLoaderLocked():
```
 private void createOrUpdateClassLoaderLocked(List<String> addedPaths) {
        //系统程序的加载器
        if (mPackageName.equals("android")) {
            if (mClassLoader != null) {
                // nothing to update
                return;
            }

            if (mBaseClassLoader != null) {
                mClassLoader = mBaseClassLoader;
            } else {
                mClassLoader = ClassLoader.getSystemClassLoader();
            }

            return;
        }
        
        //.... 省略部分源码
        
        if (mClassLoader == null) {
            // Temporarily disable logging of disk reads on the Looper thread
            // as this is early and necessary.
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();

            mClassLoader = ApplicationLoaders.getDefault().getClassLoader(zip,
                    mApplicationInfo.targetSdkVersion, isBundledApp, librarySearchPath,
                    libraryPermittedPath, mBaseClassLoader);

            StrictMode.setThreadPolicy(oldPolicy);
            // Setup the class loader paths for profiling.
            needToSetupJitProfiles = true;
        } 
}
```
查看pplicationLoaders是如何创建ClassLoader对象的。

[ApplicationLoaders](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ApplicationLoaders.java):

getClassLoader():

```
   public ClassLoader getClassLoader(String zip, int targetSdkVersion, boolean isBundled,
                                      String librarySearchPath, String libraryPermittedPath,
                                      ClassLoader parent) {
        ClassLoader baseParent = ClassLoader.getSystemClassLoader().getParent();
        synchronized (mLoaders) {
            if (parent == null) {
                parent = baseParent;
            }
            if (parent == baseParent) {
                ClassLoader loader = mLoaders.get(zip);
                if (loader != null) {
                    return loader;
                }
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, zip);
                // 关键点
                PathClassLoader pathClassloader = PathClassLoaderFactory.createClassLoader(
                                                      zip,
                                                      librarySearchPath,
                                                      libraryPermittedPath,
                                                      parent,
                                                      targetSdkVersion,
                                                      isBundled);
                mLoaders.put(zip, pathClassloader);
                return pathClassloader;
            }

            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, zip);
            PathClassLoader pathClassloader = new PathClassLoader(zip, parent);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            return pathClassloader;
        }
    }
```
从上可知，宿主程序中有唯一的ClassLoader，通过PathClassLoader

[PathClassLoader](https://www.androidos.net.cn/android/7.0.0_r31/xref/libcore/dalvik/src/main/java/dalvik/system/PathClassLoader.java)
```
public class PathClassLoader extends BaseDexClassLoader {
    public PathClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, null, null, parent);
    }
    public PathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, null, librarySearchPath, parent);
    }
}

```
[BaseDexClassLoader](https://www.androidos.net.cn/android/7.0.0_r31/xref/libcore/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java)


构造方法：
```
  public BaseDexClassLoader(String dexPath, File optimizedDirectory,
            String librarySearchPath, ClassLoader parent) {
        super(parent);
        this.pathList = new DexPathList(this, dexPath, librarySearchPath, optimizedDirectory);
    }
```
[DexPathList](https://www.androidos.net.cn/android/7.0.0_r31/xref/libcore/dalvik/src/main/java/dalvik/system/DexPathList.java)
```
public DexPathList(ClassLoader definingContext, String dexPath,
            String librarySearchPath, File optimizedDirectory) {
        //... 省略部分源码
        this.definingContext = definingContext;

        ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
        // 用于保存dex文件
        this.dexElements = makeDexElements(splitDexPath(dexPath), optimizedDirectory,
                                           suppressedExceptions, definingContext);
        // so库的文件
        this.nativeLibraryDirectories = splitPaths(librarySearchPath, false);
        // 系统的so库文件
        this.systemNativeLibraryDirectories =
                splitPaths(System.getProperty("java.library.path"), true);
        List<File> allNativeLibraryDirectories = new ArrayList<>(nativeLibraryDirectories);
        allNativeLibraryDirectories.addAll(systemNativeLibraryDirectories);
        // 进程中需要用到全部so库
        this.nativeLibraryPathElements = makePathElements(allNativeLibraryDirectories,
                                                          suppressedExceptions,
                                                          definingContext);
    }

```

继续查看，`makeDexElements()`如何生成dex所存储的dexElements数组。（第一个Hook点，后续会用到）

```
 private static Element[] makeElements(List<File> files, File optimizedDirectory,
                                          List<IOException> suppressedExceptions,
                                          boolean ignoreDexFiles,
                                          ClassLoader loader) {
        Element[] elements = new Element[files.size()];
        int elementsPos = 0;
        /*
         * Open all files and load the (direct or contained) dex files
         * up front.
         */
        for (File file : files) {
            File zip = null;
            File dir = new File("");
            DexFile dex = null;
            String path = file.getPath();
            String name = file.getName();

            if (path.contains(zipSeparator)) {
                String split[] = path.split(zipSeparator, 2);
                zip = new File(split[0]);
                dir = new File(split[1]);
            } else if (file.isDirectory()) {
                // 关键点： 存放c++ 库的目录
                elements[elementsPos++] = new Element(file, true, null, null);
            } else if (file.isFile()) {
                if (!ignoreDexFiles && name.endsWith(DEX_SUFFIX)) {
                    //关键点：非zip和jar类型的dex文件
                    try {
                        dex = loadDexFile(file, optimizedDirectory, loader, elements);
                    } catch (IOException suppressed) {
                        System.logE("Unable to load dex file: " + file, suppressed);
                        suppressedExceptions.add(suppressed);
                    }
                } else {
                    zip = file;
                    if (!ignoreDexFiles) {
                        // 关键点： 带有.dex文件的压缩文件
                        try {
                            dex = loadDexFile(file, optimizedDirectory, loader, elements);
                        } catch (IOException suppressed) {
                            suppressedExceptions.add(suppressed);
                        }
                    }
                }
            } else {
                System.logW("ClassLoader referenced unknown path: " + file);
            }

            if ((zip != null) || (dex != null)) {
                elements[elementsPos++] = new Element(dir, false, zip, dex);
            }
        }
        if (elementsPos != elements.length) {
            elements = Arrays.copyOf(elements, elementsPos);
        }
        return elements;
    }
```
根据创建好的DexFile对象，生成 Element对象，存储起来。


继续查看，`loadDexFile()`:
```
  private static DexFile loadDexFile(File file, File optimizedDirectory, ClassLoader loader,
                                       Element[] elements)
            throws IOException {
        if (optimizedDirectory == null) {
            return new DexFile(file, loader, elements);
        } else {
            String optimizedPath = optimizedPathFor(file, optimizedDirectory);
            return DexFile.loadDex(file.getPath(), optimizedPath, 0, loader, elements);
        }
    }
```
从这里可知，根据传入的插件文件路径,释放dex的文件目录，classLoader对象，会创建出对应的DexFile对象。


**接下来，看下是ClassLoader如何根据类名加载类的？**

`BaseDexClassLoader#findClass()`:
```
public class BaseDexClassLoader extends ClassLoader {
    private final DexPathList pathList;
   
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        List<Throwable> suppressedExceptions = new ArrayList<Throwable>();
        Class c = pathList.findClass(name, suppressedExceptions);
        if (c == null) {
            ClassNotFoundException cnfe = new ClassNotFoundException("Didn't find class \"" + name + "\" on path: " + pathList);
            for (Throwable t : suppressedExceptions) {
                cnfe.addSuppressed(t);
            }
            throw cnfe;
        }
        return c;
    }
```

继续查看，`DexPathList#findClass()`:

```
    public Class findClass(String name, List<Throwable> suppressed) {
        for (Element element : dexElements) {
            DexFile dex = element.dexFile;
            if (dex != null) {
                 // 根据类名，在dex文件中找到对应的Class对象
                Class clazz = dex.loadClassBinaryName(name, definingContext, suppressed);
                if (clazz != null) {
                    return clazz;
                }
            }
        }
        if (dexElementsSuppressedExceptions != null) {
            suppressed.addAll(Arrays.asList(dexElementsSuppressedExceptions));
        }
        return null;
    }
```
DexPathList内部有一个叫做dexElements的数组，然后findClass的时候，会根据类名，遍历这个数组来查找Class对象。



### **技术实现方案**


**思考**：

先将插件转换成DexFile，接着将DexFile转换成Element对象最后，添加到宿主的DexPath数组中，这样可以加载出插件中的类。

**Hook点**：

- DexPathList对象中DexElements数组。

1. 先将插件的压缩包（.zip、.apk）转换成DexFile对象，进一步构建出Element对象，合并宿主的dex文件。
```
    public static void init(String zipFilePath, String optimizedDirectory) {
        try {
            // 先解压dex文件
            DexFile dexFile = DexParse.parseDex(zipFilePath, optimizedDirectory);
            // 将插件dex加载到主进程的classloader
            ClassLoader appClassLoader = ClassLoaderHookManager.class.getClassLoader();
            loadPluginDex(new File(zipFilePath), dexFile, appClassLoader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 加载插件的dex文件
     *
     * @param apkFile
     * @param dexFile
     * @param appClassLoader
     */
    private static void loadPluginDex(File apkFile, DexFile dexFile, ClassLoader appClassLoader) {
        try {
            Class<?> baseDexClassLoaderClass = DexClassLoader.class.getSuperclass();
            Field pathListField = baseDexClassLoaderClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            // 获取到DexPathList
            Object pathList = pathListField.get(appClassLoader);
            // ElementsField对象
            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] oldElementsArray = (Object[]) dexElementsField.get(pathList);
            // Elements 获取的类型
            Class<?> elementClass = oldElementsArray.getClass().getComponentType();

            Log.i(TAG, " 查询的类型 " + elementClass.getName());
            // 创建新的ElementsField数组
            Object[] newElementsArray = (Object[]) Array.newInstance(elementClass, oldElementsArray.length + 1);
            Object o;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // 构造插件Element(File file, boolean isDirectory, File zip, DexFile dexFile) 这个构造函数
                Constructor<?> constructor = elementClass.getConstructor(File.class, boolean.class, File.class, DexFile.class);
                o = constructor.newInstance(apkFile, false, apkFile, dexFile);
            } else {
                // 构造插件的 Element，构造函数参数：(DexFile dexFile, File file)
                Constructor<?> constructor = elementClass.getConstructor(DexFile.class, File.class);
                o = constructor.newInstance(dexFile, apkFile);
            }
            Object[] toAddElementArray = new Object[]{o};
            // 把原始的elements复制进去
            System.arraycopy(oldElementsArray, 0, newElementsArray, 0, oldElementsArray.length);
            // 插件的那个element复制进去
            System.arraycopy(toAddElementArray, 0, newElementsArray, oldElementsArray.length, toAddElementArray.length);
            // 替换
            dexElementsField.set(pathList, newElementsArray);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
```
DexParse工具类：
```
 private static final class DexParse {

        /**
         * 从.apk .jar .zip 解析出 dex 文件
         *
         * @param zipFilePath
         * @param optimizedDirectory
         * @return
         * @throws IOException
         */
        public static DexFile parseDex(String zipFilePath, String optimizedDirectory) throws IOException {
            String dexFilePath = optimizedPathFor(new File(zipFilePath), new File(optimizedDirectory));
            return DexFile.loadDex(zipFilePath, dexFilePath, 0);
        }

        /**
         * 构建dex文件路径
         */
        private static String optimizedPathFor(File path,
                                               File optimizedDirectory) {
            /*
             * Get the filename component of the path, and replace the
             * suffix with ".dex" if that's not already the suffix.
             *
             * We don't want to use ".odex", because the build system uses
             * that for files that are paired with resource-only jar
             * files. If the VM can assume that there's no classes.dex in
             * the matching jar, it doesn't need to open the jar to check
             * for updated dependencies, providing a slight performance
             * boost at startup. The use of ".dex" here matches the use on
             * files in /data/dalvik-cache.
             */
            final String DEX_SUFFIX = ".dex";
            String fileName = path.getName();
            if (!fileName.endsWith(DEX_SUFFIX)) {
                int lastDot = fileName.lastIndexOf(".");
                if (lastDot < 0) {
                    fileName += DEX_SUFFIX;
                } else {
                    StringBuilder sb = new StringBuilder(lastDot + 4);
                    sb.append(fileName, 0, lastDot);
                    sb.append(DEX_SUFFIX);
                    fileName = sb.toString();
                }
            }

            File result = new File(optimizedDirectory, fileName);
            return result.getPath();
        }
    }
```

接着，在在Application的子类中attachBase()进行调用：

```
public class BaseApplication extends Application {
    private String zipFilePath;
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
         loadPluginDex(base);
    }
    private void loadPluginDex(Context context) {
        // 先拷贝assets 下的apk，写入磁盘中。
        zipFilePath = Utils.copyFiles(context, PluginConfig.apk_file_name);
        String optimizedDirectory = Utils.getCacheDir(context).getAbsolutePath();
        // 加载插件dex到虚拟机中
        ClassLoaderHookManager.init(zipFilePath, optimizedDirectory);
    }
}
```



**资源参考**：

- [classLoder教程](http://weishu.me/2016/04/05/understand-plugin-framework-classloader/)。