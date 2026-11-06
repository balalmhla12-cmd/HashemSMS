package com.hashem.sms;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent screen for creating and restoring verified HashemSMS backups. */
public final class BackupActivity extends AppCompatActivity {
    private static final ExecutorService BACKUP_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BACKUP_OPERATION_RUNNING = new AtomicBoolean(false);

    private ActivityResultLauncher<String> backupCreateLauncher;
    private ActivityResultLauncher<String> csvCreateLauncher;
    private ActivityResultLauncher<String[]> backupOpenLauncher;
    private Button createButton;
    private Button csvButton;
    private Button restoreButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiResources.configureSystemBars(this);
        backupCreateLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"), this::exportBackupTo);
        backupOpenLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::confirmBackupRestore);
        csvCreateLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"), this::exportCsvTo);
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int horizontal = dp(16);
        root.setPadding(horizontal, dp(12), horizontal, dp(16));
        root.setBackgroundResource(R.drawable.bg_hashem_gradient);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription(getString(R.string.settings_title));
        back.setColorFilter(color(R.color.primary_variant));
        back.setBackground(background(color(R.color.surface_variant), 0, dp(24)));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(view -> finish());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, 0, 0);
        TextView title = text(getString(R.string.settings_backup), 21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = text(getString(R.string.app_name), 13);
        subtitle.setTextColor(color(R.color.on_surface_variant));
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(background(color(R.color.card_surface), color(R.color.card_stroke), dp(18)));

        TextView description = text(getString(R.string.backup_activity_description), 15);
        card.addView(description, new LinearLayout.LayoutParams(-1, -2));
        TextView safety = text(getString(R.string.backup_activity_safety_note), 13);
        safety.setTextColor(color(R.color.on_surface_variant));
        LinearLayout.LayoutParams safetyParams = new LinearLayout.LayoutParams(-1, -2);
        safetyParams.topMargin = dp(10);
        card.addView(safety, safetyParams);

        createButton = actionButton(getString(R.string.backup_create));
        csvButton = actionButton(getString(R.string.backup_export_csv));
        restoreButton = actionButton(getString(R.string.backup_restore));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(16);
        card.addView(createButton, actionParams);
        LinearLayout.LayoutParams csvParams = new LinearLayout.LayoutParams(-1, -2);
        csvParams.topMargin = dp(8);
        card.addView(csvButton, csvParams);
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, -2);
        restoreParams.topMargin = dp(8);
        card.addView(restoreButton, restoreParams);
        createButton.setOnClickListener(view -> {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
            backupCreateLauncher.launch("HashemSMS-backup-" + stamp + ".hsmbackup");
        });
        csvButton.setOnClickListener(view -> {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
            csvCreateLauncher.launch("HashemSMS-messages-" + stamp + ".csv");
        });
        restoreButton.setOnClickListener(view ->
                backupOpenLauncher.launch(new String[]{"application/zip", "application/octet-stream"}));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(18);
        root.addView(card, cardParams);
        setContentView(root);
    }

    private void exportBackupTo(Uri destination) {
        if (destination == null) return;
        runBackupOperation(R.string.backup_export_started, R.string.backup_export_done,
                () -> HashemBackupManager.exportBackup(getApplicationContext(), destination));
    }

    private void exportCsvTo(Uri destination) {
        if (destination == null) return;
        runBackupOperation(R.string.backup_csv_export_started, R.string.backup_csv_export_done,
                () -> MessageCsvExporter.exportMessages(getApplicationContext(), destination));
    }

    private void confirmBackupRestore(Uri source) {
        if (source == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_restore)
                .setMessage(R.string.backup_restore_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.backup_restore, (dialog, which) ->
                        runBackupOperation(R.string.backup_restore_started, R.string.backup_restore_done, () -> {
                            HashemBackupManager.restoreBackup(getApplicationContext(), source);
                            sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(getPackageName()));
                        }))
                .show();
    }

    private void runBackupOperation(int startedMessage, int successMessage, BackupTask task) {
        if (!BACKUP_OPERATION_RUNNING.compareAndSet(false, true)) {
            Toast.makeText(this, R.string.backup_operation_running, Toast.LENGTH_SHORT).show();
            return;
        }
        setActionsEnabled(false);
        Toast.makeText(this, startedMessage, Toast.LENGTH_SHORT).show();
        BACKUP_EXECUTOR.execute(() -> {
            try {
                task.run();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                android.util.Log.e("HashemSMS", "Backup or restore operation failed", error);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        String detail = error.getMessage() == null ? "" : error.getMessage();
                        Toast.makeText(this, getString(R.string.backup_failed) + " " + detail,
                                Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                BACKUP_OPERATION_RUNNING.set(false);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) setActionsEnabled(true);
                });
            }
        });
    }

    private void setActionsEnabled(boolean enabled) {
        if (createButton != null) createButton.setEnabled(enabled);
        if (csvButton != null) csvButton.setEnabled(enabled);
        if (restoreButton != null) restoreButton.setEnabled(enabled);
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(color(R.color.primary_variant));
        button.setBackground(background(color(R.color.primary_container), color(R.color.outline), dp(24)));
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(R.color.on_surface));
        view.setGravity(Gravity.CENTER_VERTICAL);
        try {
            view.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.almarai_regular));
        } catch (Exception error) {
            view.setTypeface(Typeface.DEFAULT);
        }
        return view;
    }

    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int color(int resource) {
        return UiResources.color(this, resource);
    }

    private int dp(float value) {
        return UiResources.dp(this, value);
    }

    private interface BackupTask {
        void run() throws Exception;
    }
}
