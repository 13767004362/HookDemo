package com.xingen.hookdemo.hook.ams

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Message
import com.xingen.hookdemo.hook.activity.StubActivity
import com.xingen.hookdemo.hook.ams.AMSHookManager.KEY_RAW_INTENT
import com.xingen.hookdemo.hook.ams.AMSHookManager.targetPackageName
import com.xingen.hookdemo.hook.service.ProxyService
import java.lang.reflect.Field
import java.lang.reflect.Proxy

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 * <p>
 * Hook AMS
 */

object AMSHookManager {
    private var targetPackageName: String? = null
    private var isInIt = false
    const val TAG = "AMSHookManager"
    const val KEY_RAW_INTENT = "raw_intent"

    fun isIsInIt(): Boolean {
        return isInIt
    }

    /**
     * 初始化操作
     *
     * @param packageName
     */

    fun init(context: Context, packageName: String) {
        try {
            targetPackageName = packageName
            hookIActivityManager(context)
            hookActivityThreadHandler()
            isInIt = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * hook 掉IActivityManager，使用自己的代理对象和ams通讯
     */

    @Suppress("all")
    private fun hookIActivityManager(context: Context) {
        val activityManagerSingletonField: Field
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//8.0以上发生改变
            val activityManagerClass = Class.forName("android.app.ActivityManager")
            activityManagerSingletonField =
                activityManagerClass.getDeclaredField("IActivityManagerSingleton")
        } else {
            val activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative")
            activityManagerSingletonField = activityManagerNativeClass.getDeclaredField("gDefault")
        }
        activityManagerSingletonField.isAccessible = true
        val activityManagerSingleton = activityManagerSingletonField.get(null)
        // ActivityManagerSingleton是一个 android.util.Singleton对象; 我们取出这个单例里面的字段
        val singletonClass = Class.forName("android.util.Singleton")
        val mInstanceField = singletonClass.getDeclaredField("mInstance")
        mInstanceField.isAccessible = true
        //获取到ActivityManager通讯代理对象，即IActivityManager对象
        val rawIActivityManager = mInstanceField.get(activityManagerSingleton)
        //动态代理，创建代理对象
        val proxy = Proxy.newProxyInstance(
            Thread.currentThread().contextClassLoader,
            arrayOf(Class.forName("android.app.IActivityManager")),
            IActivityManagerHandler(rawIActivityManager!!, context)
        )
        //换成自己的IActivityManager对象
        mInstanceField.set(activityManagerSingleton, proxy)
    }

    /**
     * android 9.0 以上采用Instrumentation方式来，加载插件中Activity
     * @param context
     * @throws Exception
     */

    @Suppress("all")
    fun hookInstrumentation(context: Context) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
            try {
                //获取到ActivityThread
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val sCurrentActivityThreadField =
                    activityThreadClass.getDeclaredField("sCurrentActivityThread")
                sCurrentActivityThreadField.isAccessible = true
                val activityThread = sCurrentActivityThreadField.get(null)

                val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
                mInstrumentationField.isAccessible = true
                val instrumentation = mInstrumentationField.get(activityThread) as Instrumentation
                val proxyInstrumentation =
                    Android9Instrumentation(instrumentation, context.packageManager)
                mInstrumentationField.set(activityThread, proxyInstrumentation)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * hook ActivityThread中 handler拦截处理
     * ,恢复要开启的activity
     */

    @Suppress("all")
    private fun hookActivityThreadHandler() {
        //获取到ActivityThread
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val sCurrentActivityThreadField =
            activityThreadClass.getDeclaredField("sCurrentActivityThread")
        sCurrentActivityThreadField.isAccessible = true
        val activityThread = sCurrentActivityThreadField.get(null)
        //获取到ActivityThread中的handler
        val mHField = activityThreadClass.getDeclaredField("mH")
        mHField.isAccessible = true
        val mHandler = mHField.get(activityThread) as Handler
        //给handler添加callback监听器，拦截
        val mCallBackField = Handler::class.java.getDeclaredField("mCallback")
        mCallBackField.isAccessible = true
        mCallBackField.set(mHandler, ActivityThreadHandlerCallback(mHandler))
    }

    fun getTargetPackageName(): String? {
        return targetPackageName
    }


}
object AMSUtils {


    fun filter(args: Array<Any>?): Intent? {
        var intent: Intent? = null
        for (i in args!!.indices) {
            if (args[i] is Intent) {
                intent = args[i] as Intent
                break
            }
        }
        return intent
    }

    /**
     * 替换成代替的activity,绕过ams检查
     *
     * @param args
     */

    fun replaceActivityIntent(args: Array<Any>) {
        var rawIntent: Intent? = null
        var index = 0

        for (i in args.indices) {
            if (args[i] is Intent) {
                index = i
                break
            }
        }
        //真实启动的Intent
        rawIntent = args[index] as Intent
        args[index] = createProxyIntent(rawIntent)
    }

    fun createProxyIntent(rawIntent: Intent): Intent {
        //构建一个替代的Activity对应的intent
        val subIntent = Intent()
        val componentName = ComponentName( AMSHookManager.getTargetPackageName()!!, StubActivity::class.java.name)
        subIntent.component = componentName
        //将真实启动的Intent作为参数附带上
        subIntent.putExtra(KEY_RAW_INTENT, rawIntent)
        return subIntent
    }

    /**
     *  替换成ProxyService
     * @param args
     */
    fun replaceServiceIntent(args: Array<Any>) {
        var rawIntent: Intent? = null
        var index = 0
        for (i in args.indices) {
            if (args[i] is Intent) {
                index = i
                break
            }
        }
        rawIntent = args[index] as Intent
        // 构建一个ProxyService的intent
        val subIntent = Intent()
        subIntent.setClassName(AMSHookManager.getTargetPackageName()!!, ProxyService::class.java.name)
        // 将信息存储在intent中
        subIntent.putExtra(KEY_RAW_INTENT, rawIntent)
        args[index] = subIntent
    }

    /**
     * 恢复成要启动的activity
     * @param message
     */

    fun recoverActivityIntent(message: Message) {
        val launchActivity = 100
        if (message.what == launchActivity) {
            try {
                val activityClientRecordClass = message.obj.javaClass
                val intentField = activityClientRecordClass.getDeclaredField("intent")
                intentField.isAccessible = true
                //真实启动的Intent
                val subIntent = intentField.get(message.obj) as Intent
                val rawIntent = subIntent.getParcelableExtra<Intent>(KEY_RAW_INTENT)
                rawIntent?.let {
                    //把正式启动的intent设置进去
                    subIntent.component = it.component
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recoverActivityIntent(subIntent: Intent): Intent? {
        //真实启动的Intent
        return subIntent.getParcelableExtra<Intent>(KEY_RAW_INTENT)
    }
}