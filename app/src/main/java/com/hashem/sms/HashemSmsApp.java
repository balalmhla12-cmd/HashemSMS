package com.hashem.sms;
import android.app.Application;
public final class HashemSmsApp extends Application {
 @Override public void onCreate(){super.onCreate();NotificationHelper.createChannel(this);}
}
