package com.xingen.plugin.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast

class PluginService : Service() {
    companion object {
        private const val TAG = "PluginService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "$TAG 被创建")
        Toast.makeText(baseContext, "$TAG 被创建", Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        executeDelayTask()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun executeDelayTask() {
        Handler(Looper.getMainLooper()).postDelayed({
            Log.i(TAG, " 执行定时任务")
            application.stopService(Intent().setClassName("com.xingen.plugin", PluginService::class.java.name))
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "$TAG 被销毁")
        Toast.makeText(baseContext.applicationContext, "$TAG 运行2秒后,被销毁", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
