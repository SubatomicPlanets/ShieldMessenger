package com.subatomicplanets.shield;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class MessageStorage extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ShieldMessages.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_CONVERSATION_ID = "conversation_id";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_IS_USER_MESSAGE = "is_user_message";
    private static MessageStorage instance;

    private MessageStorage(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new MessageStorage(context.getApplicationContext());
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_MESSAGES_TABLE = "CREATE TABLE " + TABLE_MESSAGES + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_CONVERSATION_ID + " TEXT,"
                + COLUMN_MESSAGE + " TEXT,"
                + COLUMN_IS_USER_MESSAGE + " INTEGER" + ")";
        db.execSQL(CREATE_MESSAGES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }

    // Method to save a message
    public static void saveMessage(String conversationID, String message, boolean isUserMessage) {
        SQLiteDatabase db = instance.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CONVERSATION_ID, conversationID);
        values.put(COLUMN_MESSAGE, message);
        values.put(COLUMN_IS_USER_MESSAGE, isUserMessage ? 1 : 0);
        db.insert(TABLE_MESSAGES, null, values);
        db.close();
    }

    // Method to load messages for a conversation
    public static List<Message> loadMessages(String conversationID) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = instance.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MESSAGES,
                new String[]{COLUMN_MESSAGE, COLUMN_IS_USER_MESSAGE},
                COLUMN_CONVERSATION_ID + "=?",
                new String[]{conversationID},
                null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String message = cursor.getString(0);
                boolean isUserMessage = cursor.getInt(1) == 1;
                messages.add(new Message(message, isUserMessage));
            }
            cursor.close();
        }
        db.close();
        return messages;
    }

    // Message class to hold message data
    public static class Message {
        private final String message;
        private final boolean isUserMessage;

        public Message(String message, boolean isUserMessage) {
            this.message = message;
            this.isUserMessage = isUserMessage;
        }

        public String getMessage() {
            return message;
        }

        public boolean isUserMessage() {
            return isUserMessage;
        }
    }
}
