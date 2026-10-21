package com.hashem.sms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingsActivity extends AppCompatActivity {
    private LinearLayout content;
    private EditText search;
    private TextView empty, defaultDetail, permissionsDetail, simDetail;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<TextView> sectionTitles = new ArrayList<>();
    private static final String PREFS = "hashem_ui";
    private static final String NIGHT_KEY = "night_mode";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiResources.configureSystemBars(this);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (defaultDetail != null) defaultDetail.setText(defaultStatus());
        if (permissionsDetail != null) permissionsDetail.setText(permissionStatus());
        if (simDetail != null) simDetail.setText(simStatus());
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.setBackgroundResource(R.drawable.bg_hashem_gradient);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription(getString(R.string.settings_title));
        back.setColorFilter(col(R.color.primary_variant));
        back.setBackground(round(col(R.color.surface_variant), 0, dp(24)));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> finish());
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(10), 0, 0, 0);
        TextView title = txt(getString(R.string.settings_title), 22);
        title.setTypeface(bold());
        TextView subtitle = txt(getString(R.string.app_name), 13);
        subtitle.setTextColor(col(R.color.on_surface_variant));
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(R.string.settings_search_hint);
        search.setTextSize(15);
        search.setTextColor(col(R.color.on_surface));
        search.setHintTextColor(col(R.color.on_surface_variant));
        search.setPaddingRelative(dp(16), 0, dp(16), 0);
        search.setBackground(round(col(R.color.surface_variant), col(R.color.search_border), dp(26)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.topMargin = dp(12);
        searchLp.bottomMargin = dp(8);
        root.addView(search, searchLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = txt(getString(R.string.settings_search_empty), 15);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        content.addView(empty, new LinearLayout.LayoutParams(-1, dp(90)));

        buildStatus();
        section(R.string.settings_customization, "التخصيص المظهر الإشعارات الجدولة");
        buildAppearance();
        row(R.string.settings_notifications, R.string.settings_notifications_desc, R.drawable.ic_settings,
                "الإشعارات التنبيهات", this::openNotificationSettings);
        row(R.string.settings_scheduling, R.string.settings_schedule_desc, R.drawable.ic_schedule,
                "الجدولة المنبهات", this::openScheduleSettings);
        section(R.string.settings_privacy, "الخصوصية الصلاحيات الوصول");
        row(R.string.settings_privacy, R.string.settings_privacy_desc, R.drawable.ic_block,
                "الخصوصية الصلاحيات الأذونات", this::openAppSettings);
        buildAdvanced();
        row(R.string.settings_about, R.string.app_name, R.drawable.ic_nav_messages,
                "حول التطبيق الإصدار معلومات", this::about);

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        setContentView(root);
        filter("");
    }

    private void buildStatus() {
        section(R.string.settings_status_title, "الحالة التطبيق الصلاحيات الافتراضي الشرائح");
        LinearLayout card = card();
        addStatus(card, R.drawable.ic_nav_messages, R.string.settings_default_sms, defaultStatus(),
                "الرسائل الافتراضي", this::requestDefaultRole);
        divider(card);
        addStatus(card, R.drawable.ic_block, R.string.settings_permissions, permissionStatus(),
                "الصلاحيات الأذونات", this::openAppSettings);
        divider(card);
        addStatus(card, R.drawable.ic_settings, R.string.settings_sim, simStatus(),
                "الشريحة شرائح الهاتف sim", this::showSims);
        content.addView(card, margin(10));
        addFilter(card, "حالة التطبيق الصلاحيات الرسائل الافتراضي شرائح sim الهاتف");
    }

    private void buildAppearance() {
        section(R.string.settings_appearance, "المظهر فاتح داكن النظام معاينة");
        LinearLayout card = card();
        TextView heading = txt(getString(R.string.settings_preview_title), 16);
        heading.setTypeface(bold());
        card.addView(heading, margin(8));
        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER);
        options.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        themeOption(options, R.string.settings_system_default, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeOption(options, R.string.settings_light, AppCompatDelegate.MODE_NIGHT_NO);
        themeOption(options, R.string.settings_dark, AppCompatDelegate.MODE_NIGHT_YES);
        card.addView(options, margin(12));
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(12), dp(10), dp(12), dp(10));
        preview.setBackground(round(col(R.color.surface_variant), col(R.color.card_stroke), dp(16)));
        TextView incoming = txt(getString(R.string.settings_preview_incoming), 14);
        incoming.setPadding(dp(12), dp(7), dp(12), dp(7));
        incoming.setBackground(round(col(R.color.bubble_in), 0, dp(18)));
        TextView outgoing = txt(getString(R.string.settings_preview_outgoing), 14);
        outgoing.setTextColor(col(R.color.on_bubble_out));
        outgoing.setPadding(dp(12), dp(7), dp(12), dp(7));
        outgoing.setBackground(round(col(R.color.bubble_out), 0, dp(18)));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(-2, -2);
        inLp.gravity = Gravity.START;
        inLp.bottomMargin = dp(8);
        preview.addView(incoming, inLp);
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(-2, -2);
        outLp.gravity = Gravity.END;
        preview.addView(outgoing, outLp);
        card.addView(preview);
        content.addView(card, margin(10));
        addFilter(card, "المظهر معاينة الألوان فاتح داكن حسب النظام");
    }

    private void themeOption(LinearLayout parent, int label, int mode) {
        TextView option = txt(getString(label), 13);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(6), dp(9), dp(6), dp(9));
        option.setTypeface(bold());
        styleThemeOption(option, mode);
        option.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(NIGHT_KEY, mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
            Toast.makeText(this, R.string.settings_theme_applied, Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(option, lp);
    }

    private void styleThemeOption(TextView option, int mode) {
        int current = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(NIGHT_KEY, AppCompatDelegate.MODE_NIGHT_YES);
        boolean selected = current == mode;
        option.setTextColor(col(selected ? R.color.on_primary : R.color.on_surface));
        option.setBackground(round(col(selected ? R.color.primary_variant : R.color.card_surface),
                col(R.color.card_stroke), dp(18)));
    }

    private void section(int titleRes, String terms) {
        TextView heading = txt(getString(titleRes), 17);
        heading.setTypeface(bold());
        heading.setPadding(dp(2), dp(10), dp(2), dp(5));
        content.addView(heading, margin(2));
        sectionTitles.add(heading);
        addFilter(heading, terms + " " + getString(titleRes));
    }

    private void row(int titleRes, int descriptionRes, int iconRes, String terms, Runnable action) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(col(R.color.primary_variant));
        card.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView title = txt(getString(titleRes), 15);
        title.setTypeface(bold());
        TextView desc = txt(getString(descriptionRes), 12);
        desc.setTextColor(col(R.color.on_surface_variant));
        desc.setMaxLines(2);
        labels.addView(title);
        labels.addView(desc);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_back);
        arrow.setColorFilter(col(R.color.on_surface_variant));
        card.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> action.run());
        content.addView(card, margin(8));
        addFilter(card, terms + " " + getString(titleRes) + " " + getString(descriptionRes));
    }

    private void buildAdvanced() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = card();
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_settings);
        icon.setColorFilter(col(R.color.icon_gold));
        head.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView title = txt(getString(R.string.settings_advanced), 15);
        title.setTypeface(bold());
        title.setPadding(dp(12), 0, dp(8), 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_back);
        arrow.setColorFilter(col(R.color.on_surface_variant));
        head.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
        wrapper.addView(head);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setVisibility(View.GONE);
        addExpandedOption(options, R.string.settings_open_system, this::openAppSettings);
        addExpandedOption(options, R.string.settings_notifications, this::openNotificationSettings);
        addExpandedOption(options, R.string.settings_scheduling, this::openScheduleSettings);
        wrapper.addView(options);
        head.setClickable(true);
        head.setFocusable(true);
        head.setOnClickListener(v -> {
            boolean visible = options.getVisibility() != View.VISIBLE;
            options.setVisibility(visible ? View.VISIBLE : View.GONE);
            arrow.setRotation(visible ? -90f : 0f);
        });
        content.addView(wrapper, margin(8));
        addFilter(wrapper, "الإعدادات المتقدمة النظام التطبيق advanced");
    }

    private void addExpandedOption(LinearLayout parent, int label, Runnable action) {
        TextView option = txt(getString(label), 14);
        option.setPadding(dp(18), dp(10), dp(18), dp(10));
        option.setOnClickListener(v -> action.run());
        parent.addView(option);
    }

    private void addStatus(LinearLayout parent, int iconRes, int titleRes, String summary, String terms, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(col(R.color.primary_variant));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), dp(4), dp(8), dp(4));
        TextView title = txt(getString(titleRes), 14);
        title.setTypeface(bold());
        TextView detail = txt(summary, 12);
        detail.setTextColor(col(R.color.on_surface_variant));
        detail.setMaxLines(2);
        labels.addView(title);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_back);
        arrow.setColorFilter(col(R.color.on_surface_variant));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addFilter(row, terms + " " + getString(titleRes) + " " + summary);
        if (titleRes == R.string.settings_default_sms) defaultDetail = detail;
        if (titleRes == R.string.settings_permissions) permissionsDetail = detail;
        if (titleRes == R.string.settings_sim) simDetail = detail;
    }

    private String defaultStatus() {
        return getPackageName().equals(android.provider.Telephony.Sms.getDefaultSmsPackage(this))
                ? getString(R.string.settings_status_default_yes) : getString(R.string.settings_status_default_no);
    }

    private String permissionStatus() {
        String[] permissions = {"android.permission.READ_SMS", "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS", "android.permission.READ_CONTACTS"};
        int granted = 0;
        for (String permission : permissions) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) granted++;
        }
        int total = permissions.length;
        if (Build.VERSION.SDK_INT >= 33) {
            total++;
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) granted++;
        }
        return getString(R.string.settings_status_permissions_format, granted, total);
    }

    private String simStatus() {
        if (checkSelfPermission("android.permission.READ_PHONE_STATE") != PackageManager.PERMISSION_GRANTED)
            return getString(R.string.settings_status_sim_permission);
        try {
            SubscriptionManager manager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> active = manager == null ? null : manager.getActiveSubscriptionInfoList();
            int count = active == null ? 0 : active.size();
            return count == 0 ? getString(R.string.settings_status_sim_none)
                    : getString(R.string.settings_status_sim_count, count);
        } catch (RuntimeException e) {
            return getString(R.string.settings_sim_error);
        }
    }

    private void showSims() {
        StringBuilder message = new StringBuilder(getString(R.string.settings_sim_note)).append("\n\n");
        if (checkSelfPermission("android.permission.READ_PHONE_STATE") != PackageManager.PERMISSION_GRANTED) {
            message.append(getString(R.string.settings_status_sim_permission));
        } else {
            try {
                SubscriptionManager manager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                List<SubscriptionInfo> active = manager == null ? null : manager.getActiveSubscriptionInfoList();
                if (active == null || active.isEmpty()) {
                    message.append(getString(R.string.settings_status_sim_none));
                } else {
                    for (SubscriptionInfo info : active) {
                        CharSequence name = info.getDisplayName();
                        CharSequence carrier = info.getCarrierName();
                        message.append(getString(R.string.settings_sim)).append(" ")
                                .append(info.getSimSlotIndex() + 1).append(": ")
                                .append(name == null || name.length() == 0 ? carrier : name).append("\n");
                    }
                }
            } catch (RuntimeException e) {
                message.append(getString(R.string.settings_sim_error));
            }
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_manage_sim).setMessage(message.toString())
                .setPositiveButton(R.string.settings_open_system, (d, w) -> openAppSettings())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void requestDefaultRole() {
        if (getPackageName().equals(android.provider.Telephony.Sms.getDefaultSmsPackage(this))) {
            Toast.makeText(this, R.string.settings_status_default_yes, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager manager =
                        (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (manager != null && manager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                    startActivityForResult(manager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS), 702);
                    return;
                }
            }
            Intent intent = new Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_role_request_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(intent); } catch (Exception e) { openAppSettings(); }
    }

    private void openScheduleSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {}
        }
        openAppSettings();
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_role_request_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void about() {
        String version = "—";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName == null ? "—" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}
        String message = getString(R.string.settings_about_version, version) + "\n"
                + getString(R.string.settings_about_package, getPackageName());
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.settings_about)
                .setMessage(message).setPositiveButton(android.R.string.ok, null).show();
    }

    private void filter(String raw) {
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        boolean any = false;
        for (Entry entry : entries) {
            boolean show = query.isEmpty() || entry.terms.contains(query);
            entry.view.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) any = true;
        }
        for (int i = 0; i < sectionTitles.size(); i++) {
            TextView title = sectionTitles.get(i);
            int start = content.indexOfChild(title) + 1;
            int end = i + 1 < sectionTitles.size() ? content.indexOfChild(sectionTitles.get(i + 1)) : content.getChildCount();
            boolean childVisible = false;
            for (int j = start; j < end; j++) {
                View child = content.getChildAt(j);
                if (child.getVisibility() == View.VISIBLE) { childVisible = true; break; }
            }
            if (!query.isEmpty()) title.setVisibility(childVisible || title.getText().toString().toLowerCase(Locale.ROOT).contains(query) ? View.VISIBLE : View.GONE);
        }
        empty.setVisibility(any ? View.GONE : View.VISIBLE);
    }

    private void addFilter(View view, String terms) {
        entries.add(new Entry(view, terms.toLowerCase(Locale.ROOT)));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(col(R.color.card_surface), col(R.color.card_stroke), dp(18)));
        return card;
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(col(R.color.divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        parent.addView(line, lp);
    }

    private LinearLayout.LayoutParams margin(int value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(value);
        return lp;
    }

    private TextView txt(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(col(R.color.on_surface));
        view.setGravity(Gravity.CENTER_VERTICAL);
        try { view.setTypeface(ResourcesCompat.getFont(this, R.font.almarai_regular)); }
        catch (Exception e) { view.setTypeface(Typeface.DEFAULT); }
        return view;
    }

    private Typeface bold() {
        try { return ResourcesCompat.getFont(this, R.font.almarai_bold); }
        catch (Exception e) { return Typeface.create(Typeface.DEFAULT, Typeface.BOLD); }
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private int col(int resource) { return UiResources.color(this, resource); }
    private int dp(float value) { return UiResources.dp(this, value); }

    private static final class Entry {
        final View view;
        final String terms;
        Entry(View view, String terms) { this.view = view; this.terms = terms; }
    }
}
