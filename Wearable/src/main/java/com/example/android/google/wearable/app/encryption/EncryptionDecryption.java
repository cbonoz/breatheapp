package com.example.android.google.wearable.app.encryption;

import android.util.Base64;
import android.util.Log;

import java.security.AlgorithmParameters;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by cbono on 11/29/15.
 */
public class EncryptionDecryption {
    private final static String TAG = "EncryptionDecryption";
    private static String salt;
    private static int iterations = 65536;
    private static int keySize = 128;//256; //use AES 256
    private static byte[] ivBytes;

    private static SecretKey secretKey;

    public EncryptionDecryption() {
        try {
            salt = getSalt();
        } catch (Exception e) {
            Log.e(TAG,e.toString());
            e.printStackTrace();

        }
    }

    public String encrypt(char[] plaintext) throws Exception {
        byte[] saltBytes = salt.getBytes();

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        PBEKeySpec spec = new PBEKeySpec(plaintext, saltBytes, iterations, keySize);
        secretKey = skf.generateSecret(spec);
        SecretKeySpec secretSpec = new SecretKeySpec(secretKey.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretSpec);
        AlgorithmParameters params = cipher.getParameters();
        ivBytes = params.getParameterSpec(IvParameterSpec.class).getIV();
        byte[] encryptedTextBytes = cipher.doFinal(String.valueOf(plaintext).getBytes("UTF-8"));

        return Base64.encodeToString(encryptedTextBytes, Base64.DEFAULT);
    }

    public String decrypt(char[] encryptedText) throws Exception {

        System.out.println(encryptedText);

        byte[] encryptedTextBytes = Base64.decode(new String(encryptedText), Base64.DEFAULT);
        SecretKeySpec secretSpec = new SecretKeySpec(secretKey.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretSpec, new IvParameterSpec(ivBytes));

        byte[] decryptedTextBytes = null;

        try {
            decryptedTextBytes = cipher.doFinal(encryptedTextBytes);
        }   catch (Exception e) {
            e.printStackTrace();
        }
        if (decryptedTextBytes!=null)
            return new String(decryptedTextBytes);

        return "";

    }

    public String getSalt() throws Exception {

        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        byte[] salt = new byte[20];
        sr.nextBytes(salt);
        return new String(salt);
    }


        /*
    public static void main(String []args) throws Exception {

        salt = getSalt();

        char[] message = "PasswordToEncrypt".toCharArray();
        System.out.println("Message: " + String.valueOf(message));
        System.out.println("Encrypted: " + encrypt(message));
        System.out.println("Decrypted: " + decrypt(encrypt(message).toCharArray()));
    }
    */
}