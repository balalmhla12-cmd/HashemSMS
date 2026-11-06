package com.hashem.sms;
import android.app.Application;
import android.content.ComponentCallbacks2;
public final class HashemSmsApp extends Application {
 @Override public void onCreate(){super.onCreate();
  registerActivityLifecycleCallbacks(new HashemUi(this));NotificationHelper.createChannel(this);}
 @Override public void onTrimMemory(int level){
  super.onTrimMemory(level);
  // Only UI-hidden/background trim signals mean the user left the app.
  // Low-memory callbacks can arrive while the app is still visible.
  if(level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN){
   AppLockController.lockForBackground(this);
  }
 }
}
