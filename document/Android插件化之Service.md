
###  **先来了解Service的启动过程**

通过`startService()`启动服务,会来AMS检查进程是否启动,Intent对应的ServiceRecord信息,权限限制等操作，最后回到ActivityThread中,创建Service类对象。

接下来，查看下ActivityThread是如何创建Service对象的。

[ActivityThread](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ActivityThread.java)
```java
 private class ApplicationThread extends ApplicationThreadNative {
        
        public final void scheduleCreateService(IBinder token,
                ServiceInfo info, CompatibilityInfo compatInfo, int processState) {
            updateProcessState(processState, false);
            CreateServiceData s = new CreateServiceData();
            s.token = token;
            s.info = info;
            s.compatInfo = compatInfo;
            sendMessage(H.CREATE_SERVICE, s);
        }
 }
```
通过Handler回到应用程序进程里面,根据H.CREATE_SERVICE标识执行。
```java
    public void handleMessage(Message msg) {
       switch (msg.what) {
          case CREATE_SERVICE:
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, ("serviceCreate: " + String.valueOf(msg.obj)));
                    handleCreateService((CreateServiceData)msg.obj);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    break;
       }
    }
```

接下来,看下handleCreateService():
```java
  private void handleCreateService(CreateServiceData data) {
  
        LoadedApk packageInfo = getPackageInfoNoCheck(
                data.info.applicationInfo, data.compatInfo);
        Service service = null;
        try {
            java.lang.ClassLoader cl = packageInfo.getClassLoader();
            //创建Service对象
            service = (Service) cl.loadClass(data.info.name).newInstance();
        } catch (Exception e) {
        }
        try {
            ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
            context.setOuterContext(service);

            Application app = packageInfo.makeApplication(false, mInstrumentation);
            service.attach(context, this, data.info.name, data.token, app,
                    ActivityManagerNative.getDefault());
            // 执行Service的生命周期中onCreate()
            service.onCreate();
            mServices.put(data.token, service);
            try {
                ActivityManagerNative.getDefault().serviceDoneExecuting(
                        data.token, SERVICE_DONE_EXECUTING_ANON, 0, 0);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        } catch (Exception e) {
           //...
        }
    }
```

从上可知,应用程序的主进程,会根据包含Service信息的ServiceInfo对象、IBinder对象、CompatibilityInfo对象,构建一个CreateServiceData对象,再调用handleCreateService()便可以创建Service对象,且会执行onCreate()方法。


了解完启动过程后,插件化的Service,该如何实现呢?


**思考**：

1. 解析插件中Service信息,获取ServiceInfo对象
2. 构建出插件Service对应的CreateServiceData对象,再调用handleCreateService()便可以启动插件中Service信息。
3. Service是后台运行,不需要过多的用户感知,不需要过多与AMS打交道。因此可以考虑采用本地代理管理的方式。一个ProxyService去代理管理插件中的Service。

**Service的代理分发的关键点**：

Hook AMS中启动和停止Service的方法,用于本地管理Service。在代理的Service的生命周期中,手动管理插件的Service。



先解析出插件中Service信息：

```java
  /**
     * 解析插件中的service
     *
     * @param apkFilePath
     */
    private static void preloadParseService(String apkFilePath) {
        try {
            // 先获取PackageParser对象
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            Object packageParser = packageParserClass.newInstance();
            //接着获取PackageParser.Package
            Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
            parsePackageMethod.setAccessible(true);
            Object packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS);
            // 接着获取到Package中的receivers列表
            Class<?> packageParser$package_Class = packageParser$package.getClass();
            Field servicesField = packageParser$package_Class.getDeclaredField("services");
            servicesField.setAccessible(true);
            List serviceList = (List) servicesField.get(packageParser$package);
            Class<?> packageParser$Service_Class = Class.forName("android.content.pm.PackageParser$Service");
            // 获取 name
            Field infoField = packageParser$Service_Class.getDeclaredField("info");
            infoField.setAccessible(true);
            for (Object service : serviceList) {
                ServiceInfo info = (ServiceInfo) infoField.get(service);
                serviceInfoMap.put(new ComponentName(info.packageName, info.name), info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```
