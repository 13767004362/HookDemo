package com.xingen.plugin.hook;

import android.content.Context;

import java.lang.reflect.Field;

/**
 * Created by ${HeXinGen} on 2018/12/5.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class ContextImplUtils {


    /**
     *  Hook 掉ContextImpl中ClassLoader ,替换成自己指定的。
     * @param context
     * @param substituteClassLoader
     */
    public static void hookClassLoader(Context context,ClassLoader  substituteClassLoader){
        try {
             Class<?> contextImplClass=Class.forName("android.app.ContextImpl");
            Field[] fields= contextImplClass.getDeclaredFields();
            Field mClassLoaderField=null,mPackageInfoField=null;
            for (Field field:fields){
                if (field.getName().equals("mClassLoader")){
                    mClassLoaderField=field;
                }else if (field.getName().equals("mPackageInfo")){
                    mPackageInfoField=field;
                }
            }
            if (mClassLoaderField!=null){
                try {
                   mClassLoaderField.setAccessible(true);
                   mClassLoaderField.set(context,substituteClassLoader);
                }catch ( Exception e){
                    e.printStackTrace();
                }
                return;
            }
            if (mPackageInfoField!=null){
               try {
                   mPackageInfoField.setAccessible(true);
                  Object LoadedApk =  mPackageInfoField.get(context);
                  Class<?> LoadedApkClass=LoadedApk.getClass();
                 mClassLoaderField= LoadedApkClass.getDeclaredField("mClassLoader");
                 mClassLoaderField.setAccessible(true);
                 mClassLoaderField.set(LoadedApk,substituteClassLoader);
               }catch (Exception e){
                   e.printStackTrace();
               }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
