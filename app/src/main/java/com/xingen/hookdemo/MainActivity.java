package com.xingen.hookdemo;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.xingen.hookdemo.hook.ams.AMSHookManager;
import com.xingen.hookdemo.hook.receiver.ReceiverHookManager;
import com.xingen.hookdemo.hook.resource.ResourceHookManager;

import java.lang.reflect.Method;

public class MainActivity extends Activity implements View.OnClickListener {


    private ClassLoader appMainClassLoader;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        appMainClassLoader = this.getClassLoader();
        String apkFilePath = PluginConfig.getZipFilePath(this);
        // hook  ams
        String subPackageName = getPackageName();
        AMSHookManager.init(newBase,subPackageName);
        // hook 广播
        ReceiverHookManager.init(this, apkFilePath);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.main_hook_fragment_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_activity_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_receiver_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_service_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_content_provider).setOnClickListener(this);
        findViewById(R.id.main_hook_resource).setOnClickListener(this);
        findViewById(R.id.main_hook_native).setOnClickListener(this);
        findViewById(R.id.main_hook_application_btn).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_hook_application_btn:
                Application application=getApplication();
                Toast.makeText(getApplicationContext()," Application的类名是： "+application.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            break;
            case R.id.main_hook_fragment_btn:
                loadPlugin();
                break;
            case R.id.main_hook_activity_btn:
                loadTargetActivity();
                break;
            case R.id.main_hook_receiver_btn: {
                sendActionBroadcast();
            }
            break;
            case R.id.main_hook_service_btn: {
                startPluginService();
            }
            break;
            case R.id.main_hook_content_provider: {
                useContentProvider(v);
            }
            break;

            case R.id.main_hook_resource:
                usePluginResource();
                break;
            case R.id.main_hook_native:{
                useNativeLibrary();
            }
                break;

        }
    }

    private void useNativeLibrary(){
        try {
            Class<?> mClass=appMainClassLoader.loadClass(PluginConfig.native_class_name);
            Object instance= mClass.newInstance();
            Method  getShowContentMethod=mClass.getDeclaredMethod("getShowContent");
            getShowContentMethod.setAccessible(true);
           String content=(String) getShowContentMethod.invoke(instance);
           if (!TextUtils.isEmpty(content)){
               Toast.makeText(this,content,Toast.LENGTH_SHORT).show();
           }
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    private void usePluginResource() {
        ImageView imageView = findViewById(R.id.main_show_plugin_img_iv);
        int imgId=ResourceHookManager.getDrawableId("plugin_img", PluginConfig.package_name);
        imageView.setImageDrawable(getResources().getDrawable(imgId));
    }

    private void useContentProvider(View view) {
        final Uri uri = Uri.parse("content://" + PluginConfig.provider_name);
        final String column_name = "name";
        Button button = (Button) view;
        final String text_query = "Hook 使用content_provider 查询";
        final String text_insert = "Hook 使用content_provider 插入";
        ContentResolver contentResolver = getContentResolver();
        if (text_query.equals(button.getText().toString())) {// 查询
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        stringBuffer.append(cursor.getString(cursor.getColumnIndex(column_name)));
                        stringBuffer.append(",");
                    } while (cursor.moveToNext());
                }
                if (!TextUtils.isEmpty(stringBuffer.toString())) {
                    Toast.makeText(getApplicationContext(), "查询到的名字：" + stringBuffer.toString(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                button.setText(text_insert);
            }
        } else { // 插入
            ContentValues contentValues = new ContentValues();
            contentValues.put(column_name, "android " + (int) (Math.random() * 100));
            contentResolver.insert(uri, contentValues);
            button.setText(text_query);
        }
    }

    private void startPluginService() {
        ComponentName componentName = new ComponentName(PluginConfig.package_name, PluginConfig.service_name);
        startService(new Intent().setComponent(componentName));
    }

    private void sendActionBroadcast() {
        Intent intent = new Intent();
        intent.setAction(PluginConfig.receiver_action);
        sendBroadcast(intent);
    }

    /**
     * 绕过ams，启动目标的Activity
     */
    private void loadTargetActivity() {
        ComponentName componentName = new ComponentName(PluginConfig.package_name, PluginConfig.activity_name);
        startActivity(new Intent().setComponent(componentName));
    }

    /**
     * 加载插件
     */
    private void loadPlugin() {
        //开始显示插件中的DialogFragment
        try {
            //通过dexClassLoader加载指定包名的类
            Class<?> mClass = appMainClassLoader.loadClass("com.xingen.plugin.fragment.MessageDialogFragment");
            Method method = mClass.getDeclaredMethod("startDialog", Activity.class);
            method.setAccessible(true);
            method.invoke(null, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
