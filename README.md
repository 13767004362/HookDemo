

**前言**：
>开源盛行的插件化框架有很多，实现方式也有很多种。本项目是介绍如何加载dex文件(java代码)、so库（c++代码）、资源文件（resource）到虚拟机，围绕Android Framework源码，如何启动插件中Activity、Service、BroadcastReceiver、ContentProvider四大组件,从而了解到插件化中核心知识点。

**注意点**：若是实际项目中使用，推举使用成熟的插件化框架，存在的bug较少。


**插件化教程核心点**: 

- Android 插件化之ClassLoader加载Dex文件
- Android 插件化之ClassLoader加载so库
- [Android 插件化之aapt修改资源前缀](https://github.com/13767004362/HookDemo/blob/master/aapt/Android%E6%8F%92%E4%BB%B6%E5%8C%96%E4%B9%8Baapt%E4%BF%AE%E6%94%B9%E8%B5%84%E6%BA%90%E5%89%8D%E7%BC%80.md)
- [Android 插件化之加载插件资源](https://github.com/13767004362/HookDemo/blob/master/document/Android%E6%8F%92%E4%BB%B6%E5%8C%96%E4%B9%8B%E5%8A%A0%E8%BD%BDResource%E8%B5%84%E6%BA%90.md)
- Android 插件化之启动Activity
- Android 插件化之加载Service
- Android 插件化之加载BroadcastReceiver
- Android 插件化之加载ContentProvider
- Android 插件化之Fragment重建问题

**备注点**：以上插件化核心点的代码已经全部实现,详细介绍正在完善。

**项目特别说明**：

1. 需要先阅读[Android 插件化之aapt修改资源前缀](https://github.com/13767004362/HookDemo/blob/master/aapt/Android%E6%8F%92%E4%BB%B6%E5%8C%96%E4%B9%8Baapt%E4%BF%AE%E6%94%B9%E8%B5%84%E6%BA%90%E5%89%8D%E7%BC%80.md)，接着，配置好aapt。
2. 在导入项目，进行调试。


----------

**Android P以上非公开API限制问题**

android官方在9.0以上版本，限制反射调用hide隐藏api，给插件化带来一片阴影。

不过，国内大牛厉害，针对该问题已经有过墙梯。详细方案，请阅读[另一种绕过 Android P以上非公开API限制的办法](http://weishu.me/2019/03/16/another-free-reflection-above-android-p/), 使用[FreeReflection ](https://github.com/tiann/FreeReflection)库便可解决。


----------

**资源参考**：

- [DroidPlugin解析教程](https://github.com/tiann/understand-plugin-framework)
- [VirtualApk解析](http://www.androidos.net.cn/codebook/AndroidRoad/android/advance/virtualapk.html)
- [Android插件化技术](https://mp.weixin.qq.com/s/Uwr6Rimc7Gpnq4wMFZSAag?utm_source=androidweekly&utm_medium=website)
