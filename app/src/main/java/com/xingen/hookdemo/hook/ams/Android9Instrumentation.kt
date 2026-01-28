package com.xingen.hookdemo.hook.ams

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder

/**
 * @author ： HeXinGen
 * @date ： 2022/10/19
 * @description ：
 */

class Android9Instrumentation(
    private val mInstrumentation: Instrumentation,
    private val mPackageManager: PackageManager
) : Instrumentation() {

    @Suppress("all")
    fun execStartActivity(
        who: Context,
        contextThread: IBinder,
        token: IBinder,
        target: Activity,
        intent: Intent,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? {
        val infos = mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        if (infos == null || infos.size == 0) {
            //发现是插件的activity，即没有在AndroidManifest.xml中注册的Activity
            val proxyIntent = AMSUtils.createProxyIntent(intent)
            return try {
                val execMethod = Instrumentation::class.java.getDeclaredMethod(
                    "execStartActivity",
                    Context::class.java,
                    IBinder::class.java,
                    IBinder::class.java,
                    Activity::class.java,
                    Intent::class.java,
                    Int::class.javaPrimitiveType,
                    Bundle::class.java
                )
                execMethod.invoke(
                    mInstrumentation,
                    who,
                    contextThread,
                    token,
                    target,
                    proxyIntent,
                    requestCode,
                    options
                ) as ActivityResult?
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return try {
            val execMethod = Instrumentation::class.java.getDeclaredMethod(
                "execStartActivity",
                Context::class.java,
                IBinder::class.java,
                IBinder::class.java,
                Activity::class.java,
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Bundle::class.java
            )
            execMethod.invoke(
                mInstrumentation,
                who,
                contextThread,
                token,
                target,
                intent,
                requestCode,
                options
            ) as ActivityResult?
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        val rawIntent = AMSUtils.recoverActivityIntent(intent)
        return if (rawIntent != null) {
            //恢复插件的要启动的Activity组件
            mInstrumentation.newActivity(cl, rawIntent.component!!.className, rawIntent)
        } else {
            mInstrumentation.newActivity(cl, className, intent)
        }
    }
}
