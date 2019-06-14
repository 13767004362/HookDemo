package com.xingen.hookdemo;

import android.content.Context;

import com.xingen.hookdemo.utils.Utils;

import java.io.File;

/**
 * @author HeXinGen
 * date 2019/6/13.
 *
 *   插件dex 中的配置
 */
public final class PluginConfig {

    public static final String apk_file_name = "plugin.apk";
    public static final String package_name ="com.xingen.plugin";
    public static final String activity_name="com.xingen.plugin.activity.TargetActivity";
    public  static final  String receiver_action="com.xingen.plugin.receiver.PluginReceiver";
    public static final String service_name="com.xingen.plugin.service.PluginService";
    public static final String provider_name="com.xingen.plugin.contentprovider.PluginContentProvider";



}
