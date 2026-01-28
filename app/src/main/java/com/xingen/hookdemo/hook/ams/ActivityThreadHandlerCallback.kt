package com.xingen.hookdemo.hook.ams

import android.os.Handler
import android.os.Message

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */
class ActivityThreadHandlerCallback(private val activityThreadHandler: Handler) : Handler.Callback {
    override fun handleMessage(msg: Message): Boolean {
        AMSUtils.recoverActivityIntent(msg)
        activityThreadHandler.handleMessage(msg)
        return true
    }
}
