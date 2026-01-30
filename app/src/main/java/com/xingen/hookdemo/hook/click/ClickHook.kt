package com.xingen.hookdemo.hook.click

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener


/**
 * Created by HeXinGen  on 2026/1/30
 * Description:
 *
 * hook 全局的click 事件
 *
 *
 */
object ClickHook {

    val TAG = "ClickHook"


    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?
            ) {
                hookActivityClick(activity, true)

            }

            override fun onActivityDestroyed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle
            ) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }
        })
    }

    private fun hookActivityClick(activity: Activity, auto: Boolean) {
        val rootView = activity.window.decorView
        val task = Runnable {
            val viewList = Utils.getAllChildViews(rootView, true)
            viewList?.forEach {
                Utils.setHookViewClick(it)
            }
        }
        if (auto) {
            // 通过监听 view tree 来实现，自动监听
            val observer: ViewTreeObserver? = rootView.getViewTreeObserver()
            if (observer == null) {
                task.run()
            }
            observer!!.addOnGlobalLayoutListener(OnGlobalLayoutListener { task.run() })
        } else {
            task.run()
        }
    }

    internal object Utils {
        /**
         * 获取ViewGroup中具备点击事件的子view
         *
         * @param view
         * @param checkClick
         * @return
         */
        internal fun getAllChildViews(view: View, checkClick: Boolean): MutableList<View>? {
            var allChildren: MutableList<View>? = null
            if (view is ViewGroup) {
                val vp = view
                for (i in 0..<vp.childCount) {
                    val viewChild = vp.getChildAt(i)
                    if (checkClick && viewChild.hasOnClickListeners()) {
                        // 采用lazy 策略
                        if (allChildren == null) {
                            allChildren = ArrayList<View>()
                        }
                        allChildren.add(viewChild)
                    } else {
                        if (allChildren == null) {
                            allChildren = ArrayList<View>()
                        }
                        allChildren.add(viewChild)
                    }
                    //再次 调用本身（递归）
                    val tempList = getAllChildViews(viewChild, checkClick)
                    if (tempList != null) {
                        allChildren.addAll(tempList)
                    }
                }
            }
            return allChildren
        }


        /**
         * hook click事件
         *
         * @param view
         */

        @Suppress("all")
        internal fun setHookViewClick(view: View?): OnProxyClickListener? {
            try {
                val viewClass = Class.forName("android.view.View")
                val mListenerInfoField = viewClass.getDeclaredField("mListenerInfo")
                mListenerInfoField.isAccessible = true
                val mListenerInfoObject = mListenerInfoField.get(view)
                if (mListenerInfoObject != null) {
                    val listenerInfoClass = Class.forName("android.view.View\$ListenerInfo")
                    val mOnClickListenerField =
                        listenerInfoClass.getDeclaredField("mOnClickListener")
                    mOnClickListenerField.isAccessible = true
                    val onClickListener =
                        mOnClickListenerField.get(mListenerInfoObject) as View.OnClickListener?
                    if (onClickListener != null) {
                        //已经处理过快速点击的click，过滤
                        val proxy: OnProxyClickListener?
                        if (onClickListener !is OnProxyClickListener) {
                            proxy = OnProxyClickListener(onClickListener)
                            mOnClickListenerField.set(mListenerInfoObject, proxy)
                        } else {
                            proxy = onClickListener
                        }
                        return proxy
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    /**
     * clickListener的代理类
     */
    class OnProxyClickListener(private val delegate: View.OnClickListener) : View.OnClickListener {
        override fun onClick(v: View) {
            Log.i(TAG, " proxy click event ,view is $v")
            this.delegate.onClick(v)
        }
    }
}

