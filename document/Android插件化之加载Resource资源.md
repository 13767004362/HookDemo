# **Android插件化之Resource资源加载方案**

**目前插件化加载插件资源有两种方案**：

- 合并式的Resource方案： 将插件资源合并加载到宿主中
- 独立式的Resource方案： 创建插件新的Resource，与宿主隔离



## **合并式的Resource方案**

### **android 7.0 Framework 中Resource源码追踪**
先来了解下，android 7.0 Framework 层中Resource对象的创建过程。


从Activity或者Application获取Resource对象实际上，最终都是通过`ContextImpl#getResource()`获取的。


[ContextImpl类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ContextImpl.java)

getResource():
```
  @Override
    public Resources getResources() {
        return mResources;
    }
```
检索发现，Resource对象是在构建ContextImpl对象的时候获取到的（第1个的hook点，后续会用到）。

查看构造方法：
```
private Resources mResources;

private ContextImpl(ContextImpl container, ActivityThread mainThread,
            LoadedApk packageInfo, IBinder activityToken, UserHandle user, int flags,
            Display display, Configuration overrideConfiguration, int createDisplayWithId) {
        mPackageInfo = packageInfo;
         //...省略部分源码
         Resources resources = packageInfo.getResources(mainThread);
         //...省略部分源码
         mResources = resources;
}
```


接下来看下,LoadedApk又是如何创建Resource对象?

[LoadedApk类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/LoadedApk.java)

getResources():
```
  public Resources getResources(ActivityThread mainThread) {
        if (mResources == null) {
            mResources = mainThread.getTopLevelResources(mResDir, mSplitResDirs, mOverlayDirs,
                    mApplicationInfo.sharedLibraryFiles, Display.DEFAULT_DISPLAY, this);
        }
        return mResources;
    }
```
从上可知LoadedApk中也存在Resource对象（第2个的hook点，后续会用到）。

接下来，继续查看ActivityThread中又是如何创建Resource。

[ActivityThread类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ActivityThread.java)

getTopLevelResources():
```
Resources getTopLevelResources(String resDir, String[] splitResDirs, String[] overlayDirs,
            String[] libDirs, int displayId, LoadedApk pkgInfo) {
        return mResourcesManager.getResources(null, resDir, splitResDirs, overlayDirs, libDirs,
                displayId, null, pkgInfo.getCompatibilityInfo(), pkgInfo.getClassLoader());
}
```

从ResourceManager中获取，若是为空，则创建新的Resource对象。

[ResourcesManager类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ResourcesManager.java)

getResources():
```
  public @NonNull Resources getResources(@Nullable IBinder activityToken,
            @Nullable String resDir,
            @Nullable String[] splitResDirs,
            @Nullable String[] overlayDirs,
            @Nullable String[] libDirs,
            int displayId,
            @Nullable Configuration overrideConfig,
            @NonNull CompatibilityInfo compatInfo,
            @Nullable ClassLoader classLoader) {
        try {
            //存储
            final ResourcesKey key = new ResourcesKey(
                    resDir,
                    splitResDirs,
                    overlayDirs,
                    libDirs,
                    displayId,
                    overrideConfig != null ? new Configuration(overrideConfig) : null, // Copy
                    compatInfo);
            classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
            return getOrCreateResources(activityToken, key, classLoader);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_RESOURCES);
        }
    }
```
从以上，可以知会创建ResourcesKey对象，用于存储应用程序的信息。用于一个ResourcesKey对一个Resources。

接下来查看`getOrCreateResources()`

