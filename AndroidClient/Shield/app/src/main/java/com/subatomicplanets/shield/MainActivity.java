package com.subatomicplanets.shield;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize message storage
        MessageStorage.init(this);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int accountStatus = intent.getIntExtra("accountStatus", -1);
                switch (accountStatus){
                    case 0:{ // Has no account
                        startAccountCreation();
                        break;
                    } case 1: { // Has an account
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                        startActivity(new Intent(context, ContactsActivity.class));
                        finish();
                        break;
                    } case 2: { // Reset button enabled state
                        Button startButton = findViewById(R.id.startButton);
                        if (startButton != null) startButton.setEnabled(true);
                        break;
                    } case 3: { // Show loading screen
                        setContentView(R.layout.activity_login_loading);
                        break;
                    } default: {
                        break;
                    }
                }
                int percentage = intent.getIntExtra("progress_percentage", -1);
                String text = intent.getStringExtra("progress_text");
                if (percentage >= 0 && text != null){
                    progressCheckpoint(percentage, text);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("com.shield.setup"));

        // Start shield service
        Intent serviceIntent = new Intent(this, ShieldService.class);
        startService(serviceIntent);
    }

    protected void startAccountCreation(){
        setContentView(R.layout.activity_login);

        EditText phoneNumberInput = findViewById(R.id.phoneNumberInput);
        Button startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(v -> {
            String phoneNumber = phoneNumberInput.getText().toString().replaceAll("[^\\d]", "").replaceFirst("^00", "");
            if (phoneNumber.length() < 5 || phoneNumber.length() > 15) return;
            startButton.setEnabled(false);

            Intent intent = new Intent("com.shield.create_account");
            intent.setClass(this, ShieldService.class);
            intent.putExtra("number", phoneNumber);
            startService(intent);
        });
    }

    private void progressCheckpoint(int targetPercentage, String text) {
        // Get elements
        TextView loadingText = findViewById(R.id.loadingText);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        if (loadingText == null || progressBar == null) return;

        // Animate text
        loadingText.clearAnimation();
        loadingText.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            loadingText.setText(text);
            loadingText.animate().alpha(1f).setDuration(400).start();
        }).start();

        // Animate progress bar
        progressBar.clearAnimation();
        int currentProgress = progressBar.getProgress();
        long duration = (targetPercentage - currentProgress)*100L;
        ValueAnimator animator = ValueAnimator.ofInt(currentProgress, targetPercentage);
        animator.setDuration(duration).setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> progressBar.setProgress((int)animation.getAnimatedValue()));
        animator.start();
    }
}
