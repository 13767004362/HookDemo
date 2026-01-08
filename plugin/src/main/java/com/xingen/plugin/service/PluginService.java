package com.xingen.plugin.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * @author HeXinGen
 * date 2019/6/13.
 */
public class PluginService extends Service {
    private static final String TAG=PluginService.class.getSimpleName();
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG,TAG+"被创建");
        Toast.makeText(getBaseContext(),TAG+"被创建",Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
                   executeDelayTask();
        return super.onStartCommand(intent, flags, startId);
    }
    private void executeDelayTask(){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG," 执行定时任务");
              getApplication().stopService(new Intent().setClassName("com.xingen.plugin",PluginService.class.getName()));
            }
        }, 2000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG,TAG+"被销毁");
        Toast.makeText(getBaseContext().getApplicationContext(),TAG+"运行2秒后,被销毁",Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
