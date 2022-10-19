package com.xingen.hookdemo.hook.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.xingen.hookdemo.hook.ams.AMSHookManager;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author HeXinGen
 * date 2019/6/13.
 */
public class ServiceHookManager {
    private static Map<ComponentName, ServiceInfo> serviceInfoMap = new HashMap<>();
    private static Map<String, Service> serviceMap = new HashMap<>();
    private static Context appContext;

    public static void init(Context context, String apkFilePath) {
        preloadParseService(apkFilePath);
        appContext = context;
    }

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
            Object packageParser$package=null;
            if (  Build.VERSION.SDK_INT < Build.VERSION_CODES.O){
                //接着获取PackageParser.Package
                Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
                parsePackageMethod.setAccessible(true);
                packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS);
            }else{
                Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class,boolean.class);
                parsePackageMethod.setAccessible(true);
                packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS,false);
            }

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

    public static int stopService(Intent intent) {

        ServiceInfo serviceInfo = filter(intent);
        if (serviceInfo == null) {
            return 0;
        }
        Service service = serviceMap.get(serviceInfo.name);
        if (service == null) {
            return 0;
        }
        service.onDestroy();
        serviceMap.remove(serviceInfo.name);
        if (serviceMap.isEmpty()) {
            // 没有插件服务在运行，则关闭ProxyService
            appContext.stopService(new Intent().setClassName(AMSHookManager.getTargetPackageName(), ProxyService.class.getName()));
        }
        return 1;
    }

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

    /**
     * 匹配到插件中的service
     *
     * @param rawIntent
     * @return
     */
    private static ServiceInfo filter(Intent rawIntent) {
        ServiceInfo serviceInfo = null;
        for (ComponentName componentName : serviceInfoMap.keySet()) {
            if (componentName.equals(rawIntent.getComponent())) {
                serviceInfo = serviceInfoMap.get(componentName);
                break;
            }
        }
        return serviceInfo;
    }

}
