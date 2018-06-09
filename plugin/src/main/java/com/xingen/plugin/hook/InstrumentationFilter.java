package com.xingen.plugin.hook;


import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import java.lang.reflect.Field;


/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class InstrumentationFilter extends Instrumentation {
    private static final String TAG=InstrumentationFilter.class.getSimpleName();
    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        try {
            Class<?> mClass = Class.forName("android.app.ContextImpl");
            Field[] fields = mClass.getDeclaredFields();
            Field mClassLoader = null;
            Field mPackageInfo = null;
            for (Field field : fields) {
                if (field.getName().equals("mClassLoader")) {
                    mClassLoader = field;

                }
                if (field.getName().equals("mPackageInfo")) {
                    mPackageInfo = field;
                }
            }
            if (mClassLoader != null) {
                mClassLoader.setAccessible(true);
                //将系统的加载Activity的ClassLoader替换成，插件的ClassLoader
                mClassLoader.set(activity.getBaseContext(), InstrumentationFilter.class.getClassLoader());
                return;
            }
            if (mPackageInfo != null) {
                mPackageInfo.setAccessible(true);
                Class<?> loadedApk=Class.forName("android.app.LoadedApk");
                 mClassLoader= loadedApk.getDeclaredField("mClassLoader");
                 mClassLoader.setAccessible(true);
                 //将系统中LoadedApk的ClassLoader
                 mClassLoader.set(mPackageInfo.get(activity.getBaseContext()),InstrumentationFilter.class.getClassLoader());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            super.callActivityOnCreate(activity, icicle);
        }
    }
}
