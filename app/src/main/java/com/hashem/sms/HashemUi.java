package com.hashem.sms;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
        static final int[] PALETTE = {0xFF29C5F6, 0xFFB39DFF, 0xFF4DD0B1, 0xFFE6C15A, 0xFFF48FB1, 0xFF9CCC65, 0xFFE0A070};
        static final SimpleDateFormat DAY = new SimpleDateFormat("EEEE", new Locale("ar"));

        final MainActivity m;
        final String[] filters = {"all", "unread", "failed", "bulk", "groups", "starred"};
        final LinearLayout[] chips = new LinearLayout[6];
        final TextView[] chipTexts = new TextView[6];
        final ImageView[] chipIcons = new ImageView[6];
        TextView unreadBadge, failedBadge;
        Typeface bold;
        String lastKey = "";
        long lastCount = 0;
        int errors = 0;
        final Drawable[] navMine = new Drawable[6];
        final boolean[] navSel = new boolean[6];
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
            safe(this::search);
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

        void setNight(boolean night) {
            int mode = night ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            m.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_NIGHT, mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        }

        // ---------- Header: logo + theme toggle ----------
        void header() {
            LinearLayout bar = (LinearLayout) m.root.getChildAt(0);
            ImageView logo = new ImageView(m);
            logo.setImageResource(R.drawable.ic_launcher_foreground);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(54));
            lp.setMarginEnd(dp(8));
            bar.addView(logo, 0, lp);

            LinearLayout actions = (LinearLayout) bar.getChildAt(bar.getChildCount() - 1);
            if (actions.getChildCount() > 0) actions.getChildAt(0).setVisibility(View.GONE); // old search icon

            boolean night = isNight();
            LinearLayout pill = new LinearLayout(m);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(3), dp(3), dp(3), dp(3));
            pill.setBackground(round(col(R.color.surface_variant), col(R.color.search_border), dp(24), dp(1)));
            ImageView moon = toggleIcon(R.drawable.ic_moon, night, R.string.dark_mode);
            ImageView sun = toggleIcon(R.drawable.ic_sun, !night, R.string.light_mode);
            moon.setOnClickListener(v -> setNight(true));
            sun.setOnClickListener(v -> setNight(false));
            pill.addView(moon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            pill.addView(sun, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.setMarginEnd(dp(8));
            actions.addView(pill, 0, plp);
        }

        ImageView toggleIcon(int res, boolean active, int contentDescRes) {
            ImageView v = new ImageView(m);
            v.setImageResource(res);
            v.setScaleType(ImageView.ScaleType.CENTER);
            try { v.setContentDescription(m.getString(contentDescRes)); } catch (Throwable ignored) {}
            v.setColorFilter(active ? col(R.color.on_primary) : col(R.color.on_surface_variant));
            if (active) v.setBackground(oval(col(R.color.accent_cyan), 0, 0));
            return v;
        }

        // ---------- Search bar ----------
        void search() {
            int idx = m.root.indexOfChild(m.search);
            if (idx < 0) return;
            m.root.removeViewAt(idx);
            LinearLayout box = new LinearLayout(m);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPaddingRelative(dp(5), dp(5), dp(16), dp(5));
            box.setBackground(round(col(R.color.surface_variant), col(R.color.search_border), dp(30), dp(1)));
            ImageView circle = new ImageView(m);
            circle.setImageResource(R.drawable.ic_search);
            circle.setColorFilter(col(R.color.on_primary));
            circle.setScaleType(ImageView.ScaleType.CENTER);
            circle.setBackground(oval(col(R.color.accent_cyan), 0, 0));
            try { circle.setContentDescription(m.getString(R.string.search_cd)); } catch (Throwable ignored) {}
            circle.setOnClickListener(v -> {
                m.search.requestFocus();
                InputMethodManager imm = (InputMethodManager) m.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(m.search, InputMethodManager.SHOW_IMPLICIT);
            });
            box.addView(circle, new LinearLayout.LayoutParams(dp(46), dp(46)));
            m.search.setBackground(null);
            m.search.setHint(R.string.search_hint);
            m.search.setPaddingRelative(dp(14), 0, dp(10), 0);
            box.addView(m.search, new LinearLayout.LayoutParams(0, -1, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
            lp.bottomMargin = dp(10);
            m.root.addView(box, idx, lp);
        }

        // ---------- Filter chips ----------
        void buildChips() {
            LinearLayout tabs = (LinearLayout) m.filtersScroll.getChildAt(0);
            tabs.removeAllViews();
            String[] labels = { m.getString(R.string.chip_all), m.getString(R.string.chip_unread), m.getString(R.string.chip_failed), m.getString(R.string.chip_bulk), m.getString(R.string.chip_groups), m.getString(R.string.chip_starred) };
            int[] icons = {R.drawable.ic_chip_all, R.drawable.ic_chip_eye, R.drawable.ic_chip_warning, R.drawable.ic_nav_messages, R.drawable.ic_nav_messages, R.drawable.ic_star};
            ViewGroup.LayoutParams old = m.filtersScroll.getLayoutParams();
            if (old != null) { old.height = dp(52); m.filtersScroll.setLayoutParams(old); }
            for (int i = 0; i < filters.length; i++) {
                final String f = filters[i];
                LinearLayout chip = new LinearLayout(m);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPaddingRelative(dp(14), 0, dp(14), 0);
                ImageView ic = new ImageView(m);
                ic.setImageResource(icons[i]);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
                ilp.setMarginEnd(dp(6));
                chip.addView(ic, ilp);
                TextView tx = new TextView(m);
                tx.setText(labels[i]);
                tx.setTextSize(14);
                if (bold != null) tx.setTypeface(bold);
                chip.addView(tx);
                if (i > 0) {
                    TextView badge = new TextView(m);
                    badge.setTextSize(12);
                    badge.setTextColor(Color.WHITE);
                    badge.setGravity(Gravity.CENTER);
                    if (bold != null) badge.setTypeface(bold);
                    badge.setMinWidth(dp(22));
                    badge.setPadding(dp(5), 0, dp(5), 0);
                    badge.setBackground(round(i == 1 ? 0xFF2F80FF : col(R.color.error), 0, dp(12), 0));
                    badge.setVisibility(View.GONE);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, dp(22));
                    blp.setMarginStart(dp(8));
                    chip.addView(badge, blp);
                    if (i == 1) unreadBadge = badge; else if (i == 2) failedBadge = badge;
                }
                chip.setOnClickListener(v -> { m.filter = f; m.selectedBottomTab = "conversations"; m.render(); });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(44));
                clp.setMarginEnd(dp(8));
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

        void setBadge(TextView b, int n) {
            if (b == null || n < 0) return;
            if (n == 0) { if (b.getVisibility() != View.GONE) b.setVisibility(View.GONE); return; }
            String t = n > 99 ? "99+" : String.valueOf(n);
            if (!t.contentEquals(b.getText())) b.setText(t);
            if (b.getVisibility() != View.VISIBLE) b.setVisibility(View.VISIBLE);
        }

        void refreshCounts() {
            long now = SystemClock.uptimeMillis();
            if (now - lastCount < 3000) return;
            lastCount = now;
            MainActivity.DATA_EXECUTOR.execute(() -> {
                int u = -1, f = -1;
                Cursor c = null;
                try { c = m.db.listThreads("unread"); u = c.getCount(); } catch (Throwable ignored) {}
                finally { if (c != null) try { c.close(); } catch (Throwable ignored) {} }
                c = null;
                try { c = m.db.list("failed"); f = c.getCount(); } catch (Throwable ignored) {}
                finally { if (c != null) try { c.close(); } catch (Throwable ignored) {} }
                final int uu = u, ff = f;
                m.runOnUiThread(() -> {
                    if (m.isFinishing() || m.isDestroyed()) return;
                    setBadge(unreadBadge, uu);
                    setBadge(failedBadge, ff);
                });
            });
        }

        // ---------- Floating compose button ----------
        void compose() {
            Button b = m.composeButton;
            if (b == null || m.contentHost == null) return;
            ViewGroup p = (ViewGroup) b.getParent();
            if (p != null) p.removeView(b);
            try { b.setText(m.getString(R.string.compose_start)); } catch (Throwable ignored) { b.setText("بدء محادثة"); }
            b.setTextSize(15);
            b.setTextColor(Color.WHITE);
            if (bold != null) b.setTypeface(bold);
            b.setMinWidth(0);
            b.setMinHeight(dp(52));
            b.setMinimumHeight(dp(52));
            b.setPaddingRelative(dp(22), 0, dp(22), 0);
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
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(52), Gravity.BOTTOM | Gravity.START);
            lp.setMargins(dp(16), 0, dp(16), dp(16));
            m.contentHost.addView(b, lp);
            if (m.threadsRecycler != null) {
                m.threadsRecycler.setClipToPadding(false);
                m.threadsRecycler.setPadding(0, 0, 0, dp(88));
            }
        }

        // ---------- Per-frame consistency (cheap, guarded) ----------
        void tick() {
            styleNav();
            String key = m.filter + "|" + m.selectedBottomTab + "|" + isNight();
            if (!key.equals(lastKey)) {
                lastKey = key;
                for (int i = 0; i < chips.length; i++) {
                    if (chips[i] == null) continue;
                    boolean sel = filters[i].equals(m.filter) && "conversations".equals(m.selectedBottomTab);
                    styleChip(i, sel);
                }
            }
            refreshCounts();
            styleThreads();
        }

        void styleNav() {
            LinearLayout nav = m.bottomNavigation;
            if (nav == null) return;
            int n = Math.min(nav.getChildCount(), navMine.length);
            for (int i = 0; i < n; i++) {
                View c = nav.getChildAt(i);
                boolean sel = c.getTag() != null && c.getTag().equals(m.selectedBottomTab);
                if (c.getBackground() != navMine[i] || navSel[i] != sel) {
                    Drawable d = sel
                            ? round(col(R.color.primary_container), col(R.color.accent_cyan), dp(24), dp(2))
                            : round(0x00000000, 0, dp(24), 0);
                    navMine[i] = d; navSel[i] = sel;
                    c.setBackground(d);
                }
            }
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
                h.container.setBackground(round(col(R.color.card_surface), col(R.color.card_stroke), dp(18), dp(1)));
                ViewGroup.LayoutParams lp = h.container.getLayoutParams();
                if (lp instanceof RecyclerView.LayoutParams) {
                    RecyclerView.LayoutParams rlp = (RecyclerView.LayoutParams) lp;
                    rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    rlp.setMargins(dp(2), dp(4), dp(2), dp(4));
                    h.container.setLayoutParams(rlp);
                }
                View divider = h.container.getChildAt(1);
                if (divider != null) divider.setVisibility(View.GONE);
                if (bold != null) h.name.setTypeface(bold);
            }
            String label = it.contact.isEmpty() ? it.address : it.contact;
            int ring = PALETTE[Math.abs(label.hashCode()) % PALETTE.length];
            if (!Integer.valueOf(ring).equals(h.avatar.getTag())) {
                h.avatar.setTag(ring);
                h.avatar.setBackground(oval((ring & 0x00FFFFFF) | 0x26000000, ring, dp(2)));
                h.fallback.setTextColor(ring);
            }
            String initials = avatarText(label);
            if (!initials.contentEquals(h.fallback.getText())) {
                h.fallback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                h.fallback.setMaxLines(2);
                h.fallback.setText(initials);
            }
            String t = dayLabel(it.timestamp);
            if (!t.isEmpty() && !t.contentEquals(h.time.getText())) h.time.setText(t);
        }

        static String avatarText(String label) {
            String s = label == null ? "" : label.trim();
            if (s.isEmpty()) return "?";
            if (s.matches("[+0-9 ()\\-]+")) {
                String d = s.replaceAll("[^0-9]", "");
                return d.length() > 4 ? d.substring(d.length() - 4) : d;
            }
            String[] w = s.split("\\s+");
            return w.length > 1 ? w[0] + "\n" + w[1] : w[0];
        }

        static boolean sameDay(Calendar a, Calendar b) {
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                    && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        }

        static String dayLabel(long ts) {
            if (ts <= 0) return "";
            Calendar now = Calendar.getInstance();
            Calendar t = Calendar.getInstance();
            t.setTimeInMillis(ts);
            Calendar y = (Calendar) now.clone();
            y.add(Calendar.DAY_OF_YEAR, -1);
            if (sameDay(now, t)) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
            if (sameDay(y, t)) return "أمس";
            long days = (now.getTimeInMillis() - ts) / 86400000L;
            if (days < 6) return DAY.format(new Date(ts));
            return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(ts));
        }
    }
}
