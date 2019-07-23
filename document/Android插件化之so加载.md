

#### **应用层APK优化之减少lib**

常见的应用程序开发,将在libs下创建armeabi-v7a或者armeabi目录,拷贝若干的so库,防止apk体积过大。

在Moddule的build.gradle中配置：

```java
  defaultConfig {
  
    ndk {
            abiFilters("armeabi", "armeabi-v7a", "x86", "mips")
    }
    
  }
  sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
  }
```


常用的架构如下：
>armeabi，armeabi-v7a，x86，mips，arm64-v8a，mips64，x86_64


#### 关于so库的一些常识

**宿主与插件中so库加载区别** :

- **宿主应用程序** ：build 生成apk ，不需要开发者自己去判断ABI，Android系统在安装APK的时候，不会安装APK里面全部的so库文件，而是会根据当前CPU类型支持的ABI，从APK里面拷贝最合适的so库，并保存在APP的内部存储路径的libs 下面。

- **插件应用程序apk** : 动态加载插件的so，需要我们判断ABI类型来加载相应的so，Android系统不会帮我们处理。


**加载so的两种方式** :

- `System.load()`:参数必须为库文件的绝对路径(注意点：不能放在sdcard中)

- `System.loadLibrary()`: 参数为库文件名，不包含库文件的扩展名

接下来,根据framework层中的源码,来了解native是如何加载，如何查找的。


### **PathClassLoader是如何加载代码过程**

应用程序会通过PathClassLoader加载java和c++的代码,
查看PathClassLoader又是如何创建的。

[PathClassLoaderFactory类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/com/android/internal/os/PathClassLoaderFactory.java)

createClassLoader():
```java
  public static PathClassLoader createClassLoader(String dexPath,
                                                    String librarySearchPath,
                                                    String libraryPermittedPath,
                                                    ClassLoader parent,
                                                    int targetSdkVersion,
                                                    boolean isNamespaceShared) {
        PathClassLoader pathClassloader = new PathClassLoader(dexPath, librarySearchPath, parent);
        
        createClassloaderNamespace(pathClassloader,targetSdkVersion,
                          librarySearchPath,libraryPermittedPath,isNamespaceShared);
        return pathClassloader;
    }
```

