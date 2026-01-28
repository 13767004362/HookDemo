package com.xingen.hookdemo.hook.resource

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import java.lang.ref.WeakReference
import java.util.Map
/**
 * @author HeXinGen
 * date 2019/6/20.
 */

object ResourceHookManager {
    private const val TAG = "ResourceHookManager"
    private var multiResources: Resources? = null

    @JvmStatic
    fun init(context: Context, apkFilePath: String) {
        preloadResource(context, apkFilePath)
    }

    @Synchronized
    @Suppress("all")
    private fun preloadResource(context: Context, apkFilePath: String) {
        try {
            // 先创建AssetManager
            val assetManagerClass = AssetManager::class.java
            val assetManager = assetManagerClass.newInstance()
            // 将插件资源和宿主资源通过 addAssetPath方法添加进去
            val addAssetPathMethod =
                assetManagerClass.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.isAccessible = true
            val hostResourcePath = context.packageResourcePath
            val result1 = addAssetPathMethod.invoke(assetManager, hostResourcePath) as Int
            val result2 = addAssetPathMethod.invoke(assetManager, apkFilePath) as Int
            // 接下来创建，合并资源后的Resource
            val resources = Resources(
                assetManager,
                context.resources.displayMetrics,
                context.resources.configuration
            )
            // 替换 ContextImpl 中Resource对象
            val contextImplClass = context.javaClass
            val resourcesField1 = contextImplClass.getDeclaredField("mResources")
            resourcesField1.isAccessible = true
            resourcesField1.set(context, resources)
            // 先获取到LoadApk对象
            val loadedApkField = contextImplClass.getDeclaredField("mPackageInfo")
            loadedApkField.isAccessible = true
            val loadApk = loadedApkField.get(context)

            val loadApkClass = loadApk.javaClass
            // 替换掉LoadApk中的Resource对象。
            val resourcesField2 = loadApkClass.getDeclaredField("mResources")
            resourcesField2.isAccessible = true
            resourcesField2.set(loadApk, resources)
            //获取到ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField =
                activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            val activityThread = sCurrentActivityThreadField.get(null)
            // 获取到ResourceManager对象
            val resourcesManagerField = activityThreadClass.getDeclaredField("mResourcesManager")
            resourcesManagerField.isAccessible = true
            val resourcesManager = resourcesManagerField.get(activityThread)
            // 替换掉ResourceManager中resource对象
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                val resourcesManagerClass = resourcesManager.javaClass
                val mActiveResourcesField =
                    resourcesManagerClass.getDeclaredField("mActiveResources")
                mActiveResourcesField.isAccessible = true
                val map =
                    mActiveResourcesField.get(resourcesManager) as Map<Object, WeakReference<Resources>>
                val key = map.keySet().iterator().next()
                map.put(key, WeakReference(resources))
            } else {
                val resourcesManagerClass = resourcesManager.javaClass
                val mResourceImplsField = resourcesManagerClass.getDeclaredField("mResourceImpls")
                mResourceImplsField.isAccessible = true
                val map = mResourceImplsField.get(resourcesManager) as Map<Object, WeakReference<*>>
                val key = map.keySet().iterator().next()
                val mResourcesImplField = Resources::class.java.getDeclaredField("mResourcesImpl")
                mResourcesImplField.isAccessible = true
                val resourcesImpl = mResourcesImplField.get(resources)
                map.put(key, WeakReference(resourcesImpl))

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                    //在android 9.0 以上 创建Activity会单独创建Resource,并没有使用LoadApk中Resource.
                    // 因此考虑将插件资源放到LoadApk中mSplitResDirs数组中
                    try {
                        val mSplitResDirsField = loadApkClass.getDeclaredField("mSplitResDirs")
                        mSplitResDirsField.isAccessible = true
                        val temp = arrayOf(apkFilePath)
                        mSplitResDirsField.set(loadApk, temp)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            multiResources = resources
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDrawableId(resName: String, packageName: String): Int {
        var imgId = multiResources?.getIdentifier(resName, "mipmap", packageName) ?: 0
        if (imgId == 0) {
            imgId = multiResources?.getIdentifier(resName, "drawable", packageName) ?: 0
        }
        return imgId
    }

    fun getMultiResources(): Resources? {
        return multiResources
    }
}
