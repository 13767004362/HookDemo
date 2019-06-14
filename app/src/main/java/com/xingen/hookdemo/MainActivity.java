package com.xingen.hookdemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.xingen.hookdemo.hook.ams.AMSHookManager;
import com.xingen.hookdemo.hook.receiver.ReceiverHookManager;
import com.xingen.hookdemo.hook.service.ServiceHookManager;

import java.lang.reflect.Method;

public class MainActivity extends Activity implements View.OnClickListener {


    private ClassLoader appMainClassLoader;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        appMainClassLoader = this.getClassLoader();
        String apkFilePath = BaseApplication.getInstance().getZipFilePath();
        // hook  ams
        String subPackageName = getPackageName();
        AMSHookManager.init(subPackageName);
        // hook 广播
        ReceiverHookManager.init(newBase, apkFilePath);
        // hook service
        ServiceHookManager.init(newBase, apkFilePath);
        // hook ContentProvider

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.main_hook_fragment_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_activity_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_receiver_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_service_btn).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_hook_fragment_btn:
                loadPlugin();
                break;
            case R.id.main_hook_activity_btn:
                loadTargetActivity();
                break;
            case R.id.main_hook_receiver_btn: {
                sendActionBroadcast();
            }
            break;
            case R.id.main_hook_service_btn: {
                startPluginService();
            }
            break;

        }
    }
    private void startPluginService(){
        ComponentName componentName = new ComponentName(PluginConfig.package_name, PluginConfig.service_name);
        startService(new Intent().setComponent(componentName));
    }

    private void sendActionBroadcast() {
        Intent intent = new Intent();
        intent.setAction(PluginConfig.receiver_action);
        sendBroadcast(intent);
    }

    /**
     * 绕过ams，启动目标的Activity
     */
    private void loadTargetActivity() {
        ComponentName componentName = new ComponentName(PluginConfig.package_name, PluginConfig.activity_name);
        startActivity(new Intent().setComponent(componentName));
    }

    /**
     * 加载插件
     */
    private void loadPlugin() {
        //开始显示插件中的DialogFragment
        try {
            //通过dexClassLoader加载指定包名的类
            Class<?> mClass = appMainClassLoader.loadClass("com.xingen.plugin.fragment.MessageDialogFragment");
            Method method = mClass.getDeclaredMethod("startDialog", Activity.class);
            method.setAccessible(true);
            method.invoke(null, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
