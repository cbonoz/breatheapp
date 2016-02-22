package com.breatheplatform.beta.encryption;

/**
 * Created by cbono on 1/26/16.
 */

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
/**
 * This class provides the functionality of a cryptographic cipher for encryption using AES.
 * @author adkmobile@niclabs.cl
 */
public class AesEncrypter implements com.breatheplatform.beta.encryption.Encrypter {

    private final int iterationCount = 2000;
    private final int salt_pass_Length = 32;
    private final int keyLength = 128;
    private final int buf_size = 256;
    private final int buffered_size = 8192;

    private Cipher cipher = null;
    private byte[] iv;
    private byte[] salt;
    private String password;
    private boolean algorithmnotsupported = false;

    /**
     * Constructor that initializes the cipher with a key generated from the human phrase given by the user
     *
     * @param human_password
     */
    public AesEncrypter(String human_password) {

        SecureRandom random = new SecureRandom();
        salt = new byte[salt_pass_Length];
        random.nextBytes(salt);

        if(human_password==null){
            byte[] aux = new byte[salt_pass_Length/2];
            random.nextBytes(aux);
            password=byteArrayToHexString(aux);
        }
        else{
            password=human_password;
        }

        SecretKeyFactory keyFactory = null;
        byte[] keyBytes = null;
        SecretKey key = null;
        KeySpec keySpec = null;

        keySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount,
                keyLength);
        try {
            keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException e1) {
            algorithmnotsupported = true;
            Log.i("AESEncrypter", "Cant use PBKDF2WithHmacSHA1, will use DES instead");
        } catch (InvalidKeySpecException e1) {
            Log.e("AESEncrypter", "Invalid Key");
        }

        if (algorithmnotsupported) {
            try {
                byte[] bytepass = password.getBytes();
                byte[] aux = new byte[salt_pass_Length];
                for (int i = 0; i < salt_pass_Length; i++) {
                    aux[i] = (byte) (salt[i] ^ bytepass[i % bytepass.length]);
                }
                keySpec = new SecretKeySpec(aux, 0, salt_pass_Length, "AES");
                keyFactory = SecretKeyFactory.getInstance("DES");
                keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
            } catch (NoSuchAlgorithmException e) {
                Log.e("AESEncrypter", "Cant use DES");
            } catch (InvalidKeySpecException e) {
                Log.e("AESEncrypter", "Invalid Key");
            }
        }

        key = new SecretKeySpec(keyBytes, "AES");

        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            iv = new byte[cipher.getBlockSize()];
            random.nextBytes(iv);
            IvParameterSpec ivParams = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
        } catch (Exception e) {
            Log.e("AESEncrypter", "error initializing cipher");
        }
    }

    public String byteArrayToHexString(byte[] array) {
        StringBuffer hexString = new StringBuffer();
        for (byte b : array) {
            int intVal = b & 0xff;
            if (intVal < 0x10)
                hexString.append("0");
            hexString.append(Integer.toHexString(intVal));
        }
        return hexString.toString();
    }

    /**
     * Constructor that initializes the cipher with a key given by the user
     * @param key key used to encrypt
     * @param ivParams a IvParameterSpec instance with the bytes used as initialization vector (if ivParam is null an initialization vector will be randomly generated)
     */
    public AesEncrypter(SecretKey key, IvParameterSpec ivParams) {
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
        } catch (Exception e) {
            Log.e("AESEncrypter", "error initializing cipher");
        }
    }

    /**
     *
     * @return the initialization vector used to encrypt the data
     */
    public String getIV(){
        return byteArrayToHexString(iv);
    }

    /**
     *
     * @return the salt used to randomize the key
     */
    public String getSalt() {
        return byteArrayToHexString(salt);
    }

    /**
     *
     * @return the password given to the constructor or a random password if no password was provided
     */
    public String getPassword() {
        return password;
    }

    /**
     *
     * @return true if PBKDF2WithHmacSHA1 is not supported
     */
    public boolean isWorstcase() {
        return algorithmnotsupported;
    }

    /**
     * Merge an array a with max c bytes from b
     * @param a
     * @param b
     * @param c
     * @return A merged array.
     */
    private byte[] merge(byte[] a, byte[] b, int c) {
        byte[] ret;
        if (a == null && b == null)
            return null;
        if (a == null) {
            ret = new byte[c];
            for (int i = 0; i < c; i++) {
                ret[i] = b[i];
            }
        } else if (b == null) {
            ret = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                ret[i] = a[i];
            }
        } else {
            ret = new byte[a.length + c];
            for (int i = 0; i < a.length; i++) {
                ret[i] = a[i];
            }
            for (int i = a.length; i < a.length + c; i++) {
                ret[i] = b[i - a.length];
            }
        }
        return ret;
    }

    public byte[] stringEncrypter(String plain) {
        if (cipher == null) {
            return null;
        }
        byte[] ret = null;
        try {
            ret = cipher.doFinal(plain.getBytes("UTF-8"));
        } catch (Exception e) {
        }
        return ret;
    }

    public byte[] fileEncrypter(String in) throws IOException {

        byte[] buf = new byte[buf_size];
        byte[] nonencrypted = null;
        byte[] ret = null;

        BufferedInputStream br = null;
        br = new BufferedInputStream(new FileInputStream(in), buf_size);
        int aux;
        while ((aux = br.read(buf, 0, 256)) > 0) {
            nonencrypted = merge(nonencrypted, buf, aux);
        }
        br.close();
        if (nonencrypted == null)
            return null;
        try {
            ret = cipher.doFinal(nonencrypted);
        } catch (IllegalBlockSizeException e) {
        } catch (BadPaddingException e) {
        }
        return ret;
    }

    public void fileEncrypter(String in, String out, boolean append) throws IOException {

        byte[] buf = new byte[buf_size];
        byte[] encrypted = null;

        BufferedInputStream br = null;
        br = new BufferedInputStream(new FileInputStream(in), buffered_size);

        FileOutputStream fw = null;
        fw = new FileOutputStream(out, append);

        while (br.read(buf, 0, buf_size) > 0) {
            encrypted = cipher.update(buf);
            if (encrypted != null)
                fw.write(encrypted);
        }
        try {
            encrypted = cipher.doFinal();
        } catch (IllegalBlockSizeException e) {
        } catch (BadPaddingException e) {
        }
        if (encrypted != null)
            fw.write(encrypted);
        br.close();
        fw.close();
    }
}
