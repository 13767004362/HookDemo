package com.xingen.hookdemo.hook.click

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.isVisible


/**
 * Created by HeXinGen  on 2026/1/30
 * Description: hook 全局的click 事件，不采用反射
 *
 *
 * 思路：dispatchTouchEvent+ view 坐标回溯法。
 *
 *
 * - 在 dispatchTouchEvent 的 ACTION_UP 事件中，获取手指离开屏幕的坐标 (x, y)。
 * - 利用该坐标，从 DecorView 开始 递归遍历 View 树 ，找到当前坐标下最顶层且 可点击 (isClickable) 的 View。
 * - 如果找到了这个 View，说明这次点击是一个“有效的业务点击”。
 */
object SmartClickHook {
    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                hookWindowCallback(activity)
            }

            // ... 其他生命周期方法略
            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })
    }

    fun hookWindowCallback(activity: Activity) {
        val window = activity.getWindow()
        val localCallback = window.getCallback()
        window.setCallback(WindowCallbackProxy(localCallback, activity))
    }

    private class WindowCallbackProxy(target: Window.Callback, private val activity: Activity) :
        WindowCallbackAdapter(target) {
        private val mRect = Rect()


        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // 我们在 ACTION_UP 时进行捕获，因为此时点击动作已完成
            if (event.action == MotionEvent.ACTION_UP) {
                findAndReportClick(event)
            }
            return super.dispatchTouchEvent(event)
        }


        fun findAndReportClick(event: MotionEvent) {
            val decorView = activity.getWindow().getDecorView()
            val x = event.getRawX()
            val y = event.getRawY()

            // 递归寻找被点击的那个“最小”的 Clickable View
            val targetView = findTargetView(decorView, x, y)

            if (targetView != null) {
                // 只有真正可点击的 View 才会走到这里
                Log.d(
                    "ClickHook", ("有效点击 -> ViewID: " + getViewId(targetView)
                            + ", 类型: " + targetView.javaClass.getSimpleName())
                )
            }
        }

        fun findTargetView(view: View, x: Float, y: Float): View? {
            var target: View? = null
            if (isViewUnder(view, x, y) && view.isVisible) {
                // 如果是 ViewGroup，递归查找子 View
                if (view is ViewGroup) {
                    val group = view
                    // 从后往前遍历，因为后添加的 View 在上层
                    for (i in group.getChildCount() - 1 downTo 0) {
                        val child = group.getChildAt(i)
                        target = findTargetView(child, x, y)
                        if (target != null) break
                    }
                }

                // 如果子 View 没找到，或者当前 View 本身就是 Clickable
                if (target == null && view.isClickable()) {
                    target = view
                }
            }
            return target
        }

        fun isViewUnder(view: View, x: Float, y: Float): Boolean {
            view.getGlobalVisibleRect(mRect)
            return mRect.contains(x.toInt(), y.toInt())
        }

        fun getViewId(view: View): String? {
            if (view.id == View.NO_ID) return "no-id"
            try {
                return view.resources.getResourceEntryName(view.id)
            } catch (e: Exception) {
                return "unknown"
            }
        }
    }
}

/**
 * Window.Callback 的空实现代理类
 */
@Suppress("all")
open class WindowCallbackAdapter(private val mOriginalCallback: Window.Callback) : Window.Callback {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return mOriginalCallback.dispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        return mOriginalCallback.dispatchKeyShortcutEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return mOriginalCallback.dispatchTouchEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        return mOriginalCallback.dispatchTrackballEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return mOriginalCallback.dispatchGenericMotionEvent(event)
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        return mOriginalCallback.dispatchPopulateAccessibilityEvent(event)
    }

    override fun onCreatePanelView(featureId: Int): View? {
        return mOriginalCallback.onCreatePanelView(featureId)
    }

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean {
        return mOriginalCallback.onCreatePanelMenu(featureId, menu)
    }

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
        return mOriginalCallback.onPreparePanel(featureId, view, menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        return mOriginalCallback.onMenuOpened(featureId, menu)
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        return mOriginalCallback.onMenuItemSelected(featureId, item)
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
        mOriginalCallback.onWindowAttributesChanged(attrs)
    }

    override fun onContentChanged() {
        mOriginalCallback.onContentChanged()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        mOriginalCallback.onWindowFocusChanged(hasFocus)
    }

    override fun onAttachedToWindow() {
        mOriginalCallback.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        mOriginalCallback.onDetachedFromWindow()
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        mOriginalCallback.onPanelClosed(featureId, menu)
    }

    override fun onSearchRequested(): Boolean {
        return mOriginalCallback.onSearchRequested()
    }

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
        return mOriginalCallback.onSearchRequested(searchEvent)
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
        return mOriginalCallback.onWindowStartingActionMode(callback)
    }

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int
    ): ActionMode? {
        return mOriginalCallback.onWindowStartingActionMode(callback, type)
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        mOriginalCallback.onActionModeStarted(mode)
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        mOriginalCallback.onActionModeFinished(mode)
    }
}