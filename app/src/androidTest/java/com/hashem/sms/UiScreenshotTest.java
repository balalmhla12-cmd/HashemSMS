package com.hashem.sms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class UiScreenshotTest {
    private static final String TEST_ADDRESS = "+15551230001";
    private final UiDevice device = UiDevice.getInstance(
            InstrumentationRegistry.getInstrumentation());

    @Test
    public void captureMainConversationComposerBulkAndSidebarInBothThemes() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.deleteDatabase("hashem.db");
        seedPreviewData(target);
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File output = new File(testContext.getFilesDir(), "HashemSMS-Screenshots");
        if (!output.exists() && !output.mkdirs()) {
            throw new AssertionError("Unable to create screenshot directory: " + output);
        }

        captureTheme(target, AppCompatDelegate.MODE_NIGHT_NO, "light", output);
        captureTheme(target, AppCompatDelegate.MODE_NIGHT_YES, "dark", output);
        String[] screenshots = output.list((dir, name) -> name.endsWith(".png"));
        assertNotNull("Screenshot directory could not be listed", screenshots);
        assertTrue("Expected ten screenshots, found " + screenshots.length, screenshots.length == 10);
    }

    private void seedPreviewData(Context context) {
        Db db = new Db(context);
        db.addMessage(TEST_ADDRESS, "رسالة واردة تجريبية لعرض شكل المحادثة", "in", "received");
        db.addMessage(TEST_ADDRESS, "رسالة صادرة تجريبية", "out", "sent");
        long groupId = db.createGroup("مجموعة العرض التجريبية");
        if (groupId > 0) db.addGroupMember(groupId, TEST_ADDRESS, "جهة الاختبار");
        long campaignId = db.createBulkCampaign("حملة العرض التجريبية",
                new ArrayList<>(Collections.singletonList(
                        new String[]{TEST_ADDRESS, "جهة الاختبار"})));
        long messageId = db.addBulkMessage(TEST_ADDRESS, "رسالة الحملة التجريبية",
                "تم الإرسال", campaignId);
        if (messageId <= 0) throw new AssertionError("Unable to seed campaign preview");
        db.updateBulkRecipientStatus(messageId, "تم الإرسال");
        db.close();
    }

    private void captureTheme(Context target, int mode, String theme, File output)
            throws Exception {
        target.getSharedPreferences(HashemUi.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(HashemUi.KEY_NIGHT, mode).commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> AppCompatDelegate.setDefaultNightMode(mode));
        SystemClock.sleep(700);

        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            waitForUi();
            device.wait(Until.hasObject(By.text("رسالة صادرة تجريبية")), 5000);
            save(output, "01-home-" + theme + ".png");

            UiObject2 menu = device.wait(Until.findObject(By.desc("القائمة")), 5000);
            assertNotNull("Main menu button not found", menu);
            menu.click();
            UiObject2 more = device.wait(Until.findObject(By.text("المزيد")), 3000);
            assertNotNull("More menu item not found", more);
            more.click();
            waitForUi();
            save(output, "05-sidebar-" + theme + ".png");

            device.pressBack();
            waitForUi();
            menu = device.wait(Until.findObject(By.desc("القائمة")), 5000);
            assertNotNull("Main menu button not found after returning", menu);
            menu.click();
            UiObject2 bulk = device.wait(Until.findObject(By.text("الرسائل الجماعية")), 3000);
            assertNotNull("Bulk messages menu item not found", bulk);
            bulk.click();
            waitForUi();
            assertNotNull("Bulk screen title not visible",
                    device.wait(Until.findObject(By.text("إنشاء رسالة جماعية")), 3000));
            save(output, "04-bulk-" + theme + ".png");
        }

        Intent conversation = new Intent(target, ConversationActivity.class);
        conversation.putExtra("address", TEST_ADDRESS);
        try (ActivityScenario<ConversationActivity> screen =
                     ActivityScenario.launch(conversation)) {
            waitForUi();
            save(output, "02-conversation-" + theme + ".png");

            UiObject2 composer = device.wait(
                    Until.findObject(By.clazz("android.widget.EditText")), 5000);
            assertNotNull("Conversation composer not found", composer);
            composer.click();
            composer.setText("نص تجريبي في شريط الكتابة");
            waitForUi();
            save(output, "03-composer-" + theme + ".png");
        }
    }

    private void waitForUi() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        device.waitForIdle();
        SystemClock.sleep(500);
    }

    private void save(File output, String name) {
        File file = new File(output, name);
        assertTrue("Screenshot could not be saved: " + file,
                device.takeScreenshot(file));
        assertTrue("Screenshot is missing or empty: " + file,
                file.exists() && file.length() > 10_000);
    }
}
