package com.xingen.hookdemo.hook.ams;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author ： HeXinGen
 * @date ： 2022/10/19
 * @description ：
 */
public class Android9Instrumentation extends Instrumentation {
    private Instrumentation mInstrumentation;
    private PackageManager mPackageManager;

    public Android9Instrumentation(Instrumentation mInstrumentation, PackageManager mPackageManager) {
        this.mInstrumentation = mInstrumentation;
        this.mPackageManager = mPackageManager;
    }

    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {

        List<ResolveInfo> infos = mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        if (infos == null || infos.size() == 0) {
            //发现是插件的activity，即没有在AndroidManifest.xml中注册的Activity
           intent= AMSHookManager.Utils.createProxyIntent(intent);
        }
        try {
            Method execMethod = Instrumentation.class.getDeclaredMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class);
            return (ActivityResult) execMethod.invoke(mInstrumentation, who, contextThread, token,
                    target, intent, requestCode, options);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException {
        Intent rawIntent=AMSHookManager.Utils.recoverActivityIntent(intent);
        if(rawIntent!=null){
            //恢复插件的要启动的Activity组件
            return mInstrumentation.newActivity(cl,rawIntent.getComponent().getClassName(),rawIntent);
        }
        return mInstrumentation.newActivity(cl,className,intent);
    }


}
