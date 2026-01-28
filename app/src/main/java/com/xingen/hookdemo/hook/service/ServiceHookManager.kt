package com.xingen.hookdemo.hook.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.util.Log
import com.xingen.hookdemo.hook.ams.AMSHookManager
import java.io.File
import java.lang.reflect.Constructor

/**
 * @author HeXinGen
 * date 2019/6/13.
 */
object ServiceHookManager {
    private val serviceInfoMap: MutableMap<ComponentName, ServiceInfo> =
        HashMap()
    private val serviceMap: MutableMap<String, Service> = HashMap()
    private var appContext: Context? = null

    @JvmStatic
    fun init(context: Context, apkFilePath: String) {
        preloadParseService(apkFilePath)
        appContext = context
    }

    /**
     * 解析插件中的service
     *
     * @param apkFilePath
     */
    private fun preloadParseService(apkFilePath: String) {
        try {
            // 先获取PackageParser对象
            val packageParserClass = Class.forName("android.content.pm.PackageParser")
            val packageParser: Any = packageParserClass.newInstance()
            var `packageParser$package`: Any? = null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                //接着获取PackageParser.Package
                val parsePackageMethod = packageParserClass.getDeclaredMethod(
                    "parsePackage",
                    File::class.java,
                    Int::class.javaPrimitiveType
                )
                parsePackageMethod.isAccessible = true
                `packageParser$package` = parsePackageMethod.invoke(
                    packageParser,
                    File(apkFilePath),
                    PackageManager.GET_RECEIVERS
                )
            } else {
                val parsePackageMethod = packageParserClass.getDeclaredMethod(
                    "parsePackage",
                    File::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                parsePackageMethod.isAccessible = true
                `packageParser$package` = parsePackageMethod.invoke(
                    packageParser,
                    File(apkFilePath),
                    PackageManager.GET_RECEIVERS,
                    false
                )
            }

            // 接着获取到Package中的receivers列表
            val `packageParser$package_Class`: Class<*> = `packageParser$package`.javaClass
            val servicesField = `packageParser$package_Class`.getDeclaredField("services")
            servicesField.setAccessible(true)
            val serviceList = servicesField.get(`packageParser$package`) as MutableList<*>?
            val `packageParser$Service_Class` =
                Class.forName("android.content.pm.PackageParser\$Service")
            // 获取 name
            val infoField = `packageParser$Service_Class`.getDeclaredField("info")
            infoField.setAccessible(true)
            for (service in serviceList!!) {
                val info = infoField.get(service) as ServiceInfo?
                serviceInfoMap.put(ComponentName(info!!.packageName, info.name), info)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 手动管理 service的onCreate,onStartCommand()
     *
     * @param intent
     * @param flags
     * @param startId
     */
    fun startService(intent: Intent, flags: Int, startId: Int) {
        try {
            val rawIntent = intent.getParcelableExtra<Intent?>(AMSHookManager.KEY_RAW_INTENT)
            Log.i(
                ServiceHookManager::class.java.getSimpleName(),
                " startService intent: $rawIntent"
            )
            val serviceInfo = ServiceHookManager.filter(rawIntent!!)
            if (serviceInfo != null) {
                if (!serviceMap.containsKey(serviceInfo.name)) {
                    // 并不存在，创建service
                    createService(serviceInfo)
                }
                val service = serviceMap[serviceInfo.name]
                service?.onStartCommand(rawIntent, flags, startId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun stopService(intent: Intent): Int {
        val serviceInfo = filter(intent) ?: return 0
        val service = serviceMap[serviceInfo.name] ?: return 0
        service.onDestroy()
        serviceMap.remove(serviceInfo.name)
        if (serviceMap.isEmpty()) {
            // 没有插件服务在运行，则关闭ProxyService
            appContext!!.stopService(
                Intent().setClassName(
                    AMSHookManager.getTargetPackageName()!!,
                    ProxyService::class.java.getName()
                )
            )
        }
        return 1
    }

    private fun createService(serviceInfo: ServiceInfo) {
        try {
            //创建ActivityThread$CreateServiceData
            val createServiceDataClass =
                Class.forName("android.app.ActivityThread\$CreateServiceData")
            val createServiceDataConstructor: Constructor<*> =
                createServiceDataClass.getDeclaredConstructor()
            createServiceDataConstructor.isAccessible = true
            val createServiceData: Any = createServiceDataConstructor.newInstance()

            /**
             * 接下来，对CreateServiceData对象赋值
             */
            // 赋值IBinder对象
            val tokenField = createServiceDataClass.getDeclaredField("token")
            tokenField.isAccessible = true
            val token = Binder()
            tokenField.set(createServiceData, token)
            // 赋值 ServiceInfo
            val infoField = createServiceDataClass.getDeclaredField("info")
            infoField.isAccessible = true
            //这里使用宿主的ApplicationInfo
            serviceInfo.applicationInfo = appContext!!.applicationInfo
            infoField.set(createServiceData, serviceInfo)
            // 赋值CompatibilityInfo对象
            val compatInfoField = createServiceDataClass.getDeclaredField("compatInfo")
            compatInfoField.isAccessible = true
            val compatibilityClass = Class.forName("android.content.res.CompatibilityInfo")
            val defaultCompatibilityField =
                compatibilityClass.getDeclaredField("DEFAULT_COMPATIBILITY_INFO")
            val compatibility = defaultCompatibilityField.get(null)
            compatInfoField.set(createServiceData, compatibility)
            //获取到ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField =
                activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            val activityThread = sCurrentActivityThreadField.get(null)
            // 调用ActivityThread#handleCreateService()
            val createServiceDataMethod =
                activityThreadClass.getDeclaredMethod("handleCreateService", createServiceDataClass)
            createServiceDataMethod.isAccessible = true
            createServiceDataMethod.invoke(activityThread, createServiceData)

            // 获取存放service的map
            val servicesField = activityThreadClass.getDeclaredField("mServices")
            servicesField.isAccessible = true
            val services = servicesField.get(activityThread) as MutableMap<*, *>
            // 取出handleCreateService()创建的service.
            val service = services[token] as Service
            // 用完后移除
            services.remove(token)
            serviceMap[serviceInfo.name] = service
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 匹配到插件中的service
     *
     * @param rawIntent
     * @return
     */
    private fun filter(rawIntent: Intent): ServiceInfo? {
        var serviceInfo: ServiceInfo? = null
        for (componentName in serviceInfoMap.keys) {
            if (componentName == rawIntent.component) {
                serviceInfo = serviceInfoMap[componentName]
                break
            }
        }
        return serviceInfo
    }
}
