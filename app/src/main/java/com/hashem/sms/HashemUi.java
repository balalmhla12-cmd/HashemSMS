package com.hashem.sms;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

/** Visual overlay for MainActivity. Touches no messaging logic. On any failure the original UI stays. */
final class HashemUi implements Application.ActivityLifecycleCallbacks {
    static final String PREFS = "hashem_ui";
    static final String KEY_NIGHT = "night_mode";

    HashemUi(Context context) {
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    @Override public void onActivityStarted(Activity a) {
        if (!(a instanceof MainActivity)) return;
        MainActivity m = (MainActivity) a;
        if (m.root == null || "hashem_ui".equals(m.root.getTag())) return;
        m.root.setTag("hashem_ui");
        try { new Overlay(m).install(); }
        catch (Throwable t) { android.util.Log.w("HashemUi", "overlay failed; original UI kept", t); }
    }
    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}

    static final class Overlay {
        final MainActivity m;
        final String[] filters = {"all", "unread", "failed", "bulk", "unknown", "scheduled"};
        final LinearLayout[] chips = new LinearLayout[6];
        final TextView[] chipTexts = new TextView[6];
        final ImageView[] chipIcons = new ImageView[6];
        Typeface bold;
        String lastKey = "";
        int errors = 0;
        ViewTreeObserver.OnPreDrawListener listener;

        Overlay(MainActivity m) { this.m = m; }

        int dp(float v) { return UiResources.dp(m, v); }
        int col(int res) { return UiResources.color(m, res); }

        GradientDrawable round(int fill, int stroke, int radiusPx, int strokePx) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(fill);
            d.setCornerRadius(radiusPx);
            if (stroke != 0) d.setStroke(strokePx, stroke);
            return d;
        }

