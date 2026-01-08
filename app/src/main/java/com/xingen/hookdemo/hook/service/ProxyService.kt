package com.xingen.hookdemo.hook.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * @author HeXinGen
 * date 2019/6/13.
 */
class ProxyService : Service() {

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        ServiceHookManager.startService(intent, flags, startId)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
