package com.breatheplatform.beta.encryption;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.breatheplatform.beta.R;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by cbono on 4/12/16.
 * https://github.com/scottyab/AESCrypt-Android/blob/master/aescrypt/src/main/java/com/scottyab/aescrypt/AESCrypt.java
 * http://peetahzee.com/2015/02/securing-data-and-communication-with-rsa-across-android-app-and-python-server/
 */
public final class HybridCrypt {

    private static final String TAG = "HybridCrypt";

    //HybridCrypt-ObjC uses CBC and PKCS7Padding
    private static final String AES_MODE = "AES/CBC/PKCS7Padding";
    private static final String CHARSET = "UTF-8";

    //HybridCrypt-ObjC uses SHA-256 (and so a 256-bit aesKey) - we clip to 128 later
    private static final String HASH_ALGORITHM = "SHA-256";

    //HybridCrypt-ObjC uses blank IV (not the best security, but the aim here is compatibility)
    private static final byte[] ivBytes = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    //togglable log option (please turn off in live!)
    public static boolean DEBUG_LOG_ENABLED = false;

    private String password;
    private SecretKeySpec aesKey;
    private PublicKey publicKey;

    /**
     * Generates SHA256 hash of the password which is used as aesKey
     *
     * @param password used to generated aesKey
     * @return SHA256 of the password
     */
    private SecretKeySpec generateKey(final String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] bytes = password.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();

