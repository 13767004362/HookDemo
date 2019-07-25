

### **先来了解一下应用程序安装过程**

系统启动过程中,会扫描某些目录的程序，安装加载过程，为应用程序分配Linux的用户Id和Linux的用户组Id,也会解析程序。

先来看下
scanPackageLI():
```java
 private PackageParser.Package scanPackageLI(File scanFile, int parseFlags, int scanFlags,
            long currentTime, UserHandle user) throws PackageManagerException {
        if (DEBUG_INSTALL) Slog.d(TAG, "Parsing: " + scanFile);
        // 关键点  包解析器
        PackageParser pp = new PackageParser();
        pp.setSeparateProcesses(mSeparateProcesses);
        pp.setOnlyCoreApps(mOnlyCore);
        pp.setDisplayMetrics(mMetrics);

        if ((scanFlags & SCAN_TRUSTED_OVERLAY) != 0) {
            parseFlags |= PackageParser.PARSE_TRUSTED_OVERLAY;
        }

        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parsePackage");
        final PackageParser.Package pkg;
        try {
            //解析 程序的信息,存储在Package对象中。
            pkg = pp.parsePackage(scanFile, parseFlags);
        } catch (PackageParserException e) {
            throw PackageManagerException.from(e);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }

        return scanPackageLI(pkg, scanFile, parseFlags, scanFlags, currentTime, user);
    }

```
这是一个关键点,根据指定路径下的apk,解析出里面信息,存储在Package对象中。
根据这个思路，就可以获取到插件中一些标签,例如 Activity、Service、广播等。

接下来查看,PackageParser是如何解析apk的。

[PackageParser类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/content/pm/PackageParser.java)

parsePackage():
```java
    public Package parsePackage(File packageFile, int flags) throws PackageParserException {
        if (packageFile.isDirectory()) {
            return parseClusterPackage(packageFile, flags);
        } else {
            return parseMonolithicPackage(packageFile, flags);
        }
    }
```
parseMonolithicPackage():
```java
 public Package parseMonolithicPackage(File apkFile, int flags) throws PackageParserException {
        final PackageLite lite = parseMonolithicPackageLite(apkFile, flags);
        if (mOnlyCoreApps) {
            if (!lite.coreApp) {
                throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
                        "Not a coreApp: " + apkFile);
            }
        }

        final AssetManager assets = new AssetManager();
        try {
            final Package pkg = parseBaseApk(apkFile, assets, flags);
            pkg.setCodePath(apkFile.getAbsolutePath());
            pkg.setUse32bitAbi(lite.use32bitAbi);
            return pkg;
        } finally {
            IoUtils.closeQuietly(assets);
        }
    }

```
parseBaseApk():
```java
  private Package parseBaseApk(Resources res, XmlResourceParser parser, int flags,
            String[] outError) throws XmlPullParserException, IOException {
        final String splitName;
        final String pkgName;

        try {
            Pair<String, String> packageSplit = parsePackageSplitNames(parser, parser);
            pkgName = packageSplit.first;
            splitName = packageSplit.second;

            if (!TextUtils.isEmpty(splitName)) {
                outError[0] = "Expected base APK, but found split " + splitName;
                mParseError = PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME;
                return null;
            }
        } catch (PackageParserException e) {
            mParseError = PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME;
            return null;
        }

        final Package pkg = new Package(pkgName);
        TypedArray sa = res.obtainAttributes(parser,
                com.android.internal.R.styleable.AndroidManifest);
        pkg.mVersionCode = pkg.applicationInfo.versionCode = sa.getInteger(
                com.android.internal.R.styleable.AndroidManifest_versionCode, 0);
        pkg.baseRevisionCode = sa.getInteger(
                com.android.internal.R.styleable.AndroidManifest_revisionCode, 0);
        pkg.mVersionName = sa.getNonConfigurationString(
                com.android.internal.R.styleable.AndroidManifest_versionName, 0);
        if (pkg.mVersionName != null) {
            pkg.mVersionName = pkg.mVersionName.intern();
        }
        pkg.coreApp = parser.getAttributeBooleanValue(null, "coreApp", false);
        sa.recycle();
        return parseBaseApkCommon(pkg, null, res, parser, flags, outError);
    }
```