[PathClassLoader类](https://www.androidos.net.cn/android/7.0.0_r31/xref/libcore/dalvik/src/main/java/dalvik/system/PathClassLoader.java)

构造方法
```java
public class PathClassLoader extends BaseDexClassLoader {
    
     public PathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, null, librarySearchPath, parent);
    }
}
```
从上可知,传递dex文件路径,so库文件路径，和父ClassLoader参数给BaseDexClassLoader。


[BaseDexClassLoader](https://www.androidos.net.cn/android/7.0.0_r31/xref/libcore/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java)

BaseDexClassLoader的构造方法
```java
public class BaseDexClassLoader extends ClassLoader {
    private final DexPathList pathList;
    
    public BaseDexClassLoader(String dexPath, File optimizedDirectory,
            String librarySearchPath, ClassLoader parent) {
        super(parent);
        this.pathList = new DexPathList(this, dexPath, librarySearchPath, optimizedDirectory);
    }
}
```
从上可知,是将代码路径构建一个DexPathList对象，根据类名查找Class对象，也是通过DexPathList对象来查找的。

接下来，查看DexPathList这个类。

[DexPathList](https://www.androidos.net.cn/android/7.0.0_r31/xref/libcore/dalvik/src/main/java/dalvik/system/DexPathList.java)

构造方法
```java
 public DexPathList(ClassLoader definingContext, String dexPath,
            String librarySearchPath, File optimizedDirectory) {
                
         //存放BaseDexClassLoader的Dex文件路径
        this.dexElements = makeDexElements(splitDexPath(dexPath), optimizedDirectory,
                                           suppressedExceptions, definingContext);
        // 会按顺序收集好应用程序的c++库和系统的c++库 , Hook关键点
        this.nativeLibraryDirectories = splitPaths(librarySearchPath, false);
        this.systemNativeLibraryDirectories =
                splitPaths(System.getProperty("java.library.path"), true);
        List<File> allNativeLibraryDirectories = new ArrayList<>(nativeLibraryDirectories);
        allNativeLibraryDirectories.addAll(systemNativeLibraryDirectories);
        //会统一存放好相应的c++库, Hook关键点
        this.nativeLibraryPathElements = makePathElements(allNativeLibraryDirectories,
                                                          suppressedExceptions,
                                                          definingContext);
        
}
```

从以上可知,应用程序中c++ so库和系统需要so库都会统计,保存在nativeLibraryPathElements中。


先来看下c++层代码存放调用的`makePathElements()`:
```java
private static Element[] makePathElements(List<File> files,
                                              List<IOException> suppressedExceptions,
                                              ClassLoader loader) {
        return makeElements(files, null, suppressedExceptions, true, loader);
}
```
从上可知，不管是c++层的代码和java代码的存放过程，最终走向`makePathElements()`。

```java
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

从可知 : java代码是将DexFile对象存放到Element中，c++代码是将目录存放到Element中。





#### **c++ native层代码的查找过程**：

这里以`System.loadLibrary()`为例子。


查看`System#loadLibrary()`:
```java
public static void loadLibrary(String libname) {
        Runtime.getRuntime().loadLibrary0(VMStack.getCallingClassLoader(), libname);
}
```
接下来,查看`Runtime#loadLibrary0()`
```java
 synchronized void loadLibrary0(ClassLoader loader, String libname) {
        String libraryName = libname;
        if (loader != null) {
            String filename = loader.findLibrary(libraryName);
            if (filename == null) {
                // It's not necessarily true that the ClassLoader used
                // System.mapLibraryName, but the default setup does, and it's
                // misleading to say we didn't find "libMyLibrary.so" when we
                // actually searched for "liblibMyLibrary.so.so".
                throw new UnsatisfiedLinkError(loader + " couldn't find \"" +
                                               System.mapLibraryName(libraryName) + "\"");
            }
            String error = doLoad(filename, loader);
            if (error != null) {
                throw new UnsatisfiedLinkError(error);
            }
            return;
        }
    
    }
```
从上可知`System.loadLibrary();`最终走向了,ClassLoader的`findLibrary()`。

从以上可知,应用程序具备唯一的PathClassLoader。

查看`BaseDexClassLoader#findLibrary()`
```java
    @Override
    public String findLibrary(String name) {
        return pathList.findLibrary(name);
    }
```
继续查看`DexPathList#findLibrary()`

```java
 public String findLibrary(String libraryName) {
        String fileName = System.mapLibraryName(libraryName);
        // 从中查找保存的so库中查找，关键点
        for (Element element : nativeLibraryPathElements) {
            String path = element.findNativeLibrary(fileName);

            if (path != null) {
                return path;
            }
        }

        return null;
    }
```

从这里，可知，只要将插件中的so库放到nativeLibraryPathElements中，就可以自然而然的加载插件中c++代码了。

继续查看,DexPathList中内部类Element的`findNativeLibrary()`，如何返回路径:
```java
      public String findNativeLibrary(String name) {
            maybeInit();

            if (isDirectory) {
                String path = new File(dir, name).getPath();
                if (IoUtils.canOpenReadOnly(path)) {
                    return path;
                }
            } else if (urlHandler != null) {
                // Having a urlHandler means the element has a zip file.
                // In this case Android supports loading the library iff
                // it is stored in the zip uncompressed.

                String entryName = new File(dir, name).getPath();
                if (urlHandler.isEntryStored(entryName)) {
                  return zip.getPath() + zipSeparator + entryName;
                }
            }

            return null;
        }
```

由上面,可知加载so库,也会走向PathClassLoader中,进行加载。

### **实战案例**

思考：

1. 将插件中so库解压到手机磁盘,不可放sdcard。
2. 获取到宿主中so库,与插件中so库合并收集。


在插件项目中,先编写c++代码文件
```c++
#include <string>
#include <jni.h>
using  namespace std;

extern "C"
//jstring是Java原生接口规定的数据类型，它是指向java字符串的指针
//
JNIEXPORT jstring

JNICALL
Java_com_xingen_plugin_NativeCodeTest_getContentFromJNI( JNIEnv* env,jobject){
    string content="android插件中通过jni与c++交互的结果";
    return  env->NewStringUTF(content.c_str());
};

```
通过studio build生成对应的cpu的so库,如下图所示：

![image](https://github.com/13767004362/HookDemo/blob/master/document/so%E5%BA%93.png)


接下来，通过`System.loadLibrary()`加载so库
```java
public class NativeCodeTest {
    static {
        System.loadLibrary("plugin_lib");
    }

    public native  String getContentFromJNI();

    public String getShowContent(){
        return getContentFromJNI();
    }
}
```

接下来,在宿主中编写。

先根据手机cpu进行筛选，加载相应的so库：
```java
           // 查询到so库中的文件目录
            File abi_file_dir = null;
            File dirFile = new File(librarySearchPath);
            File[] files = dirFile.listFiles();
            for (File file : files) {
                if (file != null && file.exists() && file.isDirectory()) {
                    final String abi = Build.CPU_ABI;
                    // 获取当前应用程序支持cpu(非手机cpu),配到对应的so库。
                    // 注意点： 若是宿主没有32位数Zygote，是无法加载 插件中32位so库。
                    if (file.getName().contains(abi)) {
                        abi_file_dir = file;
                        break;
                    }
                }
            }
            File mLibDir = null;
            try {
                // so库，不可以放在sdcard中。
                String mLibDirPath = context.getCacheDir() + File.separator + "lib" + File.separator + Build.CPU_ABI;
                mLibDir = new File(mLibDirPath);
                if (!mLibDir.exists()) {
                    mLibDir.mkdirs();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<File> pluginNativeLibraryDirList = new LinkedList<>();
            if (mLibDir != null && abi_file_dir != null) {
                File[] so_file_array = abi_file_dir.listFiles();
                for (File file : so_file_array) {
                    File so_file = new File(mLibDir.getAbsolutePath() + File.separator + file.getName());
                    Utils.copyFiles(file.getAbsolutePath(), so_file.getAbsolutePath());
                    pluginNativeLibraryDirList.add(mLibDir);
                }
            }
```
接下来,将宿主中so资源与插件so资源进行合并：
```java
 // 获取到DexPathList对象
            Class<?> baseDexClassLoaderClass = DexClassLoader.class.getSuperclass();
            Field pathListField = baseDexClassLoaderClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object dexPathList = pathListField.get(appClassLoader);
            /**
             * 接下来,合并宿主so,系统so,插件so库
             */
            Class<?> DexPathListClass = dexPathList.getClass();
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                // 先创建一个汇总so库的文件夹,收集全部
                List<File> allNativeLibDirList = new ArrayList<>();
                // 先添加插件的so库地址
                allNativeLibDirList.addAll(pluginNativeLibraryDirList);
                // 获取到宿主的so库地址
                Field nativeLibraryDirectoriesField = DexPathListClass.getDeclaredField("nativeLibraryDirectories");
                nativeLibraryDirectoriesField.setAccessible(true);
                List<File> old_nativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(dexPathList);
                allNativeLibDirList.addAll(old_nativeLibraryDirectories);
                // 获取到system的so库地址
                Field systemNativeLibraryDirectoriesField = DexPathListClass.getDeclaredField("systemNativeLibraryDirectories");
                systemNativeLibraryDirectoriesField.setAccessible(true);
                List<File> systemNativeLibraryDirectories = (List<File>) systemNativeLibraryDirectoriesField.get(dexPathList);
                allNativeLibDirList.addAll(systemNativeLibraryDirectories);
                //通过makePathElements获取到c++存放的Element
                Method makePathElementsMethod = DexPathListClass.getDeclaredMethod("makePathElements", List.class, List.class, ClassLoader.class);
                makePathElementsMethod.setAccessible(true);
                Object[] allNativeLibraryPathElements = (Object[]) makePathElementsMethod.invoke(null, allNativeLibDirList, new ArrayList<IOException>(), appClassLoader);
                //将合并宿主和插件的so库，重新设置进去
                Field nativeLibraryPathElementsField = DexPathListClass.getDeclaredField("nativeLibraryPathElements");
                nativeLibraryPathElementsField.setAccessible(true);
                nativeLibraryPathElementsField.set(dexPathList, allNativeLibraryPathElements);
            } else {
                // 获取到宿主的so库地址
                Field nativeLibraryDirectoriesField = DexPathListClass.getDeclaredField("nativeLibraryDirectories");
                nativeLibraryDirectoriesField.setAccessible(true);
                File[] oldNativeDirs = (File[]) nativeLibraryDirectoriesField.get(dexPathList);
                int oldNativeLibraryDirSize = oldNativeDirs.length;
                // 创建一个汇总宿主，插件的so库地址的数组
                File[] totalNativeLibraryDir = new File[oldNativeLibraryDirSize + pluginNativeLibraryDirList.size()];
                System.arraycopy(oldNativeDirs, 0, totalNativeLibraryDir, 0, oldNativeLibraryDirSize);
                for (int i = 0; i < totalNativeLibraryDir.length; ++i) {
                    totalNativeLibraryDir[oldNativeLibraryDirSize + i] = pluginNativeLibraryDirList.get(i);
                }
                // 替换成合并的so库资源数组
                nativeLibraryDirectoriesField.set(dexPathList, totalNativeLibraryDir);
            }
```

最后,加载插件中so库，进行测试:
```java
    private void useNativeLibrary(){
        try {
            Class<?> mClass=appMainClassLoader.loadClass(PluginConfig.native_class_name);
            Object instance= mClass.newInstance();
            Method  getShowContentMethod=mClass.getDeclaredMethod("getShowContent");
            getShowContentMethod.setAccessible(true);
           String content=(String) getShowContentMethod.invoke(instance);
           if (!TextUtils.isEmpty(content)){
               Toast.makeText(this,content,Toast.LENGTH_SHORT).show();
           }
        }catch (Exception e){
            e.printStackTrace();
        }

    }
```



资源参考：

- https://www.jianshu.com/p/ac96420fc82c
- https://blog.csdn.net/jiangwei0910410003/article/details/52312451
