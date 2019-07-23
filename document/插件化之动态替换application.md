
# **Application动态替换**

### **先来了解一下Application的创建过程**。

启动应用进程后,会通知AMS,最终回到ActivityThread中的Handler处理，H.BIND_APPLICATION标识对应的动作,去开始创建Application对象。

Handler中回调处理：
```java
private class H extends Handler {
    
    public void handleMessage(Message msg) {
            switch (msg.what) {
                 case BIND_APPLICATION:
                    AppBindData data = (AppBindData)msg.obj;
                    handleBindApplication(data);
                    break;
            }
        
    }
}
```
接下来，调用handleBindApplication进一步处理。

handleBindApplication():
```java
 private void handleBindApplication(AppBindData data) {
     //初始化一系列的参数，例如，日期，AsyncTask配置。
      // 创建Instrumentation对象和ContextImpl对象
      
       data.info = getPackageInfoNoCheck(data.appInfo, data.compatInfo);
      //... 省略部分源码
       try {
            // data.info是LoadeApk对象,会通过ClassLoader反射创建Application的实例对象
            Application app = data.info.makeApplication(data.restrictedBackupMode, null);
            // Hook点
            mInitialApplication = app;
           //安装ContentProvider 会比Application的onCreate()先执行
            if (!data.restrictedBackupMode) {
                if (!ArrayUtils.isEmpty(data.providers)) {
                    installContentProviders(app, data.providers);
                    mH.sendEmptyMessageDelayed(H.ENABLE_JIT, 10*1000);
                }
            }
            try {
                // 调用Application的onCreate()
                mInstrumentation.callApplicationOnCreate(app);
            } catch (Exception e) {
                if (!mInstrumentation.onException(app, e)) {
                    throw new RuntimeException(
                        "Unable to create application " + app.getClass().getName()
                        + ": " + e.toString(), e);
                }
            }
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
}
```
从上可知,会创建Application类对象,接着安装ContentProvider,再调用Application的onCreate()。



接下来，看下LoadeApk的`makeApplication()`的如何创建Application还是Application子类对象的。


[LoadedApk类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/LoadedApk.java)(来源：`frameworks/base/core/java/android/app/LoadedApk`)

makeApplication():
```java
  public Application makeApplication(boolean forceDefaultAppClass,
            Instrumentation instrumentation) {
        if (mApplication != null) {
            return mApplication;
        }
        Application app = null;
        String appClass = mApplicationInfo.className;
        // 限制模式或者没有自定义Application的子类情况下,会使用默认的Application.
        if (forceDefaultAppClass || (appClass == null)) {
            appClass = "android.app.Application";
        }
        try {
            //获取到当前的classLoader对象
            java.lang.ClassLoader cl = getClassLoader();
            if (!mPackageName.equals("android")) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        "initializeJavaContextClassLoader");
                initializeJavaContextClassLoader();
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
            //为Application创建Context对象
            ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
            app = mActivityThread.mInstrumentation.newApplication(
                    cl, appClass, appContext);
            // Hook点
            appContext.setOuterContext(app);
        } catch (Exception e) {
            if (!mActivityThread.mInstrumentation.onException(app, e)) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                throw new RuntimeException(
                    "Unable to instantiate application " + appClass
                    + ": " + e.toString(), e);
            }
        }
         // Hook点
        mActivityThread.mAllApplications.add(app);
         // Hook点
        mApplication = app;
        //... 省略部分源码
        return app;
    }
```

从上面可知，当创建Application类对象成功后,会三个地方持有该对象,因此存在三个hook点,需要替换掉。

接下来,看下Instrumentation的`newApplication()`。

[Instrumentation类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/Instrumentation.java)
newApplication():
```java
  public Application newApplication(ClassLoader cl, String className, Context context)
            throws InstantiationException, IllegalAccessException, 
            ClassNotFoundException {
        return newApplication(cl.loadClass(className), context);
    }
    static public Application newApplication(Class<?> clazz, Context context)
            throws InstantiationException, IllegalAccessException, 
            ClassNotFoundException {
        Application app = (Application)clazz.newInstance();
        app.attach(context);
        return app;
    }
```
从上述可知，会调用Application生命周期中的`attch()`方法。

```java
   /**
     * @hide
     */
    /* package */ final void attach(Context context) {
        attachBaseContext(context);
        mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
    }
```

从上可知,`attachBaseContext()`会比`onCreate()`先执行

**注意点**：这里context对象是ContextImpl对象。


还记得，上面的代码嘛?当Application对象被创建好后，会继续执行`  mInstrumentation.callApplicationOnCreate(app);`。

接下来，继续查看Instrumentation`callApplicationOnCreate()`。

callApplicationOnCreate():
```java
public void callApplicationOnCreate(Application app) {
        app.onCreate();
}
```
从以上可知：Application的生命周期`onCreate()`被调用。


查看了Application的创建过程，发现有4个Hook点，和ApplicationInfo中className
需要修改。


### **编码实现动态替换Application**

**疑难杂症**：

1. 创建且调用模拟插件中Application的声明周期
2. 修改宿主中Application的对象,4个Hook点
3. 处理ContentProvider先于Application#onCreate()执行的问题。

带着以上问题，针对性处理,这样就实现了Application的动态代理。

编写插件：
```java
public class DelegateApplication extends Application {
private static final String TAG=DelegateApplication.class.getSimpleName();
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.i(TAG,TAG+"执行 attachBaseContext() ");
    }
}
```
在AndroidManifest.xml中注册：
```java
    <meta-data
            android:name="application"
            android:value="com.xingen.plugin.DelegateApplication">
    </meta-data>
```

