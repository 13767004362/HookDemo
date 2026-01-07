package com.xingen.hookdemo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.apply

/**
 * Created by HeXinGen  on 2025/11/18
 * Description:
 *
 * 采用hook方式，实现整个app 适配全屏edge-to-edge模式
 */
class HookSystemBar {


    fun hookSystemBar(application: Application) {
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?
            ) {
                // 适配android 15 edge-to-edge
                activity.adapterSystemBarEdgeToEdge()
                // 设置状态栏的黑色字体
                activity.setStatusBarLightMode(true)
            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {

            }

            override fun onActivityPaused(activity: Activity) {

            }

            override fun onActivityStopped(activity: Activity) {

            }

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle
            ) {

            }

            override fun onActivityDestroyed(activity: Activity) {

            }

        })
    }

    /**
     * 设置状态栏字体颜色
     * @param light true表示黑色字体，false表示白色字体
     */
    fun Activity.setStatusBarLightMode(light: Boolean = true) {
        // WindowCompat 已经处理android 11 和android 6.0的版本兼容问题
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
        }
    }

    /**
     * 适配系统状态栏，包含状态栏和导航栏，采用默认根布局设置。可在BaseActivity中调用。
     */
    fun Activity.adapterSystemBarEdgeToEdge() {
        val view = this.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            // WindowInsetsCompat.Type.systemBars() 获取了状态栏和导航栏的边衬，通过 WindowInsetsCompat.Type.displayCutout() 获取了刘海屏的边衬
            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}