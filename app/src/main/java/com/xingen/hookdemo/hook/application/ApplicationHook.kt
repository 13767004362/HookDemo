package com.xingen.hookdemo.hook.application

import android.app.Application
import android.content.ContentProvider
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import com.xingen.hookdemo.PluginConfig
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.Map
import java.util.Set

/**
 * @author HeXinGen
 * date 2019/7/22.
 */

object ApplicationHook {
    private var delegateApplicationName: String? = null
    private var isInit = false
    private var delegateApplication: Application? = null

    fun init(apkFilePath: String, proxyApplication: Application): Application {
        if (isInit) {
            return delegateApplication!!
        }
        preloadMeta(apkFilePath)
        return setDelegateApplication(proxyApplication)
    }

    /**
     * 解析出插件中meta信息
     *
     * @param apkFilePath
     */

    @Suppress("all")
    private fun preloadMeta(apkFilePath: String) {
        try {
            // 先获取PackageParser对象
            val packageParserClass = Class.forName("android.content.pm.PackageParser")
            val packageParser = packageParserClass.newInstance()
            //接着获取PackageParser.Package
            val parsePackageMethod = packageParserClass.getDeclaredMethod(
                "parsePackage",
                File::class.java,
                Int::class.javaPrimitiveType
            )
            parsePackageMethod.isAccessible = true
            val packageParserPackage = parsePackageMethod.invoke(
                packageParser,
                File(apkFilePath),
                PackageManager.GET_RECEIVERS
            )
            // 获取 Bundle mAppMetaData 对象
            val packageParserPackageClass = packageParserPackage.javaClass
            val mAppMetaDataField = packageParserPackageClass.getDeclaredField("mAppMetaData")
            mAppMetaDataField.isAccessible = true
            val mAppMetaData = mAppMetaDataField.get(packageParserPackage) as Bundle?

            if (mAppMetaData != null && mAppMetaData.containsKey(PluginConfig.meta_application_key)) {
                delegateApplicationName = mAppMetaData.getString(PluginConfig.meta_application_key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * hook 替换原有的代理Application
     *
     * @param application
     */

    @Suppress("all")
    private fun setDelegateApplication(application: Application): Application {
        if (TextUtils.isEmpty(delegateApplicationName)) {
            return application
        }
        try {
            // 先获取到ContextImpl对象
            val contextImpl = application.baseContext
            // 创建插件中真实的Application且，执行生命周期
            val classLoader = application.classLoader
            val applicationClass = classLoader.loadClass(delegateApplicationName)
            delegateApplication = applicationClass.newInstance() as Application

            val attachMethod =
                Application::class.java.getDeclaredMethod("attach", Context::class.java)
            attachMethod.isAccessible = true
            attachMethod.invoke(delegateApplication, contextImpl)
            // 替换ContextImpl的代理Application
            val contextImplClass = contextImpl.javaClass
            val setOuterContextMethod =
                contextImplClass.getDeclaredMethod("setOuterContext", Context::class.java)
            setOuterContextMethod.isAccessible = true
            setOuterContextMethod.invoke(contextImpl, delegateApplication)
            // 替换LoadedApk的代理Application
            val loadedApkField = contextImplClass.getDeclaredField("mPackageInfo")
            loadedApkField.isAccessible = true
            val loadedApk = loadedApkField.get(contextImpl)

            val loadedApkClass = Class.forName("android.app.LoadedApk")
            val mApplicationField = loadedApkClass.getDeclaredField("mApplication")
            mApplicationField.isAccessible = true
            mApplicationField.set(loadedApk, delegateApplication)
            // 替换ActivityThread的代理Application
            val mMainThreadField = contextImplClass.getDeclaredField("mMainThread")
            mMainThreadField.isAccessible = true
            val mMainThread = mMainThreadField.get(contextImpl)

            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val mInitialApplicationField =
                activityThreadClass.getDeclaredField("mInitialApplication")
            mInitialApplicationField.isAccessible = true
            mInitialApplicationField.set(mMainThread, delegateApplication)

            val mAllApplicationsField = activityThreadClass.getDeclaredField("mAllApplications")
            mAllApplicationsField.isAccessible = true
            val mAllApplications = mAllApplicationsField.get(mMainThread) as ArrayList<Application>
            mAllApplications.remove(application)
            mAllApplications.add(delegateApplication!!)
            // 替换LoadedApk中的mApplicationInfo中name
            val mApplicationInfoField = loadedApkClass.getDeclaredField("mApplicationInfo")
            mApplicationInfoField.isAccessible = true
            val applicationInfo = mApplicationInfoField.get(loadedApk) as ApplicationInfo
            applicationInfo.className = delegateApplicationName

            delegateApplication?.onCreate()
            replaceContentProvider(mMainThread, delegateApplication!!)
            // 标记动态替换Application完成
            isInit = true

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return delegateApplication ?: application
    }

    /**
     * 修改已经存在ContentProvider中application
     *
     * @param activityThread
     * @param delegateApplication
     */

    @Suppress("all")
    private fun replaceContentProvider(activityThread: Any, delegateApplication: Application) {
        try {
            val mProviderMapField = activityThread.javaClass.getDeclaredField("mProviderMap")
            mProviderMapField.isAccessible = true
            val mProviderMap = mProviderMapField.get(activityThread) as Map<*, *>
            val entrySet = mProviderMap.entrySet()
            for (entry in entrySet) {
                // 取出ContentProvider
                val providerClientRecord = entry.value
                val mLocalProviderField =
                    providerClientRecord.javaClass.getDeclaredField("mLocalProvider")
                mLocalProviderField.isAccessible = true
                val contentProvider =
                    mLocalProviderField.get(providerClientRecord) as ContentProvider?
                if (contentProvider != null) {
                    // 修改ContentProvider中的context
                    val contextField = Class.forName("android.content.ContentProvider")
                        .getDeclaredField("mContext")
                    contextField.isAccessible = true
                    contextField.set(contentProvider, delegateApplication)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