        GradientDrawable oval(int fill, int stroke, int strokePx) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(fill);
            if (stroke != 0) d.setStroke(strokePx, stroke);
            return d;
        }

        void install() {
            try { bold = Typeface.create(ResourcesCompat.getFont(m, R.font.almarai_family), Typeface.BOLD); } catch (Throwable ignored) {}
            safe(this::header);
            safe(this::buildChips);
            safe(this::compose);
            listener = () -> {
                try { tick(); }
                catch (Throwable t) {
                    android.util.Log.w("HashemUi", "tick failed", t);
                    if (++errors > 5 && listener != null) m.root.getViewTreeObserver().removeOnPreDrawListener(listener);
                }
                return true;
            };
            m.root.getViewTreeObserver().addOnPreDrawListener(listener);
        }

        void safe(Runnable r) {
            try { r.run(); } catch (Throwable t) { android.util.Log.w("HashemUi", "step failed", t); }
        }

        boolean isNight() {
            return (m.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        }

        // ---------- Header: requested logo sizing and alignment ----------
        void header() {
            LinearLayout bar = (LinearLayout) m.root.getChildAt(0);
            ImageView logo = new ImageView(m);
            logo.setImageResource(R.drawable.ic_launcher_foreground);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMarginEnd(dp(8));
            bar.addView(logo, 0, lp);
            bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            LinearLayout brand = (LinearLayout) bar.getChildAt(1);
            brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            for (int i = 0; i < brand.getChildCount(); i++) {
                View child = brand.getChildAt(i);
                if (child instanceof TextView) child.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            }

        }

        // ---------- Filter chips ----------
        void buildChips() {
            LinearLayout tabs = (LinearLayout) m.filtersScroll.getChildAt(0);
            tabs.removeAllViews();
            String[] labels = { m.getString(R.string.chip_all), m.getString(R.string.chip_unread), m.getString(R.string.chip_failed), m.getString(R.string.chip_bulk), m.getString(R.string.chip_unknown), "مجدولة" };
            int[] icons = {R.drawable.ic_chip_all, R.drawable.ic_chip_eye, R.drawable.ic_chip_warning, R.drawable.ic_group, R.drawable.ic_nav_contacts, R.drawable.ic_schedule};
            ViewGroup.LayoutParams old = m.filtersScroll.getLayoutParams();
            if (old != null) { old.height = dp(34); m.filtersScroll.setLayoutParams(old); }
            for (int i = 0; i < filters.length; i++) {
                final String f = filters[i];
                LinearLayout chip = new LinearLayout(m);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPaddingRelative(dp(10), 0, dp(10), 0);
                ImageView ic = new ImageView(m);
                ic.setImageResource(icons[i]);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(16), dp(16));
                ilp.setMarginEnd(dp(6));
                chip.addView(ic, ilp);
                TextView tx = new TextView(m);
                tx.setText(labels[i]);
                tx.setTextSize(13);
                if (bold != null) tx.setTypeface(bold);
                chip.addView(tx);
                chip.setOnClickListener(v -> { m.filter = f; m.selectedBottomTab = "conversations"; m.render(); });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(34));
                clp.setMarginEnd(dp(6));
                tabs.addView(chip, clp);
                chips[i] = chip; chipTexts[i] = tx; chipIcons[i] = ic;
            }
        }

        void styleChip(int i, boolean sel) {
            int fill = sel ? col(R.color.accent_cyan) : col(R.color.surface_variant);
            int stroke = sel ? col(R.color.accent_cyan) : col(R.color.search_border);
            int fg = sel ? col(R.color.on_primary) : col(R.color.on_surface);
            chips[i].setBackground(round(fill, stroke, dp(24), dp(1)));
            chipTexts[i].setTextColor(fg);
            chipIcons[i].setColorFilter(fg);
        }

        // ---------- Floating compose button ----------
        void compose() {
            Button b = m.composeButton;
            if (b == null || m.contentHost == null) return;
            ViewGroup p = (ViewGroup) b.getParent();
            if (p != null) p.removeView(b);
            try { b.setText(m.getString(R.string.compose_start)); } catch (Throwable ignored) { b.setText("بدء محادثة"); }
            b.setTextSize(14);
            b.setTextColor(Color.WHITE);
            if (bold != null) b.setTypeface(bold);
            b.setMinWidth(0);
            b.setMinHeight(dp(40));
            b.setMinimumHeight(dp(40));
            b.setPaddingRelative(dp(18), 0, dp(18), 0);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF1F6FE0, 0xFF29B6F6});
            g.setCornerRadius(dp(30));
            g.setStroke(dp(1), 0x6638C8FF);
            b.setBackground(g);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_nav_messages, 0);
            Drawable[] ds = b.getCompoundDrawablesRelative();
            if (ds[2] != null) { ds[2] = ds[2].mutate(); ds[2].setTint(Color.WHITE); }
            b.setCompoundDrawablePadding(dp(10));
            b.setElevation(dp(6));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(40), Gravity.BOTTOM | Gravity.START);
            lp.setMargins(dp(16), 0, dp(16), dp(16));
            m.contentHost.addView(b, lp);
            if (m.threadsRecycler != null) {
                m.threadsRecycler.setClipToPadding(false);
                m.threadsRecycler.setPadding(0, 0, 0, dp(88));
            }
        }

        // ---------- Per-frame consistency (cheap, guarded) ----------
        void tick() {
            String key = m.filter + "|" + m.selectedBottomTab + "|" + isNight();
            if (!key.equals(lastKey)) {
                lastKey = key;
                for (int i = 0; i < chips.length; i++) {
                    if (chips[i] == null) continue;
                    boolean sel = filters[i].equals(m.filter) && "conversations".equals(m.selectedBottomTab);
                    styleChip(i, sel);
                }
            }
            styleThreads();
        }

        void styleThreads() {
            RecyclerView rv = m.threadsRecycler;
            if (rv == null || m.threadAdapter == null || rv.getVisibility() != View.VISIBLE) return;
            List<MainActivity.ThreadItem> items = m.threadAdapter.getCurrentList();
            for (int i = 0; i < rv.getChildCount(); i++) {
                RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
                if (!(vh instanceof MainActivity.ThreadViewHolder)) continue;
                int pos = vh.getBindingAdapterPosition();
                if (pos < 0 || pos >= items.size()) continue;
                styleRow((MainActivity.ThreadViewHolder) vh, items.get(pos));
            }
        }

        void styleRow(MainActivity.ThreadViewHolder h, MainActivity.ThreadItem it) {
            if (h.container.getTag() == null) {
                h.container.setTag("styled");
                ViewGroup.LayoutParams lp = h.container.getLayoutParams();
                if (lp instanceof RecyclerView.LayoutParams) {
                    RecyclerView.LayoutParams rlp = (RecyclerView.LayoutParams) lp;
                    rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    rlp.setMargins(0, 0, 0, 0);
                    h.container.setLayoutParams(rlp);
                }
            }
            // The requested changes are limited to slightly larger conversation text
            // and removal of the unused trailing row gap.
            h.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            h.body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            h.row.setPadding(dp(2), dp(6), dp(2), dp(6));
            if (h.avatar.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams avatarParams = (LinearLayout.LayoutParams) h.avatar.getLayoutParams();
                avatarParams.setMargins(0, 0, dp(4), 0);
                h.avatar.setLayoutParams(avatarParams);
            }
        }

    }
}