getOrCreateResources():
```
 private @NonNull Resources getOrCreateResources(@Nullable IBinder activityToken,
            @NonNull ResourcesKey key, @NonNull ClassLoader classLoader) {
       // ... 省略部分源码
        
        //重点在这里,会加载应用程序的资源文件
        ResourcesImpl resourcesImpl = createResourcesImpl(key);  
        synchronized (this) {
            ResourcesImpl existingResourcesImpl = findResourcesImplForKeyLocked(key);
            if (existingResourcesImpl != null) {
                if (DEBUG) {
                    Slog.d(TAG, "- got beat! existing impl=" + existingResourcesImpl
                            + " new impl=" + resourcesImpl);
                }
                resourcesImpl.getAssets().close();
                resourcesImpl = existingResourcesImpl;
            } else {
                // 将Resources的实现类,和ResourcesKey一对一缓存起来。
                mResourceImpls.put(key, new WeakReference<>(resourcesImpl));
            }

            final Resources resources;
            if (activityToken != null) {
                resources = getOrCreateResourcesForActivityLocked(activityToken, classLoader,
                        resourcesImpl);
            } else {
                //将ResourceImpls作为参数，构建一个新Resources对象
                resources = getOrCreateResourcesLocked(classLoader, resourcesImpl);
            }
            return resources;
        }
}
```
 ResourcesImpl是 Resources的实现类，具体加载资源和获取资源信息的操作都在里面。(第3个Hook点，后续会用到)
 
 接下来，查看，如何创建 ResourcesImpl对象。
 
 createResourcesImpl():
 ```
  private @NonNull ResourcesImpl createResourcesImpl(@NonNull ResourcesKey key) {
        final DisplayAdjustments daj = new DisplayAdjustments(key.mOverrideConfiguration);
        daj.setCompatibilityInfo(key.mCompatInfo);
        //接下来重点,资源文件会被在AssetManager加载。
        final AssetManager assets = createAssetManager(key);
        final DisplayMetrics dm = getDisplayMetrics(key.mDisplayId, daj);
        final Configuration config = generateConfig(key, dm);
        final ResourcesImpl impl = new ResourcesImpl(assets, dm, config, daj);
        return impl;
    }
 ```
 
接下来，查看AssetManager对象的创建过程，如何加载资源文件。

createAssetManager():
```
 protected @NonNull AssetManager createAssetManager(@NonNull final ResourcesKey key) {
        AssetManager assets = new AssetManager();
        //res资源主目录的路径
        if (key.mResDir != null) {
            if (assets.addAssetPath(key.mResDir) == 0) {
                throw new Resources.NotFoundException("failed to add asset path " + key.mResDir);
            }
        }
        //res资源拼接目录的路径
        if (key.mSplitResDirs != null) {
            for (final String splitResDir : key.mSplitResDirs) {
                if (assets.addAssetPath(splitResDir) == 0) {
                    throw new Resources.NotFoundException(
                            "failed to add split asset path " + splitResDir);
                }
            }
        }
        //.... 省略部分源码
        return assets;
    }
```
从上可知,会通过AssetManager对象的`addAssetPath()`加载资源文件。




根据一系列的源码追踪了解到，根据资源文件路径，创建AssetManager对象，从而进一步创建Resource对象。


----------


### **合并宿主与插件资源的实现方案**

**思路**：
>将插件的资源加载到宿主应用程序中，合并加载到虚拟机中，这样宿主就可以正常访问插件中的资源。

**缺点**： 存在宿主与插件的资源冲突。

