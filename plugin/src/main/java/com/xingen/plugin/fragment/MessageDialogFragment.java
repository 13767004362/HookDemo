package com.xingen.plugin.fragment;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class MessageDialogFragment extends DialogFragment {
    public static final String TAG=MessageDialogFragment.class.getSimpleName();
    public MessageDialogFragment (){}
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
    public static void startDialog(Activity activity ){
        MessageDialogFragment  fragment=new MessageDialogFragment();
        fragment.show(activity.getFragmentManager(),MessageDialogFragment.TAG);
    }
}
