
采用多个DexClassLoader去加载插件,插件中的fragment在宿主中Activity中使用,销毁重建，报错:

```java
Caused by: android.app.Fragment$InstantiationException: Unable to instantiate fragment  com.xingen.plugin.dialog.MessageDialogFragment:  
make sure class name exists, is public, and has an empty constructor that is public
        at android.app.Fragment.instantiate(Fragment.java:618)
        at android.app.FragmentState.instantiate(Fragment.java:104)
        at android.app.FragmentManagerImpl.restoreAllState(FragmentManager.java:1777)
        at android.app.Activity.onCreate(Activity.java:953)
        at com.matchvs.union.ad.demo.MainActivity.onCreate(MainActivity.java:17)

```

追踪一下该问题，从FragmentActivity销毁重建过程开始。


查看`FragmentActivity#onCreate()`:

[FragmentActivity类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/support/v4/java/android/support/v4/app/FragmentActivity.java)
```java
 protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            Parcelable p = savedInstanceState.getParcelable(FRAGMENTS_TAG);
            mFragments.restoreAllState(p, nc != null ? nc.fragments : null);
        }
    }
```

接下来，查看`FragmentController#restoreAllState()`

[FragmentController](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/support/v4/java/android/support/v4/app/FragmentController.java)类：
```java
   public void restoreAllState(Parcelable state, List<Fragment> nonConfigList) {
        mHost.mFragmentManager.restoreAllState(state,
                new FragmentManagerNonConfig(nonConfigList, null));
    }
```

接下来,查看`FragmentManager#restoreAllState()`：

[FragmentManager](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/support/v4/java/android/support/v4/app/FragmentManager.java)
```java
void restoreAllState(Parcelable state, FragmentManagerNonConfig nonConfig) {
   //.....
        for (int i=0; i<fms.mActive.length; i++) {
            FragmentState fs = fms.mActive[i];
            if (fs != null) {
                FragmentManagerNonConfig childNonConfig = null;
                if (childNonConfigs != null && i < childNonConfigs.size()) {
                    childNonConfig = childNonConfigs.get(i);
                }
                // 创建fragment对象
                Fragment f = fs.instantiate(mHost, mContainer, mParent, childNonConfig);

            }
        }
     //.....
}
```
接下来，查看`FragmentState#instantiate()`：

[Fragment](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/support/v4/java/android/support/v4/app/Fragment.java)
```java
 public static Fragment instantiate(Context context, String fname, @Nullable Bundle args) {
        try {
            Class<?> clazz = sClassMap.get(fname);
            if (clazz == null) {
                // Class not found in the cache, see if it's real, and try to add it
                // 通过PathClassLoader根据类名加载,关键点。
                clazz = context.getClassLoader().loadClass(fname);
                sClassMap.put(fname, clazz);
            }
            Fragment f = (Fragment)clazz.newInstance();
            if (args != null) {
                args.setClassLoader(f.getClass().getClassLoader());
                f.mArguments = args;
            }
            return f;
        } catch (ClassNotFoundException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": make sure class name exists, is public, and has an"
                    + " empty constructor that is public", e);
        } catch (java.lang.InstantiationException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": make sure class name exists, is public, and has an"
                    + " empty constructor that is public", e);
        } catch (IllegalAccessException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": make sure class name exists, is public, and has an"
                    + " empty constructor that is public", e);
        }
    }
```
宿主中的PathClassLoader中并没有加载插件的dex,因此会找不到对应的类。

解决方式：

1. 采用[合并式方案，单个ClassLoader](https://github.com/13767004362/HookDemo/blob/master/document/Android%E6%8F%92%E4%BB%B6%E5%8C%96%E4%B9%8BClasssLoader%E5%8A%A0%E8%BD%BD%E6%8F%92%E4%BB%B6Dex.md)
2. 将加载插件的ClassLoader设置成应用程序的ClassLoader,进行替换。


方案二实现如下：

替换掉ContextImpl中的classloader：

```Java
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
```
在`fragment#onAttach()` : 进行hook替换
```java
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // 屏幕旋转，重建，可能导致 找不到当前的Fragment
        ContextImplHookManager.hookClassLoader(getActivity().getBaseContext(),MessageDialogFragment.class.getClassLoader());
    }
```