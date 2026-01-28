package com.xingen.hookdemo.hook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.HashMap
import java.util.List
/**
 * @author HeXinGen
 * date 2019/6/11.
 */

object ReceiverHookManager {
    private val receivers: MutableMap<ActivityInfo, List<IntentFilter>> = HashMap()

    fun init(context: Context, apkFilePath: String) {
        preloadParseReceiver(apkFilePath)
        // 因已经自定义过classloader，已经加载了插件的dex ,这里省略加载的过程。
        registerPluginReceiver(context)
    }

    /**
     * 注册插件中的广播
     */
    private fun registerPluginReceiver(context: Context) {
        try {
            val classLoader = ReceiverHookManager::class.java.classLoader
            for (activityInfo in receivers.keys) {
                val intentFilters = receivers[activityInfo]
                val broadcastReceiver =
                    classLoader!!.loadClass(activityInfo.name).newInstance() as BroadcastReceiver
                if (intentFilters != null) {
                    for (intentFilter in intentFilters) {
                        //以 Android 14 为目标并动态注册广播应用和服务需要指定exported属性
                        ContextCompat.registerReceiver(
                            context.applicationContext,
                            broadcastReceiver,
                            intentFilter,
                            ContextCompat.RECEIVER_EXPORTED
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解析插件中的广播
     */
    @Suppress("all")
    private fun preloadParseReceiver(apkFilePath: String) {
        try {
            // 先获取PackageParser对象
            val packageParserClass = Class.forName("android.content.pm.PackageParser")
            val packageParser = packageParserClass.newInstance()
            //接着获取PackageParser.Package
            val parsePackageMethod = packageParserClass.getDeclaredMethod(
                "parsePackage",
                File::class.java,
                Int::class.javaPrimitiveType
            )
            parsePackageMethod.isAccessible = true
            val packageParserPackage = parsePackageMethod.invoke(
                packageParser,
                File(apkFilePath),
                PackageManager.GET_RECEIVERS
            )
            // 接着获取到Package中的receivers列表
            val packageParserPackageClass = packageParserPackage.javaClass
            val receiversField = packageParserPackageClass.getDeclaredField("receivers")
            receiversField.isAccessible = true
            val receiversList = receiversField.get(packageParserPackage) as List<*>
            // intent-filter过滤器
            val packageParserActivityClass =
                Class.forName("android.content.pm.PackageParser\$Activity")
            val intentsFiled = packageParserActivityClass.getField("intents")
            intentsFiled.isAccessible = true
            // 获取 name
            val infoField = packageParserActivityClass.getDeclaredField("info")
            infoField.isAccessible = true

            for (receiver in receiversList) {
                val info = infoField.get(receiver) as ActivityInfo
                val intentFiltersList = intentsFiled.get(receiver) as List<IntentFilter>
                receivers[info] = intentFiltersList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
