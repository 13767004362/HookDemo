package com.xingen.hookdemo;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.xingen.hookdemo.hook.ams.AMSHookManager;
import com.xingen.hookdemo.hook.application.ApplicationHook;
import com.xingen.hookdemo.hook.classLoader.ClassLoaderHookManager;
import com.xingen.hookdemo.hook.contentprovider.ContentProviderHookManager;
import com.xingen.hookdemo.hook.pms.PMSHookManger;
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
        AMSHookManager.hookInstrumentation(context);
        PMSHookManger.hookPMS(context,"30820253308201bca00302010202044bbb0361300d06092a864886f70d0101050500306d310e300c060355040613054368696e61310f300d06035504080c06e58c97e4baac310f300d06035504070c06e58c97e4baac310f300d060355040a0c06e885bee8aeaf311b3019060355040b0c12e697a0e7babfe4b89ae58aa1e7b3bbe7bb9f310b30090603550403130251513020170d3130303430363039343831375a180f32323834303132303039343831375a306d310e300c060355040613054368696e61310f300d06035504080c06e58c97e4baac310f300d06035504070c06e58c97e4baac310f300d060355040a0c06e885bee8aeaf311b3019060355040b0c12e697a0e7babfe4b89ae58aa1e7b3bbe7bb9f310b300906035504031302515130819f300d06092a864886f70d010101050003818d0030818902818100a15e9756216f694c5915e0b529095254367c4e64faeff07ae13488d946615a58ddc31a415f717d019edc6d30b9603d3e2a7b3de0ab7e0cf52dfee39373bc472fa997027d798d59f81d525a69ecf156e885fd1e2790924386b2230cc90e3b7adc95603ddcf4c40bdc72f22db0f216a99c371d3bf89cba6578c60699e8a0d536950203010001300d06092a864886f70d01010505000381810094a9b80e80691645dd42d6611775a855f71bcd4d77cb60a8e29404035a5e00b21bcc5d4a562482126bd91b6b0e50709377ceb9ef8c2efd12cc8b16afd9a159f350bb270b14204ff065d843832720702e28b41491fbc3a205f5f2f42526d67f17614d8a974de6487b2c866efede3b4e49a0f916baa3c1336fd2ee1b1629652049");
    }
}
