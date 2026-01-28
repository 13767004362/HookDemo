package com.xingen.plugin.activity

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

class TargetActivity : Activity() {
    override fun onCreate( savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "启动成功TargetActivity", Toast.LENGTH_SHORT).show()
        val textView = TextView(this)
        setContentView(textView)
        textView.text = "插件化之Hook，启动不在AndroidManifest.xml中声明的Activity"
        textView.gravity = Gravity.CENTER
    }
}
