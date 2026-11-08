package com.techzone.cloner;
import android.app.Application;
import android.content.Context;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
public class ClonerApp extends Application {
@Override
protected void attachBaseContext(Context base) {
super.attachBaseContext(base);
try {
BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
@Override
public String getHostPackageName() { return base.getPackageName(); }
});
} catch (Exception e) { e.printStackTrace(); }
}
@Override
public void onCreate() {
super.onCreate();
try { BlackBoxCore.get().doCreate(); } catch (Exception e) { e.printStackTrace(); }
}
}
