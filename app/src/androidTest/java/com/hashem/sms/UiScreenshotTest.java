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

    private static void seedPreviewContacts(Context target) throws Exception {
        android.app.UiAutomation automation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity("android.permission.WRITE_CONTACTS",
                "android.permission.READ_CONTACTS");
        try {
            String[][] people = {{"أحمد محمد", "777123456"}, {"محمد عبدالله", "733456789"},
                    {"علي حسن", "711222333"}, {"خالد أحمد", "770555222"}};
            ArrayList<android.content.ContentProviderOperation> ops = new ArrayList<>();
            for (String[] person : people) {
                int raw = ops.size();
                ops.add(android.content.ContentProviderOperation.newInsert(
                        android.provider.ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build());
                ops.add(android.content.ContentProviderOperation.newInsert(
                        android.provider.ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(
                                android.provider.ContactsContract.Data.RAW_CONTACT_ID, raw)
                        .withValue(android.provider.ContactsContract.Data.MIMETYPE,
                                android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                                person[0])
                        .build());
                ops.add(android.content.ContentProviderOperation.newInsert(
                        android.provider.ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(
                                android.provider.ContactsContract.Data.RAW_CONTACT_ID, raw)
                        .withValue(android.provider.ContactsContract.Data.MIMETYPE,
                                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                                person[1])
                        .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE,
                                android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build());
            }
            target.getContentResolver().applyBatch(android.provider.ContactsContract.AUTHORITY, ops);
        } finally {
            automation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void captureMainConversationComposerBulkAndSidebarInBothThemes() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.deleteDatabase("hashem.db");
        seedPreviewData(target);
        seedPreviewContacts(target);
        File output = new File(target.getFilesDir(), "HashemSMS-Screenshots");
        if (!output.exists() && !output.mkdirs()) {
            throw new AssertionError("Unable to create screenshot directory: " + output);
        }

        captureTheme(target, AppCompatDelegate.MODE_NIGHT_NO, "light", output);
        captureTheme(target, AppCompatDelegate.MODE_NIGHT_YES, "dark", output);
        String[] screenshots = output.list((dir, name) -> name.endsWith(".png"));
        assertNotNull("Screenshot directory could not be listed", screenshots);
        assertTrue("Expected sixteen screenshots, found " + screenshots.length, screenshots.length == 16);
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
        seedSendingCampaign(db);
        db.close();
    }

    /** Seeds a partly finished campaign and marks it as running, without sending anything. */
    private void seedSendingCampaign(Db db) {
        String[][] people = {{"777123456", "أحمد محمد"}, {"733456789", "محمد عبدالله"},
                {"711222333", "علي حسن"}, {"770555222", "خالد أحمد"},
                {"777888999", "عبدالله صالح"}, {"733111222", "إبراهيم علي"},
                {"770111000", "سالم أحمد"}, {"711333444", "حسن محمد"}};
        ArrayList<String[]> recipients = new ArrayList<>();
        for (String[] person : people) recipients.add(person);
        long campaignId = db.createBulkCampaign("حملة التوعية", recipients);
        String[] statuses = {"تم التسليم", "تم التسليم", "لم يتم الإرسال", "تم التسليم", "تم الإرسال"};
        for (int i = 0; i < statuses.length; i++) {
            long id = db.addBulkMessage(people[i][0], "رسالة حملة التوعية", statuses[i], campaignId);
            if (id <= 0) throw new AssertionError("Unable to seed sending campaign");
            db.updateBulkRecipientStatus(id, statuses[i]);
        }
        try {
            java.lang.reflect.Field field =
                    Class.forName("com.hashem.sms.SmsSending").getDeclaredField("ACTIVE_BULK_IDS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<Long> active = (java.util.Set<Long>) field.get(null);
            active.add(campaignId);
        } catch (Exception e) {
            throw new AssertionError("Unable to mark seeded campaign as running", e);
        }
    }

    private void captureTheme(Context target, int mode, String theme, File output)
            throws Exception {
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
            waitForUi();
            save(output, "05-sidebar-" + theme + ".png");

            device.pressBack();
            waitForUi();
            UiObject2 bulk = device.wait(Until.findObject(By.textContains("جماعية")), 5000);
            assertNotNull("Bulk filter tab not found", bulk);
            bulk.click();
            waitForUi();
            assertNotNull("Bulk screen title not visible",
                    device.wait(Until.findObject(By.text("إنشاء رسالة جماعية")), 3000));
            save(output, "04-bulk-" + theme + ".png");

            UiObject2 details = device.wait(Until.findObject(By.text("التفاصيل")), 5000);
            assertNotNull("Active campaign details link not found", details);
            details.click();
            assertNotNull("Sending details not visible",
                    device.wait(Until.findObject(By.text("تفاصيل الإرسال")), 5000));
            waitForUi();
            save(output, "08-bulk-track-" + theme + ".png");
            UiObject2 trackBack = device.wait(Until.findObject(By.desc("رجوع")), 3000);
            assertNotNull("Back arrow not found on sending details", trackBack);
            trackBack.click();
            assertNotNull("Bulk home not restored after details",
                    device.wait(Until.findObject(By.text("إنشاء رسالة جماعية")), 5000));

            UiObject2 createCard = device.wait(Until.findObject(By.text("إنشاء رسالة جماعية")), 3000);
            assertNotNull("Create bulk card not found", createCard);
            createCard.click();
            assertNotNull("Bulk compose screen not visible",
                    device.wait(Until.findObject(By.textStartsWith("اختيار من جهات الاتصال")), 5000));
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3000);
            java.util.List<UiObject2> fields = device.findObjects(By.clazz("android.widget.EditText"));
            assertTrue("Compose fields not found", fields.size() >= 2);
            // fields[0] is the main search box in the header; fields[1] is the campaign name.
            fields.get(1).setText("حملة اختبار");
            UiObject2 pickButton = device.wait(
                    Until.findObject(By.textStartsWith("اختيار من جهات الاتصال")), 3000);
            assertNotNull("Contact picker button not found", pickButton);
            pickButton.click();
            assertNotNull("Recipient picker not visible",
                    device.wait(Until.findObject(By.text("اختيار المستلمين")), 5000));
            java.util.List<UiObject2> boxes = device.wait(
                    Until.findObjects(By.clazz("android.widget.CheckBox")), 3000);
            assertNotNull("Picker checkboxes not found", boxes);
            if (boxes.size() > 1) boxes.get(1).click();
            waitForUi();
            save(output, "06-bulk-picker-" + theme + ".png");
            UiObject2 next = device.wait(Until.findObject(By.text("التالي")), 3000);
            assertNotNull("Next button not found", next);
            next.click();
            assertNotNull("Review screen not visible",
                    device.wait(Until.findObject(By.text("مراجعة المستلمين")), 5000));
            assertNotNull("Campaign name was lost on review",
                    device.wait(Until.findObject(By.text("حملة اختبار")), 3000));
            waitForUi();
            save(output, "07-bulk-review-" + theme + ".png");
            UiObject2 reviewNext = device.wait(Until.findObject(By.text("التالي")), 3000);
            assertNotNull("Review next button not found", reviewNext);
            reviewNext.click();
            assertNotNull("Compose screen not restored after review",
                    device.wait(Until.findObject(By.textStartsWith("اختيار من جهات الاتصال")), 5000));
            assertNotNull("Campaign name was lost after review",
                    device.wait(Until.findObject(By.text("حملة اختبار")), 3000));
            assertNotNull("Campaign name was lost after picker",
                    device.wait(Until.findObject(By.text("حملة اختبار")), 3000));
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
