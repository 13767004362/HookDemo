
先来了解一下ContentProvider的安装和获取.

**安装ContentProvider过程**：

应用程序在创建Application的过程中,执行handleBindApplication(),会将contentprovider进行安装。

[ActivityThread](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ActivityThread.java)

```java
private void handleBindApplication(AppBindData data) {
    //...省略部分源码
    if (!data.restrictedBackupMode) {
                if (!ArrayUtils.isEmpty(data.providers)) {
                    installContentProviders(app, data.providers);
                    // For process that contains content providers, we want to
                    // ensure that the JIT is enabled "at some point".
                    mH.sendEmptyMessageDelayed(H.ENABLE_JIT, 10*1000);
                }
    }
}
```
installContentProviders():
```java
  private void installContentProviders(
            Context context, List<ProviderInfo> providers) {
        final ArrayList<IActivityManager.ContentProviderHolder> results =
            new ArrayList<IActivityManager.ContentProviderHolder>();

        for (ProviderInfo cpi : providers) {
            if (DEBUG_PROVIDER) {
                StringBuilder buf = new StringBuilder(128);
                buf.append("Pub ");
                buf.append(cpi.authority);
                buf.append(": ");
                buf.append(cpi.name);
                Log.i(TAG, buf.toString());
            }
            IActivityManager.ContentProviderHolder cph = installProvider(context, null, cpi,
                    false /*noisy*/, true /*noReleaseNeeded*/, true /*stable*/);
            if (cph != null) {
                cph.noReleaseNeeded = true;
                results.add(cph);
            }
        }

        try {
            // 将安装好的ContentProvider代理对象和相应一些信息，存储在AMS中，供其他应用程序进行访问。
            ActivityManagerNative.getDefault().publishContentProviders(
                getApplicationThread(), results);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }
```
通过contextImpl和解析apk得到的ProviderInfo列表,进行安装。

hook点：只要解析出插件中的ContentProvider信息，再调用`installContentProviders()`就可以进行安装插件中的ConentProvider。


继续看安装过程，installProvider():
```java
private IActivityManager.ContentProviderHolder installProvider(Context context,
            IActivityManager.ContentProviderHolder holder, ProviderInfo info,
            boolean noisy, boolean noReleaseNeeded, boolean stable) {
            
        ContentProvider localProvider = null;
        IContentProvider provider;
        if (holder == null || holder.provider == null) {
            if (DEBUG_PROVIDER || noisy) {
                Slog.d(TAG, "Loading provider " + info.authority + ": "
                        + info.name);
            }
            Context c = null;
            ApplicationInfo ai = info.applicationInfo;
            if (context.getPackageName().equals(ai.packageName)) {
                c = context;
            } else if (mInitialApplication != null &&
                    mInitialApplication.getPackageName().equals(ai.packageName)) {
                c = mInitialApplication;
            } else {
                try {
                    c = context.createPackageContext(ai.packageName,
                            Context.CONTEXT_INCLUDE_CODE);
                } catch (PackageManager.NameNotFoundException e) {
                    // Ignore
                }
            }
            if (c == null) {
                Slog.w(TAG, "Unable to get context for package " +
                      ai.packageName +
                      " while loading content provider " +
                      info.name);
                return null;
            }
            try {
                // 创建ContentProvider对象，执行相应的生命周期
                final java.lang.ClassLoader cl = c.getClassLoader();
                localProvider = (ContentProvider)cl.
                    loadClass(info.name).newInstance();
                provider = localProvider.getIContentProvider();
                localProvider.attachInfo(c, info);
            } catch (java.lang.Exception e) {
                if (!mInstrumentation.onException(null, e)) {
                    throw new RuntimeException(
                            "Unable to get provider " + info.name
                            + ": " + e.toString(), e);
                }
                return null;
            }
        }

        IActivityManager.ContentProviderHolder retHolder;    
        synchronized (mProviderMap) {
            if (DEBUG_PROVIDER) Slog.v(TAG, "Checking to add " + provider
                    + " / " + info.name);
            IBinder jBinder = provider.asBinder();
            if (localProvider != null) {
                ComponentName cname = new ComponentName(info.packageName, info.name);
                ProviderClientRecord pr = mLocalProvidersByName.get(cname);
                if (pr != null) {
                    if (DEBUG_PROVIDER) {
                        Slog.v(TAG, "installProvider: lost the race, "
                                + "using existing local provider");
                    }
                    provider = pr.mProvider;
                } else {
                    holder = new IActivityManager.ContentProviderHolder(info);
                    holder.provider = provider;
                    holder.noReleaseNeeded = true;
                    // 在本地进程中，进行存储构建好的CotnentProvider和代理对象
                    pr = installProviderAuthoritiesLocked(provider, localProvider, holder);
                    mLocalProviders.put(jBinder, pr);
                    mLocalProvidersByName.put(cname, pr);
                }
                retHolder = pr.mHolder;
            }   
            
        } 
        
    return retHolder;
}
```
installProviderAuthoritiesLocked():
```java
  private ProviderClientRecord installProviderAuthoritiesLocked(IContentProvider provider,
            ContentProvider localProvider, IActivityManager.ContentProviderHolder holder) {
        final String auths[] = holder.info.authority.split(";");
        final int userId = UserHandle.getUserId(holder.info.applicationInfo.uid);

        final ProviderClientRecord pcr = new ProviderClientRecord(
                auths, provider, localProvider, holder);
        for (String auth : auths) {
            final ProviderKey key = new ProviderKey(auth, userId);
            final ProviderClientRecord existing = mProviderMap.get(key);
            if (existing != null) {
                Slog.w(TAG, "Content provider " + pcr.mHolder.info.name
                        + " already published as " + auth);
            } else {
               //存储好相应信息 
                mProviderMap.put(key, pcr);
            }
        }
        return pcr;
    }
```

