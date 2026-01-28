package com.xingen.plugin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PluginReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Toast.makeText(context?.applicationContext, " 插件的广播收到 订阅信息", Toast.LENGTH_SHORT).show()
    }
}
