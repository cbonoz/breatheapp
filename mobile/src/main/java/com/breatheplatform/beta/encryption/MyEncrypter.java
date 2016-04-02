package com.breatheplatform.beta.encryption;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
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
        StringBuilder sb=new StringBuilder();
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
        byte []encryptedBytes = cipher.doFinal(plain);
        return encryptedBytes;
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
