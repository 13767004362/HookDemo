package com.xingen.hookdemo.hook.application;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import com.xingen.hookdemo.PluginConfig;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * @author HeXinGen
 * date 2019/7/22.
 */
public class ApplicationHook {

    private static String delegateApplicationName;
    private volatile static boolean isInit = false;
    private static Application delegateApplication;
    public static void init(String apkFilePath,Application proxyApplication) {
        if (isInit) {
            return ;
        }
        preloadMeta(apkFilePath);
        setDelegateApplication(proxyApplication);
    }
    /**
     * 解析出插件中meta信息
     *
     * @param apkFilePath
     */
    private static void preloadMeta(String apkFilePath) {
        try {
            // 先获取PackageParser对象
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            Object packageParser = packageParserClass.newInstance();
            //接着获取PackageParser.Package
            Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
            parsePackageMethod.setAccessible(true);
            Object packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS);
            // 获取 Bundle mAppMetaData 对象
            Class<?> packageParser$package_Class = packageParser$package.getClass();
            Field mAppMetaDataFiled = packageParser$package_Class.getDeclaredField("mAppMetaData");
            mAppMetaDataFiled.setAccessible(true);
            Bundle mAppMetaData = (Bundle) mAppMetaDataFiled.get(packageParser$package);
            if (mAppMetaData != null && mAppMetaData.containsKey(PluginConfig.meta_application_key)) {
                delegateApplicationName = mAppMetaData.getString(PluginConfig.meta_application_key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * hook 替换原有的代理Application
     *
     * @param application
     */
    public static Application setDelegateApplication(Application application) {
        if (TextUtils.isEmpty(delegateApplicationName)) {
            return application;
        }
        try {
            // 先获取到ContextImpl对象
            Context contextImpl = application.getBaseContext();
            // 创建插件中真实的Application且，执行生命周期
            ClassLoader classLoader = application.getClassLoader();
            Class<?> applicationClass = classLoader.loadClass(delegateApplicationName);
            delegateApplication = (Application) applicationClass.newInstance();
            Method attachMethod = Application.class.getDeclaredMethod("attach", Context.class);
            attachMethod.setAccessible(true);
            attachMethod.invoke(delegateApplication, contextImpl);

            // 替换ContextImpl的代理Application
            Class contextImplClass = contextImpl.getClass();
            Method setOuterContextMethod = contextImplClass.getDeclaredMethod("setOuterContext", Context.class);
            setOuterContextMethod.setAccessible(true);
            setOuterContextMethod.invoke(contextImpl, delegateApplication);
            // 替换LoadedApk的代理Application
            Field loadedApkField = contextImplClass.getDeclaredField("mPackageInfo");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(contextImpl);
            Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
            Field mApplicationField = loadedApkClass.getDeclaredField("mApplication");
            mApplicationField.setAccessible(true);
            mApplicationField.set(loadedApk, delegateApplication);

            // 替换ActivityThread的代理Application
            Field mMainThreadField = contextImplClass.getDeclaredField("mMainThread");
            mMainThreadField.setAccessible(true);
            Object mMainThread = mMainThreadField.get(contextImpl);
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(mMainThread, delegateApplication);
            Field mAllApplicationsField = activityThreadClass.getDeclaredField("mAllApplications");
            mAllApplicationsField.setAccessible(true);
            ArrayList<Application> mAllApplications = (ArrayList<Application>) mAllApplicationsField.get(mMainThread);
            mAllApplications.remove(application);
            mAllApplications.add(delegateApplication);

            // 替换LoadedApk中的mApplicationInfo中name
            Field mApplicationInfoField = loadedApkClass.getDeclaredField("mApplicationInfo");
            mApplicationInfoField.setAccessible(true);
            ApplicationInfo applicationInfo = (ApplicationInfo) mApplicationInfoField.get(loadedApk);
            applicationInfo.className = delegateApplicationName;
            delegateApplication.onCreate();
            replaceContentProvider(mMainThread, delegateApplication);
            // 标记动态替换Application完成
            isInit = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return delegateApplication != null ? delegateApplication : application;
    }

    /**
     * 修改已经存在ContentProvider中application
     *
     * @param activityThread
     * @param delegateApplication
     */
    private static void replaceContentProvider(Object activityThread, Application delegateApplication) {
        try {
            Field mProviderMapField = activityThread.getClass().getDeclaredField("mProviderMap");
            mProviderMapField.setAccessible(true);
            Map<Object, Object> mProviderMap = (Map<Object, Object>) mProviderMapField.get(activityThread);
            Set<Map.Entry<Object, Object>> entrySet = mProviderMap.entrySet();
            for (Map.Entry<Object, Object> entry : entrySet) {
                // 取出ContentProvider
                Object providerClientRecord = entry.getValue();
                Field mLocalProviderField = providerClientRecord.getClass().getDeclaredField("mLocalProvider");
                mLocalProviderField.setAccessible(true);
                ContentProvider contentProvider = (ContentProvider) mLocalProviderField.get(providerClientRecord);
                if (contentProvider!=null){
                    // 修改ContentProvider中的context
                    Field contextField = Class.forName("android.content.ContentProvider").getDeclaredField("mContext");
                    contextField.setAccessible(true);
                    contextField.set(contentProvider, delegateApplication);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
