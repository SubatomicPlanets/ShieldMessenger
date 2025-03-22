package com.subatomicplanets.shield;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.mindrot.jbcrypt.BCrypt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.crypto.Cipher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class ShieldService extends Service {
    private static final String STORE_KEY_ALIAS = "ShieldRSAKeys";
    private static final SocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 7788);
    private volatile boolean isRunning = false;
    private SSLSocket socket = null;
    private static KeyPair keyPair = null;
    private static String shieldNumber = "";
    private boolean hasLocalAccount = false;
    private static final ConcurrentLinkedQueue<byte[]> receiveQueue = new ConcurrentLinkedQueue<>();

    // Helper function to check if the socket is connected to the server
    private boolean isSocketConnected() {
        return  !(socket == null ||
                socket.isClosed() ||
                !socket.isConnected() ||
                socket.isInputShutdown() ||
                socket.isOutputShutdown());
    }

    // Helper function to check if a string is a phone number
    private boolean checkPhoneNumber(String phoneNumber) {
        return phoneNumber.length() > 4 && phoneNumber.length() < 16 && phoneNumber.matches("\\d+");
    }

    // Hash a phone number using bcrypt (also cache result)
    private String hashBcrypt(String data) {
        SharedPreferences sharedPreferences = getSharedPreferences("ShieldHashCache", Context.MODE_PRIVATE);
        String cachedHash = sharedPreferences.getString(data, null);
        if (cachedHash == null) {
            String hashed = BCrypt.hashpw(data, "$2a$15$vE3umhJXt2OTj7CxoyJ6hO");
            hashed = hashed.substring(hashed.length()-31);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(data, hashed);
            editor.apply();
            return hashed;
        } else{
            return cachedHash;
        }
    }

    // Signs a string
    private byte[] signString(String data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(data.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Verifies a string signature
    private boolean verifySignature(String data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.US_ASCII));
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Initializes everything
    @Override
    public void onCreate() {
        super.onCreate();
        if (!isRunning) {
            isRunning = true;

            // Initialize message storage
            MessageStorage.init(this);

            // Load keystore
            KeyStore keyStore = null;
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                if (keyStore.containsAlias(STORE_KEY_ALIAS)){
                    PrivateKey privateKey = (PrivateKey) keyStore.getKey(STORE_KEY_ALIAS, null);
                    PublicKey publicKey = keyStore.getCertificate(STORE_KEY_ALIAS).getPublicKey();
                    keyPair = new KeyPair(publicKey, privateKey);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Load account data if possible
            SharedPreferences sharedPreferences = getSharedPreferences("ShieldPrefs", MODE_PRIVATE);
            shieldNumber = sharedPreferences.getString("ShieldNumber", null);
            hasLocalAccount = shieldNumber != null && checkPhoneNumber(shieldNumber) && keyPair != null;

            // Connect to server
            startConnection();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Notify activity on login
        if (intent != null && intent.getAction() == null) {
            Intent returnIntent = new Intent("com.shield.setup");
            returnIntent.putExtra("accountStatus", hasLocalAccount?1:0);
            LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);
        }
        // Send message
        else if (intent != null && "com.shield.send_message".equals(intent.getAction())) {
            String number = intent.getStringExtra("number");
            String message = intent.getStringExtra("message");
            if (number != null && message != null) {
                sendMessage(number, message);
            }
        }
        // Create account
        else if (intent != null && "com.shield.create_account".equals(intent.getAction())) {
            String number = intent.getStringExtra("number");
            if (number != null) {
                createAccount(number);
            }
        }
        return START_STICKY;
    }

    // Starts a connection to the server. If it disconnects or can't reach the sever it tries again after 30 seconds
    private void startConnection() {
        new Thread(() -> {
            // SSL
            SSLSocketFactory factory;
            try (InputStream caInput = getResources().openRawResource(R.raw.cert)) {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", CertificateFactory.getInstance("X.509").generateCertificate(caInput));
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);
                sslContext.init(null, tmf.getTrustManagers(), null);
                factory = sslContext.getSocketFactory();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            // Connection loop
            while (isRunning) {
                try {
                    if (!isSocketConnected()) {
                        socket = (SSLSocket) factory.createSocket();
                        socket.connect(SERVER_ADDRESS, 3000);
                        socket.startHandshake();
                        if (hasLocalAccount) {
                            String shieldHash = hashBcrypt(shieldNumber);
                            OutputStream outputStream = socket.getOutputStream();
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            byteArrayOutputStream.write(64);
                            byteArrayOutputStream.write(shieldHash.getBytes());
                            byteArrayOutputStream.write(signString(shieldHash));
                            outputStream.write(byteArrayOutputStream.toByteArray());
                            outputStream.flush();
                        }
                        listenForServerMessages();
                    }
                } catch (IOException e) {
                    if (socket != null && !socket.isClosed()){
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                    }
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ignored) { }
                }
            }
        }).start();
    }

    // Listens for new messages from the server and does stuff with them
    private void listenForServerMessages() throws IOException {
        InputStream inputStream = socket.getInputStream();
        byte[] buffer = new byte[1024];
        while (isRunning && isSocketConnected()) {
            // Read from socket
            int bytesRead = inputStream.read(buffer);
            if (bytesRead == -1){
                if (!socket.isClosed()) socket.close();
                break;
            }
            // Get data
            if (bytesRead < 2) continue;
            byte command = buffer[0];
            byte[] data = new byte[bytesRead - 1];
            System.arraycopy(buffer, 1, data, 0, bytesRead - 1);
            // Switch between messages and other data
            switch (command) {
                case 64:
                    receiveQueue.offer(data);
                    break;
                case 65:
                    // Safety check
                    if (bytesRead < 33) break;
                    // Get data
                    String senderHash = new String(java.util.Arrays.copyOfRange(data, 0, 31), StandardCharsets.US_ASCII);
                    byte[] encryptedMessage = java.util.Arrays.copyOfRange(data, 31, data.length);
                    // Decrypt
                    byte[] decryptedMessage;
                    try {
                        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
                        decryptedMessage = cipher.doFinal(encryptedMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                    // Checks and save
                    String[] parts = new String(decryptedMessage).split(" ", 2);
                    if (parts.length == 2){
                        String number = parts[0].trim();
                        String message = parts[1].trim();
                        if (checkPhoneNumber(number) && senderHash.equals(hashBcrypt(number))){
                            MessageStorage.saveMessage(number, message, false);
                            Intent returnIntent = new Intent("com.shield.message_received");
                            returnIntent.putExtra("number", number);
                            returnIntent.putExtra("message", message);
                            LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void createAccount(String phoneNumber) {
        new Thread(() -> {
            try {
                if (isSocketConnected()){
                    Intent returnIntent = new Intent("com.shield.setup");
                    returnIntent.putExtra("accountStatus", 3);
                    returnIntent.putExtra("progress_percentage", 65);
                    returnIntent.putExtra("progress_text", "Hashing Phone Number...");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);

                    // Hash number
                    shieldNumber = phoneNumber;
                    String shieldHash = hashBcrypt(shieldNumber);

                    // Progress update
                    returnIntent = new Intent("com.shield.setup");
                    returnIntent.putExtra("progress_percentage", 90);
                    returnIntent.putExtra("progress_text", "Generating RSA keys...");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);

                    // Generate and save RSA keys
                    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
                    keyGen.initialize(new KeyGenParameterSpec.Builder(
                            STORE_KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                            .setKeySize(2048)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .build());
                    keyPair = keyGen.generateKeyPair();

                    // Progress update
                    returnIntent = new Intent("com.shield.setup");
                    returnIntent.putExtra("progress_percentage", 100);
                    returnIntent.putExtra("progress_text", "Creating Account...");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);

                    // Send to server
                    OutputStream outputStream = socket.getOutputStream();
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byteArrayOutputStream.write(65);
                    byteArrayOutputStream.write(shieldHash.getBytes());
                    byteArrayOutputStream.write(signString(shieldNumber));
                    byteArrayOutputStream.write(KeyConverter.getDerFromPublicKey(keyPair.getPublic()));
                    outputStream.write(byteArrayOutputStream.toByteArray());
                    outputStream.flush();

                    // Receive answer
                    long start = System.currentTimeMillis();
                    while (isRunning && isSocketConnected() && (System.currentTimeMillis() - start) < 5000) {
                        Thread.sleep(200);
                        byte[] data = receiveQueue.poll();
                        if (data == null) continue;
                        hasLocalAccount = (data.length == 1 && data[0] == 1);
                        break;
                    }

                    // Save the number and its hash if successful
                    if (hasLocalAccount) {
                        SharedPreferences sharedPreferences = getSharedPreferences("ShieldPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("ShieldNumber", shieldNumber);
                        editor.apply();
                    }

                    // Send result back to activity
                    returnIntent = new Intent("com.shield.setup");
                    returnIntent.putExtra("accountStatus", hasLocalAccount?1:0);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);
                }
                else {
                    // Reset the button if socket not connected
                    Intent returnIntent = new Intent("com.shield.setup");
                    returnIntent.putExtra("accountStatus", 2);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);
                }
            } catch (Exception e) {
                // If there was some error creating the account then go back to account creation page
                Intent returnIntent = new Intent("com.shield.setup");
                returnIntent.putExtra("accountStatus", 0);
                LocalBroadcastManager.getInstance(this).sendBroadcast(returnIntent);
                e.printStackTrace();
            }
        }).start();
    }

    private void sendMessage(String contactNumber, String message){
        new Thread(() -> {
            try {
                byte[] contactHash = hashBcrypt(contactNumber).getBytes();
                if (!isSocketConnected()){

                    return;
                }

                // Send request to get public key
                OutputStream outputStream = socket.getOutputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byteArrayOutputStream.write(66);
                byteArrayOutputStream.write(contactHash);
                outputStream.write(byteArrayOutputStream.toByteArray());
                outputStream.flush();

                // Receive answer
                long start = System.currentTimeMillis();
                while (isRunning && isSocketConnected() && (System.currentTimeMillis() - start) < 5000) {
                    Thread.sleep(200);
                    byte[] data = receiveQueue.poll();
                    if (data == null) continue;
                    if (data.length <= 256) break;

                    // Load and verify the public key
                    byte[] signatureRaw = Arrays.copyOfRange(data, 0, 256);
                    byte[] publicKeyRaw = Arrays.copyOfRange(data, 256, data.length);
                    PublicKey publicKey = KeyConverter.getPublicKeyFromDer(publicKeyRaw);
                    if (!verifySignature(contactNumber, signatureRaw, publicKey)) break;

                    // Encrypt the message
                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                    byte[] encryptedMessage = cipher.doFinal((shieldNumber+" "+message).getBytes());

                    // Send encrypted message
                    byteArrayOutputStream.reset();
                    byteArrayOutputStream.write(67);
                    byteArrayOutputStream.write(contactHash);
                    byteArrayOutputStream.write(encryptedMessage);
                    outputStream.write(byteArrayOutputStream.toByteArray());
                    outputStream.flush();

                    // Save to storage
                    MessageStorage.saveMessage(contactNumber, message, true);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (socket != null && !socket.isClosed()){
            new Thread(() -> {
                try {
                    socket.close();
                } catch (IOException ignored) {}

            });
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}