**ContentProvider获取过程**：


在`ContextImpl#getContentResolver()`获取到ContentResolver对象。

[ContextImpl](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ContextImpl.java)
```java
 @Override
    public ContentResolver getContentResolver() {
        return mContentResolver;
    }
```
检索全局,发现是在ContextImpl的构造方法中创建ContentResolver对象：
```java
private ContextImpl(ContextImpl container, ActivityThread mainThread,
            LoadedApk packageInfo, IBinder activityToken, UserHandle user, int flags,
            Display display, Configuration overrideConfiguration, int createDisplayWithId) {
    //.... 省略部分源码
    mContentResolver = new ApplicationContentResolver(this, mainThread, user);
}
```
从上可知,应用程序是通过ApplicationContentResolver来与contentprovider进行交互操作的。


接下来 , 查看下ApplicationContentResolver这个静态内部类。

```java
 private static final class ApplicationContentResolver extends ContentResolver {
        private final ActivityThread mMainThread;
        private final UserHandle mUser;

        public ApplicationContentResolver(
                Context context, ActivityThread mainThread, UserHandle user) {
            super(context);
            mMainThread = Preconditions.checkNotNull(mainThread);
            mUser = Preconditions.checkNotNull(user);
        }

        @Override
        protected IContentProvider acquireProvider(Context context, String auth) {
            return mMainThread.acquireProvider(context,
                    ContentProvider.getAuthorityWithoutUserId(auth),
                    resolveUserIdFromAuthority(auth), true);
        }
        //... 省略部分源码
        @Override
        protected IContentProvider acquireUnstableProvider(Context c, String auth) {
            return mMainThread.acquireProvider(c,
                    ContentProvider.getAuthorityWithoutUserId(auth),
                    resolveUserIdFromAuthority(auth), false);
        }
       //... 省略部分源码
    }
```
继续,查看`ContentResolver#query()`是如何走向的：

