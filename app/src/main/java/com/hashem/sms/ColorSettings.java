package com.hashem.sms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.Locale;

/** Shared persistent palette and accessible picker for all user-customizable colors. */
final class ColorSettings {
    static final int NO_OVERRIDE = Integer.MIN_VALUE;
    static final String INCOMING = "incoming_bubble";
    static final String OUTGOING = "outgoing_bubble";
    static final String CONVERSATION_BACKGROUND = "conversation_background";
    static final String SIM_ONE = "sim_one";
    static final String SIM_TWO = "sim_two";
    private static final String PREFS = "hashem_color_preferences";
    private static final String PREFIX = "color_";
    private static final int[] PRESETS = {
            0xFF006B91, 0xFF007EA8, 0xFF00A6A6, 0xFF2E7D32,
            0xFF558B2F, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5,
            0xFFE65100, 0xFFC62828, 0xFF6D4C41, 0xFF455A64
    };

    private ColorSettings() {}

    static int get(Context context, String key, int defaultResource) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREFIX + key, ContextCompat.getColor(context, defaultResource));
    }

    static void save(Context context, String key, int color) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PREFIX + key, color).apply();
    }

    static void reset(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(PREFIX + key).apply();
    }

    static int conversationBackground(Context context) {
        return get(context, CONVERSATION_BACKGROUND, R.color.conversation_background);
    }

    static int simColor(Context context, int slot) {
        return slot <= 0 ? get(context, SIM_ONE, R.color.primary_variant)
                : get(context, SIM_TWO, R.color.icon_orange);
    }

    static int colorOverride(Context context, int resourceId) {
        if (resourceId == R.color.bubble_in) return get(context, INCOMING, R.color.bubble_in);
        if (resourceId == R.color.bubble_out) return get(context, OUTGOING, R.color.bubble_out);
        if (resourceId == R.color.on_bubble_in) {
            return readableTextColor(get(context, INCOMING, R.color.bubble_in));
        }
        if (resourceId == R.color.on_bubble_out) {
            return readableTextColor(get(context, OUTGOING, R.color.bubble_out));
        }
        if (resourceId == R.color.conversation_text) {
            return readableTextColor(conversationBackground(context));
        }
        return NO_OVERRIDE;
    }

    static int readableTextColor(int background) {
        double white = contrastRatio(Color.WHITE, background);
        double black = contrastRatio(Color.BLACK, background);
        return white >= black ? Color.WHITE : Color.BLACK;
    }

    static double contrastRatio(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        double lighter = Math.max(first, second);
        double darker = Math.min(first, second);
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    private static double luminance(int color) {
        double red = linear(Color.red(color) / 255d);
        double green = linear(Color.green(color) / 255d);
        double blue = linear(Color.blue(color) / 255d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private static double linear(double value) {
        return value <= 0.04045d ? value / 12.92d
                : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    static void showPicker(Activity activity, String key, String label, int defaultResource) {
        final int defaultColor = ContextCompat.getColor(activity, defaultResource);
        final int[] selected = {get(activity, key, defaultResource)};
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8));

        TextView previewLabel = new TextView(activity);
        previewLabel.setText(R.string.color_picker_preview);
        previewLabel.setTextSize(14);
        panel.addView(previewLabel);

        TextView preview = new TextView(activity);
        preview.setText(label + "  Aa");
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(18);
        preview.setPadding(dp(activity, 12), dp(activity, 14), dp(activity, 12), dp(activity, 14));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(activity, 62));
        previewLp.topMargin = dp(activity, 6);
        previewLp.bottomMargin = dp(activity, 12);
        panel.addView(preview, previewLp);

        TextView hueLabel = new TextView(activity);
        hueLabel.setText(R.string.color_picker_hue);
        panel.addView(hueLabel);
        HuePicker huePicker = new HuePicker(activity);
        LinearLayout.LayoutParams hueLp = new LinearLayout.LayoutParams(-1, dp(activity, 30));
        hueLp.topMargin = dp(activity, 6);
        hueLp.bottomMargin = dp(activity, 12);
        panel.addView(huePicker, hueLp);

        TextView shadeLabel = new TextView(activity);
        shadeLabel.setText(R.string.color_picker_shade);
        panel.addView(shadeLabel);
        ShadePicker shadePicker = new ShadePicker(activity);
        panel.addView(shadePicker, new LinearLayout.LayoutParams(-1, dp(activity, 174)));

        TextView presetLabel = new TextView(activity);
        presetLabel.setText("ألوان جاهزة");
        LinearLayout.LayoutParams presetLabelLp = new LinearLayout.LayoutParams(-1, -2);
        presetLabelLp.topMargin = dp(activity, 12);
        panel.addView(presetLabel, presetLabelLp);
        GridLayout presets = new GridLayout(activity);
        presets.setColumnCount(6);
        final EditText[] hexInputRef = {null};
        for (int color : PRESETS) {
            Button swatch = new Button(activity);
            swatch.setText("");
            swatch.setContentDescription(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF));
            swatch.setBackground(tintDrawable(activity, color));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(activity, 38);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(activity, 2), dp(activity, 3), dp(activity, 2), dp(activity, 3));
            presets.addView(swatch, lp);
            swatch.setOnClickListener(v -> {
                selected[0] = color;
                updatePreview(preview, label, color);
                updateHex(hexInputRef[0], color);
                shadePicker.setColor(color);
                huePicker.setColor(color);
            });
        }
        panel.addView(presets);

        TextView hexLabel = new TextView(activity);
        hexLabel.setText(R.string.color_picker_hex);
        LinearLayout.LayoutParams hexLabelLp = new LinearLayout.LayoutParams(-1, -2);
        hexLabelLp.topMargin = dp(activity, 8);
        panel.addView(hexLabel, hexLabelLp);
        EditText hexInput = new EditText(activity);
        hexInputRef[0] = hexInput;
        hexInput.setSingleLine(true);
        hexInput.setText(String.format(Locale.ROOT, "#%06X", selected[0] & 0xFFFFFF));
        hexInput.setHint("#1A73E8");
        panel.addView(hexInput, new LinearLayout.LayoutParams(-1, dp(activity, 48)));

        final boolean[] updatingHex = {false};
        Runnable[] sync = new Runnable[1];
        sync[0] = () -> {
            int color = shadePicker.getColor();
            selected[0] = color;
            updatePreview(preview, label, color);
            updateHex(hexInput, color);
        };
        huePicker.setListener(hue -> { shadePicker.setHue(hue); sync[0].run(); });
        shadePicker.setListener(sync[0]);
        shadePicker.setColor(selected[0]);
        huePicker.setColor(selected[0]);
        updatePreview(preview, label, selected[0]);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updatingHex[0]) return;
                String value = s.toString().trim();
                if (value.matches("#?[0-9a-fA-F]{6}")) {
                    String digits = value.startsWith("#") ? value.substring(1) : value;
                    int color = 0xFF000000 | Integer.parseInt(digits, 16);
                    selected[0] = color;
                    updatePreview(preview, label, color);
                    shadePicker.setColor(color);
                    huePicker.setColor(color);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Button reset = new Button(activity);
        reset.setText(R.string.color_picker_reset);
        reset.setAllCaps(false);
        panel.addView(reset);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.color_picker_title)
                .setView(panel)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, which) -> save(activity, key, selected[0]))
                .create();
        reset.setOnClickListener(v -> {
            selected[0] = defaultColor;
            shadePicker.setColor(defaultColor);
            huePicker.setColor(defaultColor);
            updatePreview(preview, label, defaultColor);
            updateHex(hexInput, defaultColor);
        });
        dialog.show();
    }

    private static void updatePreview(TextView preview, String label, int color) {
        preview.setText(label + "  Aa");
        preview.setTextColor(readableTextColor(color));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(preview.getResources().getDisplayMetrics().density * 14);
        preview.setBackground(background);
    }

    private static void updateHex(EditText input, int color) {
        if (input == null) return;
        String value = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        if (!value.contentEquals(input.getText())) input.setText(value);
        input.setSelection(input.length());
    }

    private static android.graphics.drawable.GradientDrawable tintDrawable(Context context, int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, 1), 0x55777777);
        return drawable;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class HuePicker extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        private float hue;
        private java.util.function.Consumer<Float> listener;

        HuePicker(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setListener(java.util.function.Consumer<Float> listener) { this.listener = listener; }
        void setColor(int color) { float[] hsv = new float[3]; Color.colorToHSV(color, hsv); hue = hsv[0]; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0, colors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), getHeight() / 2f, getHeight() / 2f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 3));
            paint.setColor(Color.WHITE);
            float x = getWidth() * hue / 360f;
            canvas.drawCircle(x, getHeight() / 2f, getHeight() / 2f - dp(getContext(), 2), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE
                    || event.getAction() == MotionEvent.ACTION_UP) {
                hue = Math.max(0, Math.min(360, event.getX() / Math.max(1, getWidth()) * 360f));
                invalidate();
                if (listener != null) listener.accept(hue);
                return true;
            }
            return true;
        }
    }

    private static final class ShadePicker extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private float saturation = 0.65f;
        private float value = 0.9f;
        private Runnable listener;
        ShadePicker(Context context) { super(context); }
        void setListener(Runnable listener) { this.listener = listener; }
        void setHue(float hue) { this.hue = hue; invalidate(); }
        void setColor(int color) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; invalidate();
        }
        int getColor() { return Color.HSVToColor(new float[]{hue, saturation, value}); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int pure = Color.HSVToColor(new float[]{hue, 1f, 1f});
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0, Color.WHITE, pure, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), 0x00000000, Color.BLACK, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(saturation * getWidth(), (1f - value) * getHeight(), dp(getContext(), 7), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE
                    || event.getAction() == MotionEvent.ACTION_UP) {
                saturation = Math.max(0f, Math.min(1f, event.getX() / Math.max(1, getWidth())));
                value = 1f - Math.max(0f, Math.min(1f, event.getY() / Math.max(1, getHeight())));
                invalidate();
                if (listener != null) listener.run();
                return true;
            }
            return true;
        }
    }
}