**解决方式**：通过[Android插件化之aapt修改资源前缀](https://github.com/13767004362/HookDemo/blob/master/aapt/Android%E6%8F%92%E4%BB%B6%E5%8C%96%E4%B9%8Baapt%E4%BF%AE%E6%94%B9%E8%B5%84%E6%BA%90%E5%89%8D%E7%BC%80.md)

**代码实现如下**

**1. 将插件的资源合并到宿主中,创建新合并后的Resource替换掉宿主原有的Resource对象**。

根据源码走向,了解到反射替换宿主原有的Resource对象,有以下若干Hook点:

- ContextImpl 中Resource对象
- LoadedApk中Resource对象
- ResourceManager中resource对象(sdk版本差异处理)

代码如下所示：
```

    private synchronized static void preloadResource(Context context, String apkFilePath) {
        try {
            // 先创建AssetManager
            Class<? extends AssetManager> AssetManagerClass = AssetManager.class;
            AssetManager assetManager = AssetManagerClass.newInstance();
            // 将插件资源和宿主资源通过 addAssetPath方法添加进去
            Method addAssetPathMethod = AssetManagerClass.getDeclaredMethod("addAssetPath", String.class);
            addAssetPathMethod.setAccessible(true);
            String hostResourcePath = context.getPackageResourcePath();
            int result_1 = (int) addAssetPathMethod.invoke(assetManager, hostResourcePath);
            int result_2 = (int) addAssetPathMethod.invoke(assetManager, apkFilePath);
            // 接下来创建，合并资源后的Resource
            Resources resources = new Resources(assetManager, context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());
            // 替换 ContextImpl 中Resource对象
            Class<?> contextImplClass = context.getClass();
            Field resourcesField1 = contextImplClass.getDeclaredField("mResources");
            resourcesField1.setAccessible(true);
            resourcesField1.set(context, resources);
            // 先获取到LoadApk对象
            Field loadedApkField = contextImplClass.getDeclaredField("mPackageInfo");
            loadedApkField.setAccessible(true);
            Object loadApk = loadedApkField.get(context);
            Class<?> loadApkClass = loadApk.getClass();
            // 替换掉LoadApk中的Resource对象。
            Field resourcesField2 = loadApkClass.getDeclaredField("mResources");
            resourcesField2.setAccessible(true);
            resourcesField2.set(loadApk, resources);

            //获取到ActivityThread
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object ActivityThread = sCurrentActivityThreadField.get(null);
            // 获取到ResourceManager对象
            Field ResourcesManagerField = ActivityThreadClass.getDeclaredField("mResourcesManager");
            ResourcesManagerField.setAccessible(true);
            Object resourcesManager = ResourcesManagerField.get(ActivityThread);
            // 替换掉ResourceManager中resource对象
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Class<?> resourcesManagerClass = resourcesManager.getClass();
                Field mActiveResourcesField = resourcesManagerClass.getDeclaredField("mActiveResources");
                mActiveResourcesField.setAccessible(true);
                Map<Object, WeakReference<Resources>> map = (Map<Object, WeakReference<Resources>>) mActiveResourcesField.get(resourcesManager);
                Object key = map.keySet().iterator().next();
                map.put(key, new WeakReference<>(resources));
            } else {
                // still hook Android N Resources, even though it's unnecessary, then nobody will be strange.
                Class<?> resourcesManagerClass = resourcesManager.getClass();
                Field mResourceImplsField = resourcesManagerClass.getDeclaredField("mResourceImpls");
                mResourceImplsField.setAccessible(true);
                Map map = (Map) mResourceImplsField.get(resourcesManager);
                Object key = map.keySet().iterator().next();
                Field mResourcesImplField = Resources.class.getDeclaredField("mResourcesImpl");
                mResourcesImplField.setAccessible(true);
                Object resourcesImpl = mResourcesImplField.get(resources);
                map.put(key, new WeakReference<>(resourcesImpl));
            }
            multiResources = resources;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

**2. 根据插件中包名、资源目录和文件获取到资源id , 在宿主中加载资源id获取到对应的资源**


```
private void usePluginResource() {
        ImageView imageView = findViewById(R.id.main_show_plugin_img_iv);
        // 根据包名、资源目录和文件获取到资源id
        int imgId=ResourceHookManager.getDrawableId("plugin_img", PluginConfig.package_name);
        // 宿主中使用插件的资源id
        imageView.setImageDrawable(getResources().getDrawable(imgId));
}
```

## 除此之外，还有一种独立式的Resource方案。

**优点**：资源不存在冲突，不需要特殊处理。

**缺点**：存在插件、宿主之间资源信息共享问题。

**步骤如下**：

1. 先根据apk或者zip的路径(包含资源文件)，创建出独立的Resource对象。
```
public class ResourceHookManager {
  private static  Resources resources;
    public static void init(Context context, String apkFilePath) {
        preloadResource(context, apkFilePath);
    }
    private synchronized static void preloadResource(Context context, String apkFilePath) {
        try {
            // 先创建AssetManager
            AssetManager assetManager = AssetManager.class.newInstance();
            AssetManager.class.getDeclaredMethod("addAssetPath", String.class).invoke(
                    assetManager,apkFilePath);
            //在创建Resource
            resources=new Resources(assetManager,context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static Drawable getDrawable(String name,String packageName){
       int imgId=getId(name,"mipmap",packageName);
       if (imgId==0){
           imgId=getId(name,"drawable",packageName);
       }
       return resources.getDrawable(imgId);
    }
    public static int  getId(String name,String type,String packageName){
      return resources.getIdentifier(name,type,packageName);
    }
}

```
2. 根据Resouce对象,获取相应的资源:
```
    private void usePluginResource() {
        ImageView imageView = findViewById(R.id.main_show_plugin_img_iv);
        imageView.setImageDrawable(ResourceHookManager.getDrawable("plugin_img", PluginConfig.package_name));
    }
```