[ContentResolver](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/content/ContentResolver.java)
```java
 public final @Nullable Cursor query(final @RequiresPermission.Read @NonNull Uri uri,
            @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder,
            @Nullable CancellationSignal cancellationSignal) {
        Preconditions.checkNotNull(uri, "uri");
        // 获取到ContentProvider的代理对象
        IContentProvider unstableProvider = acquireUnstableProvider(uri);
        if (unstableProvider == null) {
            return null;
        }
        //...    
}
```
继续查看acquireUnstableProvider():
```java
 public final IContentProvider acquireUnstableProvider(Uri uri) {
        if (!SCHEME_CONTENT.equals(uri.getScheme())) {
            return null;
        }
        String auth = uri.getAuthority();
        if (auth != null) {
            return acquireUnstableProvider(mContext, uri.getAuthority());
        }
        return null;
}
```
发现，会走到`ApplicationContentResolver#acquireUnstableProvider()`:
```java
        @Override
        protected IContentProvider acquireUnstableProvider(Context c, String auth) {
            return mMainThread.acquireProvider(c,
                    ContentProvider.getAuthorityWithoutUserId(auth),
                    resolveUserIdFromAuthority(auth), false);
        }
```
发现,会走到ActivityThread对象的acquireProvider():

[ActivityThread](https://www.androidos.net.cn/android/7.0.0_r31/xref/frameworks/base/core/java/android/app/ActivityThread.java)
```java
 public final IContentProvider acquireProvider(
            Context c, String auth, int userId, boolean stable) {
        // 从启动应用程序进程时，已经安装的ContentProvider中查看
        final IContentProvider provider = acquireExistingProvider(c, auth, userId, stable);
        if (provider != null) {
            return provider;
        }
        
        // 从AMS中去找其他应用程序安装的ContentProvider。
        IActivityManager.ContentProviderHolder holder = null;
        try {
            holder = ActivityManagerNative.getDefault().getContentProvider(
                    getApplicationThread(), auth, userId, stable);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
        if (holder == null) {
            Slog.e(TAG, "Failed to find provider info for " + auth);
            return null;
        }
        
        // 会去新安装ConntentProvider
        holder = installProvider(c, holder, holder.info,
                true /*noisy*/, holder.noReleaseNeeded, stable);
        return holder.provider;
    }
```

从上可知会先从应用程序自身的已经安装的ContentProvider中查找匹配，若是没有找到,则会从AactivityManagerService中去查找其他应用程序安装的ContentProvider进行匹配。若是没有，则会重新安装应用程序的ContentProvider中重新匹配。

从应用程序自身安装的ContentProvider中查找, acquireExistingProvider():
```java
 public final IContentProvider acquireExistingProvider(
            Context c, String auth, int userId, boolean stable) {
        synchronized (mProviderMap) {
            final ProviderKey key = new ProviderKey(auth, userId);
            final ProviderClientRecord pr = mProviderMap.get(key);
            if (pr == null) {
                return null;
            }

            IContentProvider provider = pr.mProvider;
            IBinder jBinder = provider.asBinder();
            // ContentProvider所在进程已经死亡
            if (!jBinder.isBinderAlive()) {
                // The hosting process of the provider has died; we can't
                // use this one.
                Log.i(TAG, "Acquiring provider " + auth + " for user " + userId
                        + ": existing object's process dead");
                handleUnstableProviderDiedLocked(jBinder, true);
                return null;
            }

            // Only increment the ref count if we have one.  If we don't then the
            // provider is not reference counted and never needs to be released.
            ProviderRefCount prc = mProviderRefCountMap.get(jBinder);
            if (prc != null) {
                // 会累计引用的个数
                incProviderRefLocked(prc, stable);
            }
            return provider;
        }
    }
```
从mProviderMap中获取缓存好的ContentProvider信息中查询,若是能匹配到，且能够使用则返回。反之，返回null。


### **实战案例**

**思考**：

1. 解析出插件中contentprovider
2. 在Application#attachBaseContext()中安装插件的contentprovidr。
3. 存在的一个问题，其他应用程序无法访问插件中ContentProvider。因PackageManagerService是无法获取到插件中信息。

在插件中编写一个contentprovoider
```java
public class PluginContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.xingen.plugin.contentprovider.PluginContentProvider";

    public static final Uri URI = Uri.parse("content://" + AUTHORITY);

    public static final String NAME = "name";

    private static final String TABLE_NAME = "developer";
    private PluginDB db;

    @Override
    public boolean onCreate() {
        db = new PluginDB(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase database = db.getReadableDatabase();
        return database.query(TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase database = db.getWritableDatabase();
        long rowId = database.insert(TABLE_NAME, null, values);
        Uri rowUri = ContentUris.appendId(URI.buildUpon(), rowId).build();
        return rowUri;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    private static class PluginDB extends SQLiteOpenHelper {
        private static final String db_name = "developer.db";
        private static final int db_version = 1;

        public PluginDB(Context context) {
            super(context, db_name, null, db_version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("Create table ");
            stringBuffer.append(TABLE_NAME);
            stringBuffer.append("( _id INTEGER PRIMARY KEY AUTOINCREMENT, ");
            stringBuffer.append(NAME);
            stringBuffer.append(" text ");
            stringBuffer.append(");");
            db.execSQL(stringBuffer.toString());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }
}

```
在AndroidManifest.xml中注册：
```java
        <provider
            android:name="com.xingen.plugin.contentprovider.PluginContentProvider"
            android:authorities="com.xingen.plugin.contentprovider.PluginContentProvider">

        </provider>
```

在宿主中编写hook,安装插件中的Contentprovider:

```java
public class ContentProviderHookManager {
    private static List<ProviderInfo> providerInfoList=new LinkedList<>();
    public static void init(Application  context, String apkFilePath){
         preloadParseContentProvider(apkFilePath);
         // 便于classloader加载，修改
        String packageName=context.getBaseContext().getPackageName();
        for (ProviderInfo providerInfo:providerInfoList){
             providerInfo.applicationInfo.packageName=packageName;
        }
        installContentProvider(context);
    }

    /**
     *  将ContentProvider安装到进程中
     */
    private static void installContentProvider(Context context){
        try {
            //获取到ActivityThread
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object ActivityThread = sCurrentActivityThreadField.get(null);
            // 调用 installContentProviders()
            Method installContentProvidersMethod=ActivityThreadClass.getDeclaredMethod("installContentProviders",Context.class,List.class);
            installContentProvidersMethod.setAccessible(true);
            installContentProvidersMethod.invoke(ActivityThread,context,providerInfoList);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    /**
     * 解析插件中的service
     *
     * @param apkFilePath
     */
    private static void preloadParseContentProvider(String apkFilePath) {
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
            Field providersField = packageParser$package_Class.getDeclaredField("providers");
            providersField.setAccessible(true);
            List providersList = (List) providersField.get(packageParser$package);
            Class<?> packageParser$Provider_Class = Class.forName("android.content.pm.PackageParser$Provider");
            // 获取 name
            Field infoField = packageParser$Provider_Class.getDeclaredField("info");
            infoField.setAccessible(true);
            for (Object provider : providersList) {
                ProviderInfo info = (ProviderInfo) infoField.get(provider);
                providerInfoList.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

在宿主的Application中加载：

```java
public class ProxyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        loadPluginDex(base);
    }

    private void loadPluginDex(Context context) {
        // 先拷贝assets 下的apk，写入磁盘中。
        String zipFilePath = PluginConfig.getZipFilePath(context);
        File zipFile = new File(zipFilePath);
        final String asset_file_name = "plugin.apk";
        Utils.copyFiles(context, asset_file_name, zipFile);
        String optimizedDirectory = new File(Utils.getCacheDir(context).getAbsolutePath() + File.separator + "plugin").getAbsolutePath();
        // 加载插件dex
        ClassLoaderHookManager.init(context, zipFilePath, optimizedDirectory);
        // 安装插件中的ContentProvider
         ContentProviderHookManager.init(this, zipFilePath);
    }
}
```
最后，测试：
```java
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
```
