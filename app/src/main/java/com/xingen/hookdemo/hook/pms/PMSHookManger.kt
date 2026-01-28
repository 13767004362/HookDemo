package com.xingen.hookdemo.hook.pms

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
/**
 * @author ： HeXinGen
 * @date ： 2023/7/10
 * @description ：
 */

object PMSHookManger {
    private const val TAG = "PMSHookManger"

    fun printAppSignature(context: Context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val signatures = packageInfo.signatures
            if (signatures != null) {
                Log.i(TAG, "sig:${signatures[0].toCharsString()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("all")
    fun hookPMS(context: Context, signed: String) {
        try {
            //获取到ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField =
                activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            val activityThread = sCurrentActivityThreadField.get(null)

            val sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager")
            sPackageManagerField.isAccessible = true
            val sPackageManager = sPackageManagerField.get(activityThread)
            // 准备好代理对象, 用来替换原始的对象
            val iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager")
            val proxy = Proxy.newProxyInstance(
                iPackageManagerInterface.classLoader,
                arrayOf(iPackageManagerInterface),
                PMSHookProxy(sPackageManager, signed, context.packageName)
            )
            // 1. 替换掉ActivityThread里面的 sPackageManager 字段
            sPackageManagerField.set(activityThread, proxy)
            // 2. 替换 ApplicationPackageManager里面的 mPM对象
            val pm = context.packageManager
            val mPmField = pm.javaClass.getDeclaredField("mPM")
            mPmField.isAccessible = true
            mPmField.set(pm, proxy)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private class PMSHookProxy(
        private val oldPMS: Any,
        //应用正确的签名信息
        private val SIGN: String,
        private val appPkgName: String
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
            return when (method.name) {
                "getPackageInfo" -> {
                    val pkgName = args!![0] as String
                    //android 13 开始,getPackageInfo(String packageName, long flags, int userId) 第二个参数是long类型
                    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        args[1] as Long
                    } else {
                        (args[1] as Int).toLong()
                    }
                    //是否是获取我们需要hook apk的签名
                    if (flag == PackageManager.GET_SIGNATURES.toLong() && appPkgName == pkgName) {
                        //将构造方法中传进来的新的签名覆盖掉原来的签名
                        val sign = Signature(SIGN)
                        val info = method.invoke(oldPMS, *args) as PackageInfo
                        info.signatures!![0] = sign
                        return info
                    }
                    method.invoke(oldPMS, *args)
                }


                else -> method.invoke(oldPMS, *(args ?: emptyArray()))
            }
        }
    }
}
