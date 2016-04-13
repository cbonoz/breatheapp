package com.breatheplatform.beta.encryption;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.breatheplatform.beta.R;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by cbono on 3/27/16.
 * http://stackoverflow.com/questions/15554296/simple-java-aes-encrypt-decrypt-example
 * http://blog.tunebrains.com/2015/10/02/android-encrypted-api-with-rails.html
 *
 * Usage:
 public static void main(String[] args) {
 String key = "Bar12345Bar12345"; // 128 bit key
 String initVector = "RandomInitVector"; // 16 bytes IV

 System.out.println(decrypt(key, initVector,
 encrypt(key, initVector, "Hello World")));
 }

 //    String lEncryptedKey = Base64.encode(RSAEncrypt(lAESKey, lAESKey), 0);
 //    String lEncryptedBody = Base64.encode(AESEncrypt(body, lAESKey), 0);
 //    post("http://api-domain.com", lEncryptedBody, lEncryptedKey);

 */
public class MyEncrypter {

    public static final String TAG = "MyEncrypter";

    public static final int AES_KEY_SIZE = 128 / 8;

    public static byte[] lRSAKey = null;
    public static String lAESKey = null;


    public static String randomKey(int len) {
        Random generator = new Random();
        StringBuilder randomStringBuilder = new StringBuilder();
        int randomLength = len;
        char tempChar;
        for (int i = 0; i < randomLength; i++) {
            tempChar = (char) (generator.nextInt(96) + 32);
            randomStringBuilder.append(tempChar);
        }
        return randomStringBuilder.toString();
    }

    public static byte[] AESEncrypt(final String plain, String pKey) throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException {

        SecretKeySpec skeySpec = new SecretKeySpec(pKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(plain.getBytes());
        return encrypted;
    }

    public static String read(InputStream in) {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String read;

        try {
            while ((read = br.readLine()) != null) {
                //System.out.println(read);
                sb.append(read);
            }

            br.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "key file read error");
            return null;
        }
    }


    public static byte[] readKeyWrapped(InputStream pInputStream) throws IOException {
        String lStringKey = read(pInputStream);
        String lRawString = stripKeyHeaders(lStringKey);
        return lRawString.getBytes();//.decode(lRawString, Base64.DEFAULT); //base64(lRawString);
    }

    public static String stripKeyHeaders(String key) {
        StringBuilder strippedKey = new StringBuilder();
        String lines[] = key.split("\n");
        for (String line : lines) {
            if (!line.contains("PRIVATE KEY") && !line.contains("PUBLIC KEY") && !TextUtils.isEmpty(line.trim())) {
                strippedKey.append(line.trim());
            }
        }
        return strippedKey.toString().trim();
    }


