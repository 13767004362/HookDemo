package com.xingen.plugin.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * @author HeXinGen
 * date 2019/6/11.
 */
public class PluginReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context.getApplicationContext()," 插件的广播收到 订阅信息",Toast.LENGTH_SHORT).show();
    }
}