接下来，在宿主中，编写Hook大法：

先解析插件中meta标签，获取Application的名字。
```java
   /**
     * 解析出插件中meta信息
     *
     * @param apkFilePath
     */
    private static void preloadMeta(String apkFilePath) {
        try {
            // 先获取PackageParser对象
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            Object packageParser = packageParserClass.newInstance();
            //接着获取PackageParser.Package
            Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
            parsePackageMethod.setAccessible(true);
            Object packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS);
            // 获取 Bundle mAppMetaData 对象
            Class<?> packageParser$package_Class = packageParser$package.getClass();
            Field mAppMetaDataFiled = packageParser$package_Class.getDeclaredField("mAppMetaData");
            mAppMetaDataFiled.setAccessible(true);
            Bundle mAppMetaData = (Bundle) mAppMetaDataFiled.get(packageParser$package);
            if (mAppMetaData != null && mAppMetaData.containsKey(PluginConfig.meta_application_key)) {
                delegateApplicationName = mAppMetaData.getString(PluginConfig.meta_application_key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

接下来，开始Hook：
```java
 /**
     * hook 替换原有的代理Application
     *
     * @param application
     */
    public static Application setDelegateApplication(Application application) {
        if (TextUtils.isEmpty(delegateApplicationName)) {
            return application;
        }
        try {
            // 先获取到ContextImpl对象
            Context contextImpl = application.getBaseContext();
            // 创建插件中真实的Application且，执行生命周期
            ClassLoader classLoader = application.getClassLoader();
            Class<?> applicationClass = classLoader.loadClass(delegateApplicationName);
            delegateApplication = (Application) applicationClass.newInstance();
            Method attachMethod = Application.class.getDeclaredMethod("attach", Context.class);
            attachMethod.setAccessible(true);
            attachMethod.invoke(delegateApplication, contextImpl);

            // 替换ContextImpl的代理Application
            Class contextImplClass = contextImpl.getClass();
            Method setOuterContextMethod = contextImplClass.getDeclaredMethod("setOuterContext", Context.class);
            setOuterContextMethod.setAccessible(true);
            setOuterContextMethod.invoke(contextImpl, delegateApplication);
            // 替换LoadedApk的代理Application
            Field loadedApkField = contextImplClass.getDeclaredField("mPackageInfo");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(contextImpl);
            Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
            Field mApplicationField = loadedApkClass.getDeclaredField("mApplication");
            mApplicationField.setAccessible(true);
            mApplicationField.set(loadedApk, delegateApplication);

            // 替换ActivityThread的代理Application
            Field mMainThreadField = contextImplClass.getDeclaredField("mMainThread");
            mMainThreadField.setAccessible(true);
            Object mMainThread = mMainThreadField.get(contextImpl);
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(mMainThread, delegateApplication);
            Field mAllApplicationsField = activityThreadClass.getDeclaredField("mAllApplications");
            mAllApplicationsField.setAccessible(true);
            ArrayList<Application> mAllApplications = (ArrayList<Application>) mAllApplicationsField.get(mMainThread);
            mAllApplications.remove(application);
            mAllApplications.add(delegateApplication);

            // 替换LoadedApk中的mApplicationInfo中name
            Field mApplicationInfoField = loadedApkClass.getDeclaredField("mApplicationInfo");
            mApplicationInfoField.setAccessible(true);
            ApplicationInfo applicationInfo = (ApplicationInfo) mApplicationInfoField.get(loadedApk);
            applicationInfo.className = delegateApplicationName;
            delegateApplication.onCreate();
            replaceContentProvider(mMainThread, delegateApplication);
            // 标记动态替换Application完成
            isInit = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return delegateApplication != null ? delegateApplication : application;
    }
```
当然，ContentProvider会持有代理的Application,需要特殊处理一下：
```java
 /**
     * 修改已经存在ContentProvider中application
     *
     * @param activityThread
     * @param delegateApplication
     */
    private static void replaceContentProvider(Object activityThread, Application delegateApplication) {
        try {
            Field mProviderMapField = activityThread.getClass().getDeclaredField("mProviderMap");
            mProviderMapField.setAccessible(true);
            Map<Object, Object> mProviderMap = (Map<Object, Object>) mProviderMapField.get(activityThread);
            Set<Map.Entry<Object, Object>> entrySet = mProviderMap.entrySet();
            for (Map.Entry<Object, Object> entry : entrySet) {
                // 取出ContentProvider
                Object providerClientRecord = entry.getValue();
                Field mLocalProviderField = providerClientRecord.getClass().getDeclaredField("mLocalProvider");
                mLocalProviderField.setAccessible(true);
                ContentProvider contentProvider = (ContentProvider) mLocalProviderField.get(providerClientRecord);
                if (contentProvider!=null){
                    // 修改ContentProvider中的context
                    Field contextField = Class.forName("android.content.ContentProvider").getDeclaredField("mContext");
                    contextField.setAccessible(true);
                    contextField.set(contentProvider, delegateApplication);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
```
在宿主中Application子类中调用：
```java
public class ProxyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //解析插件中的Application,动态替换
        ApplicationHook.init( PluginConfig.getZipFilePath(this),this);
    }
    
}
```

最后在Activity中,测试验证：
```java
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_hook_application_btn:
                Application application=getApplication();
                Toast.makeText(getApplicationContext()," Application的类名是： "+application.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
        }
    }
```

资源参考：

- [Dex 加密之 Application 替换](https://blog.csdn.net/xiangzhihong8/article/details/79724978)
- [Android动态替换Application实现](https://blog.csdn.net/qq_18983205/article/details/85161715)