    public static byte[] RSAEncrypt(final byte[] plain, byte[] pKey) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeySpecException, InvalidKeyException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(pKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(spec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(plain);
        return encryptedBytes;
    }

    private static byte[] aesKey = null;
    private static final String initVector = "RandomInitVector"; // 16 bytes IV

    public static void createAes() {

        try {
            byte[] keyStart = "this is a key".getBytes();
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            sr.setSeed(keyStart);
            kgen.init(128, sr); // 192 and 256 bits may not be available
            SecretKey skey = kgen.generateKey();
            aesKey = skey.getEncoded();
            Log.d(TAG, "Created AES Key!");
        } catch (Exception e) {
            aesKey = null;
            Log.e(TAG, "Failed to create AES Key");
        }
    }

    public static byte[] getAes() {
        return aesKey;
    }

    public static byte[] encryptRSA(Context mContext, byte[] message) throws Exception {

        // reads the public key stored in a file
        InputStream is = mContext.getResources().openRawResource(R.raw.api_public);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        List<String> lines = new ArrayList<String>();
        String line = null;
        while ((line = br.readLine()) != null)
            lines.add(line);

        // removes the first and last lines of the file (comments)
        if (lines.size() > 1 && lines.get(0).startsWith("-----") && lines.get(lines.size()-1).startsWith("-----")) {
            lines.remove(0);
            lines.remove(lines.size()-1);
        }

        // concats the remaining lines to a single String
        StringBuilder sb = new StringBuilder();
        for (String aLine: lines)
            sb.append(aLine);
        String keyString = sb.toString();
        Log.d("log", "keyString:"+keyString);

        // converts the String to a PublicKey instance

        byte[] keyBytes = Base64.decode(keyString.getBytes("utf-8"), Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey key = keyFactory.generatePublic(spec);

        // decrypts the message
        byte[] encrypted = null;
        Cipher cipher = Cipher.getInstance("RSA");
//        cipher.init(Cipher.DECRYPT_MODE, key);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        encrypted = cipher.doFinal(Base64.decode(message, Base64.DEFAULT));
        return encrypted;
    }

    private static PublicKey getPKfromFile(Context c) throws FileNotFoundException {
        BufferedReader br;
        br = new BufferedReader(new InputStreamReader(c.getResources().openRawResource(R.raw.pkey)));

        List<String> lines = new ArrayList<String>();
        String line = null;
        try {
            while ((line = br.readLine()) != null)
                lines.add(line);
            br.close();
        } catch (IOException e) {
        }

        if (lines.size() > 1) {
            if (lines.get(0).startsWith("-----"))
                lines.remove(0);
            if (lines.get(lines.size() - 1).startsWith("-----")) {
                lines.remove(lines.size() - 1);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String aLine : lines)
            sb.append(aLine);
        String keyString = sb.toString();

        PublicKey pk;

        byte[] keyBytes = null;
        try {
            keyBytes = Base64.decode(keyString.getBytes("utf-8"), 0);
        } catch (UnsupportedEncodingException e1) {}

        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            Log.e("RSAEncrypter","RSA not suported");
        }

        try {
            pk = keyFactory.generatePublic(spec);
        } catch (InvalidKeySpecException e) {
            pk=null;
            Log.e("RSAEncrypter","Invalid Key");
        }
        Log.d("getPk ", "Retrieved key getPkFromFile: " + pk);
        return pk;
    }

    public static byte[] encryptAES(byte[] clear) throws Exception {
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(aesKey, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            return cipher.doFinal(clear);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] decryptAES(byte[] encrypted) throws Exception {
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(aesKey, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            return cipher.doFinal(encrypted);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



//
//
//

//
//
//    public static RsaEncrypter rsaEncrypter = null;
//
//    private static final String initVector = "RandomInitVector"; // 16 bytes IV
//
//    public static String randomKey(String pw) throws NoSuchAlgorithmException {
//        SecretKey aesKey;
//        KeyGenerator keygen;
//        keygen = KeyGenerator.getInstance("AES");
//        keygen.init(128);
//        aesKey = keygen.generateKey();
//        Log.d(TAG, "generated aesKey: " + Base64.encodeToString(aesKey.getEncoded(), Base64.DEFAULT));
////        try {
////            byte[] key = (initVector).getBytes("UTF-8"); //(pw+initVector).getBytes
////            MessageDigest sha = MessageDigest.getInstance("SHA-1");
////            key = sha.digest(key);
////            key = Arrays.copyOf(key, 16); // use only first 128 bit
////
////            aesKey = new SecretKeySpec(key, "AES");
////        } catch (Exception e) {
////            return null;
////        }
//
//        return Base64.encodeToString(aesKey.getEncoded(), Base64.DEFAULT);
//    }
//
//
//    private static AesEncrypter aesEncrypter = null;
//
//    public static void createRsaEncrypter(Context c) {
////        try {
////            return new RsaEncrypter(ClientPaths.rsaKeyDirectory, 128);
////        } catch (Exception e) {
////            Log.e(TAG, "Could not create rsaEncrypter - trying again");
////            e.printStackTrace();
////        }
////        ClientPaths.writeDataToFile(ClientPaths.PUBLIC_KEY, ClientPaths.rsaKeyFile, false);
////        try {
////            return new RsaEncrypter(ClientPaths.rsaKeyDirectory, 128);
////        } catch (Exception e) {
////            Log.e(TAG, "Could not create rsaEncrypter");
////            e.printStackTrace();
////            return null;
////        }
//
//        try {
//            rsaEncrypter = new RsaEncrypter(c,128);
//            Log.d(TAG, "Successfully created RSAEncrypter");
//        } catch (Exception e) {
//            rsaEncrypter = null;
//        }
//
//    }
//
//    public static String encryptAes(String pw, String s) {
//        if (aesEncrypter == null) {
//            try {
////                byte[] ivBytes = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
//
//                IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
//
//
//                aesEncrypter = new AesEncrypter(generateAesKey(pw),iv);
//            } catch (Exception e) {
//                return null;
//            }
//        }
//
//
//        byte[] encrypted = aesEncrypter.stringEncrypter(s);
//        try {
////            return new String(encrypted, "utf-8");
//            return Base64.encodeToString(encrypted,Base64.DEFAULT);
////            return new String(Base64.encode(encrypted, Base64.DEFAULT));
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    //http://stackoverflow.com/questions/19623367/rsa-encryption-decryption-using-java
//
//    public static String encryptRsa(String s) {
//        if (s==null)
//            return null;
//
//        if (rsaEncrypter == null) {
//            Log.e(TAG, "Attempted to encrypt RSA without initialize!");
//        }
//
//        byte[] encrypted = rsaEncrypter.stringEncrypter(s);
//        try {
////            return new String(encrypted, "utf-8");
//            return Base64.encodeToString(encrypted,Base64.DEFAULT);
////            return new String(Base64.encode(encrypted, Base64.DEFAULT));
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//
//    }
//
//    public static String getAesKey() {
//        if (aesEncrypter == null)
//            return null;
//
//        byte[] aesKey = aesEncrypter.getKey();
//        try {
////            return new String(aesKey, "utf-8");
//            return Base64.encodeToString(aesKey, Base64.DEFAULT);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
////        return new String(Base64.encode(aesKey,Base64.DEFAULT));
//    }
//
//    public static String getEncryptedAesKey() {
//        if (rsaEncrypter == null)
//            return null;
//        String aesKey = getAesKey();
//        return encryptRsa(aesKey);
//    }

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
//            return new String(Base64.encode(encrypted, Base64.DEFAULT));
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