        log("SHA-256 aesKey ", key);

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        return secretKeySpec;
    }

    public HybridCrypt(Context c, String pw) {
        password = pw;
        try {
            aesKey = generateKey(password);

        } catch (Exception e) {
            e.printStackTrace();
            aesKey = null;
        }
//
//        try {
//            publicKey = getPKfromFile(c);
//        } catch (Exception e) {
//            e.printStackTrace();
//            publicKey = null;
//        }
        publicKey = loadPublicKey(c);
    }

    public String getKeyString() {
        return Base64.encodeToString(aesKey.getEncoded(), Base64.DEFAULT);
    }

    /**
     * Encrypt and encode message using 256-bit AES with aesKey generated from password.
     *
     *
     *
     * @param message the thing you want to encrypt assumed String UTF-8
     * @return Base64 encoded CipherText
     * @throws GeneralSecurityException if problems occur during encryption
     */
    public String encrypt(String message)
            throws GeneralSecurityException {

        try {
            log("message", message);

            byte[] cipherText = encrypt(aesKey, ivBytes, message.getBytes(CHARSET));

            //NO_WRAP is important as was getting \n at the end
            String encoded = Base64.encodeToString(cipherText, Base64.NO_WRAP);
            log("Base64.NO_WRAP", encoded);
            return encoded;
        } catch (UnsupportedEncodingException e) {
            if (DEBUG_LOG_ENABLED)
                Log.e(TAG, "UnsupportedEncodingException ", e);
            throw new GeneralSecurityException(e);
        }
    }


    /**
     * More flexible AES encrypt that doesn't encode
     * @param key AES aesKey typically 128, 192 or 256 bit
     * @param iv Initiation Vector
     * @param message in bytes (assumed it's already been decoded)
     * @return Encrypted cipher text (not encoded)
     * @throws GeneralSecurityException if something goes wrong during encryption
     */
    public static byte[] encrypt(final SecretKeySpec key, final byte[] iv, final byte[] message)
            throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance(AES_MODE);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] cipherText = cipher.doFinal(message);

        log("cipherText", cipherText);

        return cipherText;
    }


    /**
     * Decrypt and decode ciphertext using 256-bit AES with aesKey generated from password
     *

     * @param base64EncodedCipherText the encrpyted message encoded with base64
     * @return message in Plain text (String UTF-8)
     * @throws GeneralSecurityException if there's an issue decrypting
     */
    public String decrypt(String base64EncodedCipherText)
            throws GeneralSecurityException {

        try {
            final SecretKeySpec key = generateKey(password);

            log("base64EncodedCipherText", base64EncodedCipherText);
            byte[] decodedCipherText = Base64.decode(base64EncodedCipherText, Base64.NO_WRAP);
            log("decodedCipherText", decodedCipherText);

            byte[] decryptedBytes = decrypt(key, ivBytes, decodedCipherText);

            log("decryptedBytes", decryptedBytes);
            String message = new String(decryptedBytes, CHARSET);
            log("message", message);


            return message;
        } catch (UnsupportedEncodingException e) {
            if (DEBUG_LOG_ENABLED)
                Log.e(TAG, "UnsupportedEncodingException ", e);

            throw new GeneralSecurityException(e);
        }
    }


    /**
     * More flexible AES decrypt that doesn't encode
     *
     * @param key AES aesKey typically 128, 192 or 256 bit
     * @param iv Initiation Vector
     * @param decodedCipherText in bytes (assumed it's already been decoded)
     * @return Decrypted message cipher text (not encoded)
     * @throws GeneralSecurityException if something goes wrong during encryption
     */
    public static byte[] decrypt(final SecretKeySpec key, final byte[] iv, final byte[] decodedCipherText)
            throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance(AES_MODE);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] decryptedBytes = cipher.doFinal(decodedCipherText);

        log("decryptedBytes", decryptedBytes);

        return decryptedBytes;
    }

    private static void log(String what, byte[] bytes) {
        if (DEBUG_LOG_ENABLED)
            Log.d(TAG, what + "[" + bytes.length + "] [" + bytesToHex(bytes) + "]");
    }

    private static void log(String what, String value) {
        if (DEBUG_LOG_ENABLED)
            Log.d(TAG, what + "[" + value.length() + "] [" + value + "]");
    }

    /**
     * Converts byte array to hexidecimal useful for logging and fault finding
     * @param bytes
     * @return
     */
    private static String bytesToHex(byte[] bytes) {
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    //BEGIN RSA

//    http://stackoverflow.com/questions/9658921/encrypting-aes-key-with-rsa-public-key

    public String encryptRSA(String s)  {
        try {
            Cipher c = Cipher.getInstance("RSA");
            c.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] bytes = c.doFinal(s.getBytes());
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getEncryptedKey() {
        Cipher cipher = null;
        try {
            // initialize the cipher with the user's public Key
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.encodeToString(cipher.doFinal(aesKey.getEncoded()),Base64.DEFAULT);
        }
        catch(Exception e )
        {
            System.out.println("exception encoding aesKey: " + e.getMessage());
            e.printStackTrace();

        }
        return null;
    }

    private PublicKey getPKfromFile(Context c) throws FileNotFoundException {
        BufferedReader br;
        br = new BufferedReader(new InputStreamReader(c.getResources().openRawResource(R.raw.public_key)));

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
        Log.d("getPk ", "Retrieved aesKey getPkFromFile: " + pk);
        return pk;
    }


    private PublicKey loadPublicKey(Context c) {

//        BufferedReader br;
//        br = new BufferedReader(new InputStreamReader(c.getResources().openRawResource(R.raw.pkey)));
//
//        List<String> lines = new ArrayList<String>();
//        String line = null;
//        try {
//            while ((line = br.readLine()) != null)
//                lines.add(line);
//            br.close();
//        } catch (IOException e) {
//        }
//
//        if (lines.size() > 1) {
//            if (lines.get(0).startsWith("-----"))
//                lines.remove(0);
//            if (lines.get(lines.size() - 1).startsWith("-----")) {
//                lines.remove(lines.size() - 1);
//            }
//        }
//
//        StringBuilder sb = new StringBuilder();
//        for (String aLine : lines)
//            sb.append(aLine);
//        byte[] keyBytes = sb.toString().getBytes();

//        PublicKey pk;




        // This line is a little mysterious, particularly what the heck is a X509EncodedKey?!
        // It seems that X509 is the default format that openssl outputs in.
        try {
            // Gets the file public_key
            InputStream is = c.getResources().openRawResource(R.raw.public_key);

            // create a new byte array where we're going to store the binary data to
            byte[] keyBytes = new byte[is.available()];

            // Read from the file
            is.read(keyBytes);
            is.close();

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

            // With the aesKey, generate a PublicKey object
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            e.printStackTrace();
            return null;

        }

    }




}