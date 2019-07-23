package com.xingen.hookdemo;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.xingen.hookdemo.hook.application.ApplicationHook;
import com.xingen.hookdemo.hook.classLoader.ClassLoaderHookManager;
import com.xingen.hookdemo.hook.contentprovider.ContentProviderHookManager;
import com.xingen.hookdemo.hook.resource.ResourceHookManager;
import com.xingen.hookdemo.hook.service.ServiceHookManager;
import com.xingen.hookdemo.utils.Utils;

import java.io.File;

import me.weishu.reflection.Reflection;

/**
 * @author HeXinGen
 * date 2019/5/31.
 */
public class ProxyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
        loadPluginDex(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //解析插件中的Application,动态替换
        ApplicationHook.init( PluginConfig.getZipFilePath(this),this);
    }



    private void loadPluginDex(Context context) {
        // 先拷贝assets 下的apk，写入磁盘中。
        String zipFilePath = PluginConfig.getZipFilePath(context);
        File zipFile = new File(zipFilePath);
        final String asset_file_name = "plugin.apk";
        Utils.copyFiles(context, asset_file_name, zipFile);
        String optimizedDirectory = new File(Utils.getCacheDir(context).getAbsolutePath() + File.separator + "plugin").getAbsolutePath();
        // 加载插件dex
        ClassLoaderHookManager.init(context, zipFilePath, optimizedDirectory);
        //加载插件资源
        ResourceHookManager.init(context, zipFilePath);
        // hook service ，解析多进程的service 。多进程，会重复走onCreate()
        ServiceHookManager.init(context, zipFilePath);
        // hook ContentProvider(加载ContentProvider是在application 的onCreate()之前)
         ContentProviderHookManager.init(this, zipFilePath);
    }
}
