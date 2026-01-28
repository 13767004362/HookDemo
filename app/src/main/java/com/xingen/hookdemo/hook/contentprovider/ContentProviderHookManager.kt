package com.xingen.hookdemo.hook.contentprovider

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.LinkedList
import java.util.List
/**
 * @author HeXinGen
 * date 2019/6/14.
 */
object ContentProviderHookManager {
    private val providerInfoList: MutableList<ProviderInfo> = LinkedList()

    @JvmStatic
    fun init(context: Application, apkFilePath: String) {
        preloadParseContentProvider(apkFilePath)
        // 便于classloader加载，修改
        val packageName = context.baseContext.packageName
        for (providerInfo in providerInfoList) {
            providerInfo.applicationInfo.packageName = packageName
        }
        installContentProvider(context)
    }
    /**
     *  将ContentProvider安装到进程中
     */
    @Suppress("all")
    private fun installContentProvider(context: Context) {
        try {
            //获取到ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            val activityThread = sCurrentActivityThreadField.get(null)
            // 调用 installContentProviders()
            val installContentProvidersMethod = activityThreadClass.getDeclaredMethod(
                "installContentProviders",
                Context::class.java,
                List::class.java
            )
            installContentProvidersMethod.isAccessible = true
            installContentProvidersMethod.invoke(activityThread, context, providerInfoList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /**
     * 解析插件中的service
     *
     * @param apkFilePath
     */

    @Suppress("all")
    private fun preloadParseContentProvider(apkFilePath: String) {
        try {
            // 先获取PackageParser对象
            val packageParserClass = Class.forName("android.content.pm.PackageParser")
            val packageParser = packageParserClass.newInstance()
            val parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File::class.java, Int::class.javaPrimitiveType)
            parsePackageMethod.isAccessible = true
            val packageParserPackage = parsePackageMethod.invoke(packageParser, File(apkFilePath), PackageManager.GET_RECEIVERS)
            // 接着获取到Package中的receivers列表
            val packageParserPackageClass = packageParserPackage.javaClass
            val providersField = packageParserPackageClass.getDeclaredField("providers")
            providersField.isAccessible = true
            val providersList = providersField.get(packageParserPackage) as List<*>
            // 获取 name
            val packageParserProviderClass = Class.forName("android.content.pm.PackageParser\$Provider")
            val infoField = packageParserProviderClass.getDeclaredField("info")
            infoField.isAccessible = true

            for (provider in providersList) {
                val info = infoField.get(provider) as ProviderInfo
                providerInfoList.add(info)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
