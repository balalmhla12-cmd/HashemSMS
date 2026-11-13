package com.hashem.sms;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;

/** Small visual adjustments requested for the home screen; messaging behavior remains in MainActivity. */
final class HashemUi implements Application.ActivityLifecycleCallbacks {
    private int startedActivityCount;
    private static final String PREFS = "hashem_ui";
    private static final String KEY_NIGHT = "night_mode";

    HashemUi(Context context) {
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    @Override public void onActivityStarted(Activity activity) {
        startedActivityCount++;
        AppLockController.enforce(activity);
        if (!(activity instanceof MainActivity)) return;
        MainActivity main = (MainActivity) activity;
        if (main.root == null || "hashem_ui".equals(main.root.getTag())) return;
        main.root.setTag("hashem_ui");
        try {
            new Overlay(main).install();
        } catch (Throwable error) {
            android.util.Log.w("HashemUi", "overlay failed; original UI kept", error);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {
        if (startedActivityCount > 0) startedActivityCount--;
        // A configuration recreation is not the user leaving the app.
        // The lock screen is part of the foreground app flow. Stopping it after a
        // successful authentication must not be mistaken for the user leaving the app;
        // otherwise the immediate-lock setting would relock before the underlying
        // activity is resumed.
        if (startedActivityCount == 0 && !activity.isChangingConfigurations()
                && !(activity instanceof AppLockActivity)) {
            AppLockController.lockForBackground(activity);
        }
    }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}

    static final class Overlay {
        private final MainActivity main;

        Overlay(MainActivity main) {
            this.main = main;
        }

        private int dp(float value) {
            return UiResources.dp(main, value);
        }

        private void safe(Runnable action) {
            try {
                action.run();
            } catch (Throwable error) {
                android.util.Log.w("HashemUi", "visual adjustment skipped", error);
            }
        }

        void install() {
            safe(this::header);
            safe(this::compose);
            safe(this::watchThreadRows);
        }

        /** Add the requested slightly larger app mark without rebuilding the header actions. */
        private void header() {
            if (!(main.root.getChildAt(0) instanceof LinearLayout)) return;
            LinearLayout bar = (LinearLayout) main.root.getChildAt(0);
            if (bar.getChildCount() < 2) return;

            ImageView logo = new ImageView(main);
            logo.setImageResource(R.drawable.ic_launcher_foreground);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams logoParams =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            logoParams.setMarginEnd(dp(8));
            bar.addView(logo, 0, logoParams);
            bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

            View brandView = bar.getChildAt(1);
            if (brandView instanceof LinearLayout) {
                LinearLayout brand = (LinearLayout) brandView;
                brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                for (int i = 0; i < brand.getChildCount(); i++) {
                    View child = brand.getChildAt(i);
                    if (child instanceof TextView) {
                        child.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    }
                }
            }
            View actionsView = bar.getChildAt(2);
            if (actionsView instanceof LinearLayout) {
                addThemeToggle((LinearLayout) actionsView);
            }
        }

        private void addThemeToggle(LinearLayout actions) {
            if (actions.findViewWithTag("hashem_theme_toggle") != null) return;
            LinearLayout toggle = new LinearLayout(main);
            toggle.setTag("hashem_theme_toggle");
            toggle.setOrientation(LinearLayout.HORIZONTAL);
            toggle.setGravity(Gravity.CENTER);
            toggle.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            toggle.setPadding(dp(3), 0, dp(3), 0);
            android.graphics.drawable.GradientDrawable track =
                    new android.graphics.drawable.GradientDrawable();
            track.setColor(UiResources.color(main, R.color.home_toggle_background));
            track.setCornerRadius(dp(18));
            toggle.setBackground(track);

            TextView sun = themeIcon("☀", "الوضع العادي");
            TextView moon = themeIcon("☾", "الوضع الداكن");
            toggle.addView(sun, new LinearLayout.LayoutParams(dp(30), dp(30)));
            toggle.addView(moon, new LinearLayout.LayoutParams(dp(30), dp(30)));

            Runnable refresh = () -> {
                boolean night = (main.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                styleThemeIcon(sun, !night);
                styleThemeIcon(moon, night);
            };
            sun.setOnClickListener(v -> setNightMode(false));
            moon.setOnClickListener(v -> setNightMode(true));
            toggle.setContentDescription("التبديل بين الوضع العادي والداكن");
            actions.addView(toggle, 0, new LinearLayout.LayoutParams(dp(66), dp(36)));
            refresh.run();
        }

        private TextView themeIcon(String glyph, String description) {
            TextView icon = new TextView(main);
            icon.setText(glyph);
            icon.setTextSize(16);
            icon.setGravity(Gravity.CENTER);
            icon.setContentDescription(description);
            return icon;
        }

        private void styleThemeIcon(TextView icon, boolean selected) {
            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();
            background.setColor(selected ? UiResources.color(main, R.color.home_toggle_active)
                    : android.graphics.Color.TRANSPARENT);
            background.setCornerRadius(dp(15));
            icon.setBackground(background);
            icon.setTextColor(UiResources.color(main, selected
                    ? R.color.home_header : R.color.home_toggle_inactive));
        }

        private void setNightMode(boolean night) {
            int mode = night ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            main.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_NIGHT, mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        }

        /** Move the existing compose button to the requested bottom-left position without restyling it. */
        private void compose() {
            Button button = main.composeButton;
            if (button == null || main.contentHost == null) return;

            ViewGroup parent = (ViewGroup) button.getParent();
            if (parent != null) parent.removeView(button);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    -2, -2, Gravity.BOTTOM | Gravity.START);
            params.setMargins(dp(16), 0, dp(16), dp(16));
            main.contentHost.addView(button, params);

            if (main.threadsRecycler != null) {
                main.threadsRecycler.setClipToPadding(false);
                main.threadsRecycler.setPadding(0, 0, 0, dp(88));
            }
        }

        /** Style each row only when RecyclerView attaches it, never on every pre-draw frame. */
        private void watchThreadRows() {
            RecyclerView recycler = main.threadsRecycler;
            if (recycler == null || main.threadAdapter == null) return;

            recycler.addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override public void onChildViewAttachedToWindow(View view) {
                            styleAttachedRow(recycler, view);
                        }

                        @Override public void onChildViewDetachedFromWindow(View view) {}
                    });

            for (int i = 0; i < recycler.getChildCount(); i++) {
                styleAttachedRow(recycler, recycler.getChildAt(i));
            }
        }

        private void styleAttachedRow(RecyclerView recycler, View view) {
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(view);
            if (holder instanceof MainActivity.ThreadViewHolder) {
                styleRow((MainActivity.ThreadViewHolder) holder);
            }
        }

        /** Apply only the requested row width, trailing-gap and text-size adjustments. */
        private void styleRow(MainActivity.ThreadViewHolder holder) {
            if ("hashem_row_styled".equals(holder.container.getTag())) return;
            holder.container.setTag("hashem_row_styled");

            ViewGroup.LayoutParams params = holder.container.getLayoutParams();
            if (params instanceof RecyclerView.LayoutParams) {
                RecyclerView.LayoutParams rowParams = (RecyclerView.LayoutParams) params;
                rowParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                rowParams.setMargins(0, 0, 0, 0);
                holder.container.setLayoutParams(rowParams);
            }

            holder.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            holder.body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            holder.row.setPadding(dp(2), dp(6), dp(2), dp(6));

            if (holder.avatar.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams avatarParams =
                        (LinearLayout.LayoutParams) holder.avatar.getLayoutParams();
                avatarParams.setMargins(0, 0, dp(4), 0);
                holder.avatar.setLayoutParams(avatarParams);
            }
        }
    }
}
