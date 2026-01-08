package com.xingen.hookdemo

import android.content.Context
import com.xingen.hookdemo.utils.Utils
import java.io.File

/**
 * @author HeXinGen
 * date 2019/6/13.
 *
 * 插件dex 中的配置
 */
object PluginConfig {
    const val apk_file_name: String = "plugin.zip"
    const val package_name: String = "com.xingen.plugin"
    const val activity_name: String = "com.xingen.plugin.activity.TargetActivity"
    const val receiver_action: String = "com.xingen.plugin.receiver.PluginReceiver"
    const val service_name: String = "com.xingen.plugin.service.PluginService"
    const val provider_name: String = "com.xingen.plugin.contentprovider.PluginContentProvider"
    const val native_class_name: String = "com.xingen.plugin.NativeCodeTest"
    const val meta_application_key: String = "application"

    fun getZipFilePath(context: Context?): String {
        return File(
            Utils.getCacheDir(context).absolutePath + File.separator + apk_file_name
        ).absolutePath
    }
}
