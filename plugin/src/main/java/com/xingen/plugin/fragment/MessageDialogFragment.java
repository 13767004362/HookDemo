package com.xingen.plugin.fragment;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class MessageDialogFragment extends DialogFragment {
    public static final String TAG=MessageDialogFragment.class.getSimpleName();

    public static void startDialog(Activity activity ){
        MessageDialogFragment  fragment=new MessageDialogFragment();
        fragment.show(activity.getFragmentManager(),MessageDialogFragment.TAG);
    }
    public MessageDialogFragment (){

    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // 屏幕旋转，重建，可能导致 找不到当前的Fragment
        ContextImplHookManager.hookClassLoader(getActivity().getBaseContext(),MessageDialogFragment.class.getClassLoader());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return createTV();
    }
    private TextView createTV(){
        TextView textView=new TextView(getActivity());
        textView.getPaint().setFakeBoldText(true);
        textView.setPadding(50,20,50,20);
        textView.setText("Android插件化之Hook");
        return  textView;
    }


    public  static class ContextImplHookManager {


        /**
         *  Hook 掉ContextImpl中ClassLoader ,替换成自己指定的。
         * @param context
         * @param substituteClassLoader
         */
        public static void hookClassLoader(Context context,ClassLoader  substituteClassLoader){
            try {
                Class<?> contextImplClass=Class.forName("android.app.ContextImpl");
                Field[] fields= contextImplClass.getDeclaredFields();
                Field mClassLoaderField=null,mPackageInfoField=null;
                for (Field field:fields){
                    if (field.getName().equals("mClassLoader")){
                        mClassLoaderField=field;
                    }else if (field.getName().equals("mPackageInfo")){
                        mPackageInfoField=field;
                    }
                }
                if (mClassLoaderField!=null){
                    try {
                        mClassLoaderField.setAccessible(true);
                        mClassLoaderField.set(context,substituteClassLoader);
                    }catch ( Exception e){
                        e.printStackTrace();
                    }
                    return;
                }
                if (mPackageInfoField!=null){
                    try {
                        mPackageInfoField.setAccessible(true);
                        Object LoadedApk =  mPackageInfoField.get(context);
                        Class<?> LoadedApkClass=LoadedApk.getClass();
                        mClassLoaderField= LoadedApkClass.getDeclaredField("mClassLoader");
                        mClassLoaderField.setAccessible(true);
                        mClassLoaderField.set(LoadedApk,substituteClassLoader);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}
