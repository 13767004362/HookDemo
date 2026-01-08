## **自定义aapt.exe，且通过aapt修改资源的前缀**

在实际开发中,android  编译出来的apk中resource.arsc文件中资源前缀默认是0x7f。若是加载的插件apk 与宿主中资源前缀一样，则存在资源冲突问题。

![](https://github.com/13767004362/HookDemo/blob/master/aapt/android%E8%87%AA%E5%B8%A6%E7%9A%84%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E5%89%8D%E7%BC%80.png)

当前android studio 中使用aapt2 来编译资源，可通过指定插件中的resource前缀来解决问题：
```groovy
    aaptOptions {
       //aapt 修改资源id
       //aaptOptions.additionalParameters '--PLUG-resoure-id', '0x72'

        // aapt2 修改资源id
        additionalParameters '--allow-reserved-package-id','--package-id','0x72'
    }
```
![](https://github.com/13767004362/HookDemo/blob/master/aapt/aapt%E4%BF%AE%E6%94%B9%E5%90%8E%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E7%9A%84%E5%89%8D%E7%BC%80.png)

资源参考：
- https://blog.csdn.net/ws6013480777777/article/details/89926843

