## **自定义aapt.exe，且通过aapt修改资源的前缀**

### **1. 编译aappt程序**

如何编译自定义aapt，网上教程较多。这里不做多讲解，请自行阅读[Android中如何修改编译的资源ID值(默认值是0x7F...可以随意改成0x02~0x7E)](https://blog.csdn.net/jiangwei0910410003/article/details/50820219)。

### **2.使用自定义的aapt程序打包,修改资源前缀**

**2.1. 替换原有sdk中的aapt程序**：

**做法**：

先将上一步骤编译好的aapt.exe替换到studio中sdk\build-tools\工程中使用的编译版本\aapt.exe替换。

**详细步骤**：
   
1. 将当前项目`aapt/aapt_win.exe`改成`aapt.exe`。
2. 选择现有编译版本，例如：使用build版本27放置到,那么对应的aapt程序的目录为`sdk\build-tools\27.0.3`
3. 将`aapt.exe`拷贝到 `H:\Android\sdk\build-tools\27.0.3\`中进行替换。

**2.2 AndroidStudio中配置aapt**

2.2.1 在需要修改资源前缀的Module的build.gradle中**:

```
android {
    aaptOptions {
        aaptOptions.additionalParameters '--PLUG-resoure-id', '0x72'
    }
}
```

`0x72`这个值是自行配置的前缀，根据实际需求自行配置。


2.2.2 关闭aapt2的优化

在项目的gradle.properties中添加： 用于关闭aapt2。
```
android.enableAapt2=false
```

### **3.效果展示**


正常的应用程序的资源前缀如下图所示：

![](https://github.com/13767004362/HookDemo/blob/master/aapt/android%E8%87%AA%E5%B8%A6%E7%9A%84%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E5%89%8D%E7%BC%80.png)

aapt修改后的资源如下图所示：

![](https://github.com/13767004362/HookDemo/blob/master/aapt/aapt%E4%BF%AE%E6%94%B9%E5%90%8E%E5%BA%94%E7%94%A8%E7%A8%8B%E5%BA%8F%E7%9A%84%E5%89%8D%E7%BC%80.png)



**资源参考**：

- [Android中如何修改编译的资源ID值(默认值是0x7F...可以随意改成0x02~0x7E)](https://blog.csdn.net/jiangwei0910410003/article/details/50820219)
- [
Android插件化原理和实践 (五) 之 解决合并资源后资源Id冲突的问题](https://blog.csdn.net/hwliu51/article/details/76945286)
