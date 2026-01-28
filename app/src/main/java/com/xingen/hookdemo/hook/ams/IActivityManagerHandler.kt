package com.xingen.hookdemo.hook.ams

import android.content.Context
import com.xingen.hookdemo.hook.service.ServiceHookManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */

class IActivityManagerHandler(
    private val rawIActivityManager: Any,
    private val context: Context
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any? {
        //替换掉交给AMS的intent对象，将里面的TargetActivity的暂时替换成已经声明好的替身StubActivity
        return when (method.name) {
            "startActivity" -> {
                AMSUtils.replaceActivityIntent(args)
                method.invoke(rawIActivityManager, *args)
            }
            "startService" -> {
               AMSUtils.replaceServiceIntent(args)
                method.invoke(rawIActivityManager, *args)
            }
            "stopService" -> {
                // 判断是否停止插件中的服务。
                val intent = AMSUtils.filter(args)
                return if (!context.packageName.equals(intent?.component?.packageName)) {
                    ServiceHookManager.stopService(intent!!)
                } else {
                    method.invoke(rawIActivityManager, *args)
                }
            }
            else -> method.invoke(rawIActivityManager, *args)
        }
    }
}
