#### **1. 编译aappt程序**

如何编译自定义aapt，请阅读[Android中如何修改编译的资源ID值(默认值是0x7F...可以随意改成0x02~0x7E)](https://blog.csdn.net/jiangwei0910410003/article/details/50820219)。

#### **2. 使用自定义的aapt程序打包,修改资源前缀**

2.1. 将当前目录下的aapt_win.exe替换到studio中sdk\build-tools\工程中使用的编译版本\aapt.exe替换。
   
例如，使用build版本27,那么对应的aapt程序的路径为
`： H:\Android\sdk\build-tools\27.0.3\aapt.exe`

2.2. 在需要修改资源前缀的Module的build.gradle中:

```
android {

    aaptOptions {
        aaptOptions.additionalParameters '--PLUG-resoure-id', '0x72'
    }
}
```



2.3 关闭aapt2的优化：

在项目的gradle.properties中添加
```

##关闭aapt2
android.enableAapt2=false
```

**资源参考**：

- [Android中如何修改编译的资源ID值(默认值是0x7F...可以随意改成0x02~0x7E)](https://blog.csdn.net/jiangwei0910410003/article/details/50820219)
- [
Android插件化原理和实践 (五) 之 解决合并资源后资源Id冲突的问题](https://blog.csdn.net/hwliu51/article/details/76945286)