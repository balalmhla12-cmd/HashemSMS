package com.hashem.sms;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

public final class AppLockActivity extends AppCompatActivity {
    private static final int REQUEST_CREDENTIAL = 8041;
    private boolean authenticationStarted;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // Keep the app behind the authentication gate.
            }
        });
        if (!AppLockController.isEnabled(this)) {
            AppLockController.unlockForSession();
            finish();
            return;
        }
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            AppLockController.setEnabled(this, false);
            Toast.makeText(this, R.string.app_lock_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildScreen();
        new android.os.Handler(getMainLooper()).post(this::authenticate);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padding = UiResources.dp(this, 28);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(UiResources.color(this, R.color.surface));
        TextView title = new TextView(this);
        title.setText(R.string.app_lock_title);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(UiResources.color(this, R.color.on_surface));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = new TextView(this);
        description.setText(R.string.app_lock_description);
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        description.setTextColor(UiResources.color(this, R.color.on_surface_variant));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = UiResources.dp(this, 18);
        root.addView(description, descriptionParams);
        Button retry = new Button(this);
        retry.setText(R.string.app_lock_retry);
        retry.setAllCaps(false);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-1, -2);
        retryParams.topMargin = UiResources.dp(this, 26);
        root.addView(retry, retryParams);
        retry.setOnClickListener(view -> authenticate());
        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private void authenticate() {
        if (isFinishing() || authenticationStarted || !AppLockController.isEnabled(this)) return;
        authenticationStarted = true;
        if (Build.VERSION.SDK_INT < 28) {
            requestDeviceCredential();
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        AppLockController.authenticated();
                        finish();
                    }

                    @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence message) {
                        authenticationStarted = false;
                        Toast.makeText(AppLockActivity.this,
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                        || errorCode == BiometricPrompt.ERROR_CANCELED
                                        || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                        ? R.string.app_lock_auth_cancelled : R.string.app_lock_auth_failed,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onAuthenticationFailed() {
                        Toast.makeText(AppLockActivity.this, R.string.app_lock_auth_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_lock_title))
                .setDescription(getString(R.string.app_lock_description));
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }
        try {
            prompt.authenticate(builder.build());
        } catch (RuntimeException error) {
            authenticationStarted = false;
            android.util.Log.e("HashemSMS", "Biometric authentication could not start", error);
            Toast.makeText(this, R.string.app_lock_auth_failed, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private void requestDeviceCredential() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent intent = keyguard == null ? null
                : keyguard.createConfirmDeviceCredentialIntent(
                        getString(R.string.app_lock_title), getString(R.string.app_lock_description));
        if (intent == null) {
            authenticationStarted = false;
            AppLockController.setEnabled(this, false);
            Toast.makeText(this, R.string.app_lock_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            startActivityForResult(intent, REQUEST_CREDENTIAL);
        } catch (RuntimeException error) {
            authenticationStarted = false;
            android.util.Log.e("HashemSMS", "Device credential screen could not start", error);
            Toast.makeText(this, R.string.app_lock_auth_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREDENTIAL) return;
        authenticationStarted = false;
        if (resultCode == RESULT_OK) {
            AppLockController.authenticated();
            finish();
        } else {
            Toast.makeText(this, R.string.app_lock_auth_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

}