Hook AMS 代理：
```
    /**
     * hook ActivityThread中 handler拦截处理
     * ,恢复要开启的activity
     */
    private static void hookActivityThreadHandler() throws Exception  {
        //获取到ActivityThread
        Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
        Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
        sCurrentActivityThreadField.setAccessible(true);
        Object ActivityThread = sCurrentActivityThreadField.get(null);
        //获取到ActivityThread中的handler
        Field mHField=ActivityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);
         Handler mHandler=(Handler) mHField.get(ActivityThread);
         //给handler添加callback监听器，拦截
        Field mCallBackField = Handler.class.getDeclaredField("mCallback");
        mCallBackField.setAccessible(true);
        mCallBackField.set(mHandler, new ActivityThreadHandlerCallback(mHandler));
    }
    

```
拦截和启动Service的方法,用于欺满AMS,和手动停止插件Service。
```java
public class IActivityManagerHandler implements InvocationHandler {
    private Object rawIActivityManager;
    private Context context;

    public IActivityManagerHandler(Context context,Object rawIActivityManager) {
        this.context=context;
        this.rawIActivityManager = rawIActivityManager;

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //替换掉交给AMS的intent对象，将里面的TargetActivity的暂时替换成已经声明好的替身StubActivity
        switch (method.getName()) {
            case "startActivity":
                  AMSHookManager.Utils.replaceActivityIntent(args);
                return method.invoke(rawIActivityManager, args);
            case "startService":
                AMSHookManager.Utils.replaceServiceIntent(args);
                return method.invoke(rawIActivityManager, args);
            case "stopService":
                // 判断是否停止插件中的服务。
                Intent intent = AMSHookManager.Utils.filter(args);
                if (!context.getPackageName().equals(intent.getComponent().getPackageName())) {
                    return ServiceHookManager.stopService(intent);
                } else {
                    return method.invoke(rawIActivityManager, args);
                }
            default:
                return method.invoke(rawIActivityManager, args);
        }
    }
}

```
在代理ProxyService中：
```java
public class ProxyService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServiceHookManager.startService( intent, flags,  startId);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```
手动管理Service:
```java

    /**
     * 手动管理 service的onCreate,onStartCommand()
     *
     * @param intent
     * @param flags
     * @param startId
     */
    public static void startService(Intent intent, int flags, int startId) {
        try {
            Intent rawIntent = intent.getParcelableExtra(AMSHookManager.KEY_RAW_INTENT);
            Log.i(ServiceHookManager.class.getSimpleName(), " startService intent: " + rawIntent.toString());
            ServiceInfo serviceInfo = filter(rawIntent);
            if (serviceInfo != null) {
                if (!serviceMap.containsKey(serviceInfo.name)) {
                    // 并不存在，创建service
                    createService(serviceInfo);
                }
                Service service = serviceMap.get(serviceInfo.name);
                if (service != null) {
                    service.onStartCommand(rawIntent, flags, startId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```
模拟加载 , Service的过程：
```java
  private static void createService(ServiceInfo serviceInfo) {
        try {
            //创建ActivityThread$CreateServiceData
            Class<?> createServiceDataClass = Class.forName("android.app.ActivityThread$CreateServiceData");
            Constructor<?> createServiceDataConstructor = createServiceDataClass.getDeclaredConstructor();
            createServiceDataConstructor.setAccessible(true);
            Object createServiceData = createServiceDataConstructor.newInstance();
            /**
             *  接下来，对CreateServiceData对象赋值
             */
            // 赋值IBinder对象
            Field tokenField = createServiceDataClass.getDeclaredField("token");
            tokenField.setAccessible(true);
            Binder token = new Binder();
            tokenField.set(createServiceData, token);
            // 赋值 ServiceInfo
            Field infoField = createServiceDataClass.getDeclaredField("info");
            infoField.setAccessible(true);
            // 这里修改，是为了loadClass的时候, LoadedApk会是主程序的ClassLoader
            serviceInfo.applicationInfo.packageName=appContext.getPackageName();
            infoField.set(createServiceData, serviceInfo);
            // 赋值CompatibilityInfo对象
            Field compatInfoField = createServiceDataClass.getDeclaredField("compatInfo");
            compatInfoField.setAccessible(true);
            Class<?> compatibilityClass = Class.forName("android.content.res.CompatibilityInfo");
            Field defaultCompatibilityField = compatibilityClass.getDeclaredField("DEFAULT_COMPATIBILITY_INFO");
            Object compatibility = defaultCompatibilityField.get(null);
            compatInfoField.set(createServiceData, compatibility);
            //获取到ActivityThread
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object ActivityThread = sCurrentActivityThreadField.get(null);
            // 调用ActivityThread#handleCreateService()
            Method createServiceDataMethod = ActivityThreadClass.getDeclaredMethod("handleCreateService", createServiceDataClass);
            createServiceDataMethod.setAccessible(true);
            createServiceDataMethod.invoke(ActivityThread, createServiceData);

            // 获取存放service的map
            Field servicesField = ActivityThreadClass.getDeclaredField("mServices");
            servicesField.setAccessible(true);
            Map services = (Map) servicesField.get(ActivityThread);
            // 取出handleCreateService()创建的service.
            Service service = (Service) services.get(token);
            // 用完后移除
            services.remove(token);
            serviceMap.put(serviceInfo.name, service);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```
测试
```
    private void startPluginService() {
        ComponentName componentName = new ComponentName(PluginConfig.package_name, PluginConfig.service_name);
        startService(new Intent().setComponent(componentName));
    }
```