parseBaseApkCommon():
```java
private Package parseBaseApkCommon(Package pkg, Set<String> acceptedTags, Resources res,
            XmlResourceParser parser, int flags, String[] outError) throws XmlPullParserException,
            IOException {
        mParseInstrumentationArgs = null;
        mParseActivityArgs = null;
        mParseServiceArgs = null;
        mParseProviderArgs = null;
        // ...省略部分源码
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
             if (tagName.equals(TAG_APPLICATION)) {
                if (foundApp) {
                    if (RIGID_PARSER) {
                        outError[0] = "<manifest> has more than one <application>";
                        mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                        return null;
                    } else {
                        Slog.w(TAG, "<manifest> has more than one <application>");
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                }
                foundApp = true;
                //解析Application标签中的四大组件和其他标签
                if (!parseBaseApplication(pkg, res, parser, flags, outError)) {
                    return null;
                }
            }       
        }
                
}
```
parseBaseApplication():
```java
 private boolean parseBaseApplication(Package owner, Resources res,
            XmlResourceParser parser, int flags, String[] outError)
        throws XmlPullParserException, IOException {
        
        final ApplicationInfo ai = owner.applicationInfo;
        final String pkgName = owner.applicationInfo.packageName;
        // ...省略部分源码
        final int innerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
            String tagName = parser.getName();
            if (tagName.equals("activity")) { // Activity标签
                Activity a = parseActivity(owner, res, parser, flags, outError, false,
                        owner.baseHardwareAccelerated);
                if (a == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.activities.add(a);
            } else if (tagName.equals("receiver")) { 
               // 注意点：将Broadcast当成Activity来处理的。
                Activity a = parseActivity(owner, res, parser, flags, outError, true, false);
                if (a == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.receivers.add(a);
            } else if (tagName.equals("service")) { // 解析Service标签
                Service s = parseService(owner, res, parser, flags, outError);
                if (s == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.services.add(s);
            } else if (tagName.equals("provider")) {
                Provider p = parseProvider(owner, res, parser, flags, outError);
                if (p == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.providers.add(p);
            }
            //... 省略部分源码
        }
        return true;
}
```
从上可知：解析好的广播会当Activity一样处理，存储在Package对象中的receivers集合中。

[PackageParser类](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/content/pm/PackageParser.java)中的静态内部类`Package`
```java
   /**
     * Representation of a full package parsed from APK files on disk. A package
     * consists of a single base APK, and zero or more split APKs.
     */
public final static class Package {
        
        public final ArrayList<Permission> permissions = new ArrayList<Permission>(0);
        public final ArrayList<PermissionGroup> permissionGroups = new ArrayList<PermissionGroup>(0);
        public final ArrayList<Activity> activities = new ArrayList<Activity>(0);
        public final ArrayList<Activity> receivers = new ArrayList<Activity>(0);
        public final ArrayList<Provider> providers = new ArrayList<Provider>(0);
        public final ArrayList<Service> services = new ArrayList<Service>(0);
    
}   
```



只需要拿到插件的Package对象，就可以拿到插件中静态注册的广播信息。

### **实战案例**
---

**思路**：

**插件中静态广播处理方式**：
1. 将插件的Dex加载进JVM
2. 通过PackageParser解析APK生成对应的Package信息
3. 从Package对象中拿到静态注册的广播信息，当做动态广播注册。

**插件中动态广播处理方式**：

1. 将插件的Dex加载进JVM,便可以动态注册


根据思路分析，开始编码实现。

在插件中编写一个广播类
```java
public class PluginReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context.getApplicationContext()," 插件的广播收到 订阅信息",Toast.LENGTH_SHORT).show();
    }
}
```
在AndroidManifest.xml中注册：
```java
        <receiver android:name="com.xingen.plugin.receiver.PluginReceiver">
            <intent-filter>
                <action android:name="com.xingen.plugin.receiver.PluginReceiver"></action>
            </intent-filter>
        </receiver>
```
在宿主中开始解析插件apk获取到对应的广播信息。
```java
 /**
     *  解析插件中的广播
     * @param apkFilePath
     */
    private static void preloadParseReceiver(String apkFilePath) {
        try {
            // 先获取PackageParser对象
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            Object packageParser = packageParserClass.newInstance();
            //接着获取PackageParser.Package
            Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
            parsePackageMethod.setAccessible(true);
            Object packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS);
            // 接着获取到Package中的receivers列表
            Class<?> packageParser$package_Class = packageParser$package.getClass();
            Field receiversField= packageParser$package_Class.getDeclaredField("receivers");
            receiversField.setAccessible(true);
            List receiversList= (List) receiversField.get(packageParser$package);
            Class<?> packageParser$Activity_Class =Class.forName("android.content.pm.PackageParser$Activity");
            // intent-filter过滤器
            Field intentsFiled=packageParser$Activity_Class.getField("intents");
            intentsFiled.setAccessible(true);
            // 获取 name
            Field infoField=packageParser$Activity_Class.getDeclaredField("info");
            infoField.setAccessible(true);
            for (Object receiver:receiversList){
                ActivityInfo info=(ActivityInfo) infoField.get(receiver);
                List<? extends IntentFilter> intentFiltersList= (List<? extends IntentFilter>) intentsFiled.get(receiver);
                receivers.put(info,intentFiltersList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```
获取到插件信息后,开始动态注册：
```java
    /**
     *  注册插件中的广播
     * @param context
     */
    private static void registerPluginReceiver(Context context){
        try {
            ClassLoader classLoader= ReceiverHookManager.class.getClassLoader();
            for ( ActivityInfo activityInfo:receivers.keySet()){
                List<? extends IntentFilter> intentFilters=receivers.get(activityInfo);
                BroadcastReceiver broadcastReceiver=(BroadcastReceiver) classLoader.loadClass(activityInfo.name).newInstance();
                if (intentFilters!=null&&broadcastReceiver!=null){
                    for ( IntentFilter intentFilter:intentFilters){
                         context.getApplicationContext().registerReceiver(broadcastReceiver,intentFilter);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
```
测试验证：
```java

    private void sendActionBroadcast() {
        Intent intent = new Intent();
        intent.setAction(PluginConfig.receiver_action);
        sendBroadcast(intent);
    }
```




