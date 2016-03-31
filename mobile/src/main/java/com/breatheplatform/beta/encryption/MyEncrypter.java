package com.breatheplatform.beta.encryption;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Created by cbono on 3/27/16.
 * http://stackoverflow.com/questions/15554296/simple-java-aes-encrypt-decrypt-example
 *
 * Usage:
 public static void main(String[] args) {
 String key = "Bar12345Bar12345"; // 128 bit key
 String initVector = "RandomInitVector"; // 16 bytes IV

 System.out.println(decrypt(key, initVector,
 encrypt(key, initVector, "Hello World")));
 }

 */
public class MyEncrypter {

    public static final String TAG = "MyEncrypter";

    public static RsaEncrypter rsaEncrypter = null;

    private static final String initVector = "RandomInitVector"; // 16 bytes IV

    public static SecretKey generateAesKey(String pw) throws NoSuchAlgorithmException {
        SecretKey aesKey;
        KeyGenerator keygen;
        keygen = KeyGenerator.getInstance("AES");
        keygen.init(128);
        aesKey = keygen.generateKey();
        Log.d(TAG, "generated aesKey: " + aesKey.getEncoded().toString());
//        try {
//            byte[] key = (initVector).getBytes("UTF-8"); //(pw+initVector).getBytes
//            MessageDigest sha = MessageDigest.getInstance("SHA-1");
//            key = sha.digest(key);
//            key = Arrays.copyOf(key, 16); // use only first 128 bit
//
//            aesKey = new SecretKeySpec(key, "AES");
//        } catch (Exception e) {
//            return null;
//        }

        return aesKey;
    }


    private static AesEncrypter aesEncrypter = null;

    public static void createRsaEncrypter(Context c) {
//        try {
//            return new RsaEncrypter(ClientPaths.rsaKeyDirectory, 128);
//        } catch (Exception e) {
//            Log.e(TAG, "Could not create rsaEncrypter - trying again");
//            e.printStackTrace();
//        }
//        ClientPaths.writeDataToFile(ClientPaths.PUBLIC_KEY, ClientPaths.rsaKeyFile, false);
//        try {
//            return new RsaEncrypter(ClientPaths.rsaKeyDirectory, 128);
//        } catch (Exception e) {
//            Log.e(TAG, "Could not create rsaEncrypter");
//            e.printStackTrace();
//            return null;
//        }

        try {
            rsaEncrypter = new RsaEncrypter(c,128);
            Log.d(TAG, "Successfully created RSAEncrypter");
        } catch (Exception e) {
            rsaEncrypter = null;
        }

    }

    public static String encryptAes(String pw, String s) {
        if (aesEncrypter == null) {
            try {
//                byte[] ivBytes = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

                IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));


                aesEncrypter = new AesEncrypter(generateAesKey(pw),iv);
            } catch (Exception e) {
                return null;
            }
        }


        byte[] encrypted = aesEncrypter.stringEncrypter(s);
        try {
//            return new String(encrypted, "utf-8");
            return Base64.encodeToString(encrypted,Base64.DEFAULT);
//            return new String(Base64.encode(encrypted, Base64.DEFAULT));


        } catch (Exception e) {
            return null;
        }


    }

    //http://stackoverflow.com/questions/19623367/rsa-encryption-decryption-using-java

    public static String encryptRsa(String s) {
        if (s==null)
            return null;

        if (rsaEncrypter == null) {
            Log.e(TAG, "Attempted to encrypt RSA without initialize!");
        }

        byte[] encrypted = rsaEncrypter.stringEncrypter(s);
        try {
//            return new String(encrypted, "utf-8");
            return Base64.encodeToString(encrypted,Base64.DEFAULT);
//            return new String(Base64.encode(encrypted, Base64.DEFAULT));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static String getAesKey() {
        if (aesEncrypter == null)
            return null;

        byte[] aesKey = aesEncrypter.getKey();
        try {
//            return new String(aesKey, "utf-8");
            return Base64.encodeToString(aesKey, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
//        return new String(Base64.encode(aesKey,Base64.DEFAULT));
    }

    public static String getEncryptedAesKey() {
        if (rsaEncrypter == null)
            return null;
        String aesKey = getAesKey();
        return encryptRsa(aesKey);
    }

//
//    public static String encryptAes(String key, String value) {
//        try {
//            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
//            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
//
//            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
//            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
//
//            byte[] encrypted = cipher.doFinal(value.getBytes());
//            System.out.println("encrypted string: "
//                    + Base64.encodeToString(encrypted,Base64.DEFAULT));
//
//            return new String(Base64.encode(encrypted,Base64.DEFAULT));
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//        return null;
//    }
//
//    public static String decryptAes(String key, String encrypted) {
//        try {
//            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
//            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
//
//            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
//            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
//
//            byte[] original = cipher.doFinal(Base64.decode(encrypted, 0));
//
//            return new String(original);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//        return null;
//    }
}
