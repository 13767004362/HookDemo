package com.xingen.plugin.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import java.lang.reflect.Field

class MessageDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "MessageDialogFragment"

        fun startDialog(activity: AppCompatActivity) {
            val fragment = MessageDialogFragment()
            fragment.show(activity.supportFragmentManager, TAG)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val activityBase = activity?.baseContext as? ContextThemeWrapper ?: return
        val contextImpl = activityBase.baseContext
        ContextImplHookManager.hookClassLoader(contextImpl, MessageDialogFragment::class.java.classLoader!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createTV()
    }

    private fun createTV(): TextView {
        val textView = TextView(activity)
        textView.paint.isFakeBoldText = true
        textView.setPadding(50, 20, 50, 20)
        textView.text = "Android插件化之Hook"
        return textView
    }

    object ContextImplHookManager {
        fun hookClassLoader(context: Context, substituteClassLoader: ClassLoader) {
            try {
                val contextImplClass = Class.forName("android.app.ContextImpl")
                val fields = contextImplClass.declaredFields
                var mClassLoaderField: Field? = null
                var mPackageInfoField: Field? = null

                for (field in fields) {
                    when (field.name) {
                        "mClassLoader" -> mClassLoaderField = field
                        "mPackageInfo" -> mPackageInfoField = field
                    }
                }

                mClassLoaderField?.let {
                    try {
                        it.isAccessible = true
                        it.set(context, substituteClassLoader)
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                mPackageInfoField?.let {
                    try {
                        it.isAccessible = true
                        val loadedApk = it.get(context)
                        val loadedApkClass = loadedApk?.javaClass
                        mClassLoaderField = loadedApkClass?.getDeclaredField("mClassLoader")
                        mClassLoaderField?.isAccessible = true
                        mClassLoaderField?.set(loadedApk, substituteClassLoader)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
