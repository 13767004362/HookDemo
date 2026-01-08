package com.xingen.hookdemo

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import com.xingen.hookdemo.databinding.ActivityMainBinding
import com.xingen.hookdemo.hook.ams.AMSHookManager
import com.xingen.hookdemo.hook.pms.PMSHookManger
import com.xingen.hookdemo.hook.receiver.ReceiverHookManager
import com.xingen.hookdemo.hook.resource.ResourceHookManager

class MainActivity : AppCompatActivity() {
    private var appMainClassLoader: ClassLoader? = null
    private lateinit var binding: ActivityMainBinding


    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        appMainClassLoader = this.classLoader
        val apkFilePath = PluginConfig.getZipFilePath(this)
        // hook  ams
        val subPackageName = packageName
        AMSHookManager.init(newBase, subPackageName)
        // hook 广播
        ReceiverHookManager.init(this, apkFilePath)
        Looper.getMainLooper().setMessageLogging { x: String? -> }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(this.layoutInflater)
        setContentView(binding.getRoot())
        initView()
    }

    private fun initView() {
        binding.mainHookFragmentBtn.setOnClickListener {
            loadPlugin()
        }
        binding.mainHookActivityBtn.setOnClickListener {
            loadTargetActivity()
        }
        binding.mainHookReceiverBtn.setOnClickListener {
            sendActionBroadcast()
        }
        binding.mainHookServiceBtn.setOnClickListener {
            startPluginService()
        }
        binding.mainHookContentProvider.setOnClickListener {
            useContentProvider(it)
        }
        binding.mainHookResource.setOnClickListener {
            usePluginResource()
        }
        binding.mainHookNative.setOnClickListener {
            useNativeLibrary()
        }
        binding.mainHookPmsSign.setOnClickListener {
            PMSHookManger.printAppSignature(this)
        }
        binding.mainHookApplicationBtn.setOnClickListener {
            val application = getApplication()
            Toast.makeText(
                applicationContext,
                " Application的类名是： " + application.javaClass.getSimpleName(),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun useNativeLibrary() {
        try {
            val mClass = appMainClassLoader!!.loadClass(PluginConfig.native_class_name)
            val instance: Any = mClass.newInstance()
            val getShowContentMethod = mClass.getDeclaredMethod("getShowContent")
            getShowContentMethod.isAccessible = true
            val content = getShowContentMethod.invoke(instance) as String?
            if (!TextUtils.isEmpty(content)) {
                Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun usePluginResource() {
        val imageView = findViewById<ImageView>(R.id.main_show_plugin_img_iv)
        val imgId = ResourceHookManager.getDrawableId("plugin_img", PluginConfig.package_name)
        imageView.setImageDrawable(ResourcesCompat.getDrawable(this.resources, imgId, this.theme))
    }

    private fun useContentProvider(view: View) {
        val uri = ("content://" + PluginConfig.provider_name).toUri()
        val columnName = "name"
        val button = view as Button
        val textQuery = "Hook 使用content_provider 查询"
        val textInsert = "Hook 使用content_provider 插入"
        val contentResolver = getContentResolver()
        if (textQuery == button.getText().toString()) { // 查询
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                val stringBuffer = StringBuffer()
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        val index = cursor.getColumnIndex(columnName)
                        stringBuffer.append(cursor.getString(index))
                        stringBuffer.append(",")
                    } while (cursor.moveToNext())
                }
                if (!TextUtils.isEmpty(stringBuffer.toString())) {
                    Toast.makeText(
                        applicationContext,
                        "查询到的名字：$stringBuffer",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
                button.text = textInsert
            }
        } else { // 插入
            val contentValues = ContentValues()
            contentValues.put(columnName, "android " + (Math.random() * 100).toInt())
            contentResolver.insert(uri, contentValues)
            button.text = textQuery
        }
    }

    private fun startPluginService() {
        val componentName = ComponentName(PluginConfig.package_name, PluginConfig.service_name)
        startService(Intent().setComponent(componentName))
    }

    private fun sendActionBroadcast() {
        val intent = Intent()
        intent.setAction(PluginConfig.receiver_action)
        sendBroadcast(intent)
    }

    /**
     * 绕过ams，启动目标的Activity
     */
    private fun loadTargetActivity() {
        val componentName = ComponentName(PluginConfig.package_name, PluginConfig.activity_name)
        startActivity(Intent().setComponent(componentName))
    }

    /**
     * 加载插件
     */
    private fun loadPlugin() {
        //开始显示插件中的DialogFragment
        try {
            //通过dexClassLoader加载指定包名的类
            val mClass =
                appMainClassLoader!!.loadClass("com.xingen.plugin.fragment.MessageDialogFragment")
            val method = mClass.getDeclaredMethod("startDialog", AppCompatActivity::class.java)
            method.isAccessible = true
            method.invoke(null, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
