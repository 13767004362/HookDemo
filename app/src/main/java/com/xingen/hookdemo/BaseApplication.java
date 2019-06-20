package com.xingen.hookdemo;

import android.app.Application;
import android.content.Context;

import com.xingen.hookdemo.hook.classLoader.ClassLoaderHookManager;
import com.xingen.hookdemo.hook.contentprovider.ContentProviderHookManager;
import com.xingen.hookdemo.hook.receiver.ReceiverHookManager;
import com.xingen.hookdemo.hook.resource.ResourceHookManager;
import com.xingen.hookdemo.hook.service.ServiceHookManager;
import com.xingen.hookdemo.utils.Utils;

import me.weishu.reflection.Reflection;

/**
 * @author HeXinGen
 * date 2019/5/31.
 */
public class BaseApplication extends Application {
    private static  BaseApplication instance;
    private     String zipFilePath;
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
         Reflection.unseal(base);
         loadPluginDex(base);
    }
    @Override
    public void onCreate() {
        super.onCreate();
         instance=this;
    }
    public String getZipFilePath() {
        return zipFilePath;
    }
    public static BaseApplication getInstance() {
        return instance;
    }
    private void loadPluginDex(Context context) {
        // 先拷贝assets 下的apk，写入磁盘中。
        zipFilePath = Utils.copyFiles(context, PluginConfig.apk_file_name);
        String optimizedDirectory = Utils.getCacheDir(context).getAbsolutePath();
        // 加载插件dex
        ClassLoaderHookManager.init(zipFilePath, optimizedDirectory);
        //加载插件资源
        ResourceHookManager.init(context,zipFilePath);
        // hook service ，解析多进程的service 。多进程，会重复走onCreate()
        ServiceHookManager.init(context,  zipFilePath);
        // hook ContentProvider(加载ContentProvider是在application 的onCreate()之前)
        ContentProviderHookManager.init(context,zipFilePath);

    }
}
