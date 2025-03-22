package com.subatomicplanets.shield;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.TextKeyListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

public class ChatActivity extends Activity {
    private LinearLayout chatContainer;
    private ScrollView chatScrollView;
    private boolean shouldScrollToBottom = true;
    private BroadcastReceiver messageReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Find UI components
        chatContainer = findViewById(R.id.chatContainer);
        chatScrollView = findViewById(R.id.chatScrollView);
        EditText messageInput = findViewById(R.id.messageInput);
        TextView userNameHeader = findViewById(R.id.userNameHeader);

        // Get contact data
        String contactNumber = getIntent().getStringExtra("contactNumber");
        String contactName = getIntent().getStringExtra("contactName");
        if (contactNumber == null || contactName == null) return;
        userNameHeader.post(() -> userNameHeader.setText(contactName));

        // Display old messages
        List<MessageStorage.Message> messages = MessageStorage.loadMessages(contactNumber);
        for (MessageStorage.Message msg : messages) {
            displayMessage(msg.getMessage(), msg.isUserMessage());
        }

        // Register receiver
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String number = intent.getStringExtra("number");
                if (contactNumber.equals(number)) {
                    String message = intent.getStringExtra("message");
                    displayMessage(message, false);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("com.shield.message_received"));

        // On send button pressed
        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> sendMessage(messageInput, contactNumber));

        // Scrolling
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        chatScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> shouldScrollToBottom = isAtBottom());
        chatScrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (shouldScrollToBottom) {
                chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    public void displayMessage(String message, boolean isUserMessage) {
        // Displays a message
        LayoutInflater inflater = LayoutInflater.from(this);
        TextView messageView = (TextView) inflater.inflate(R.layout.chat_item, chatContainer, false);
        messageView.setText(message);
        if (isUserMessage) messageView.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2196F3")));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = isUserMessage ? Gravity.END : Gravity.START;
        params.bottomMargin = 12;
        messageView.setLayoutParams(params);
        chatContainer.addView(messageView);
    }

    private void sendMessage(EditText messageInput, String contactNumber) {
        // Get message
        String message = messageInput.getText().toString().trim();

        // Checks
        if (message.length() < 1 || message.length() > 200) {
            return;
        }

        // Display
        TextKeyListener.clear(messageInput.getText());
        displayMessage(message, true);

        // Send to service
        Intent intent = new Intent("com.shield.send_message");
        intent.setClass(this, ShieldService.class);
        intent.putExtra("number", contactNumber);
        intent.putExtra("message", message);
        startService(intent);
    }

    private boolean isAtBottom() {
        // Checks if scroll view is at bottom
        int scrollY = chatScrollView.getScrollY();
        int height = chatScrollView.getHeight();
        int scrollViewHeight = chatContainer.getHeight();
        return scrollY + height >= scrollViewHeight - 100;
    }

    @Override
    protected void onDestroy() {
        // Cleanup
        if (messageReceiver != null){
            LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        }
        super.onDestroy();
    }
}