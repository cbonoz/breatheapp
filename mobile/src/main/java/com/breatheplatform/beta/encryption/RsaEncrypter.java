package com.breatheplatform.beta.encryption;

/**
 * Created by cbono on 1/26/16.
 */

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.breatheplatform.beta.R;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;

/**
 * This class provides the functionality of a cryptographic cipher for encryption using RSA.
 * to initialize a Encrypter a .pem file holding the public key must be provided.
 * @author adkmobile@niclabs.cl
 *
 */
public class RsaEncrypter implements Encrypter {

    private PublicKey rsa_pk = null;
    private Cipher cipher = null;
    private int key_length; /* key length in bytes */
    private Context context = null;
    private final int buf_size = 8192; /*default buf value*/

    /**
     * Constructor that initialize the cipher with the public key provided by the user
     * @param key_path Path to the .pem file, in the external storage that holds the public key
     * @param length Number of bits of the public key
     */
    public RsaEncrypter(String key_path, int length) throws FileNotFoundException {
        rsa_pk = getPKfromFile(key_path);
        key_length = length / 8;
        try {
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");// Cipher.getInstance("RSA/ECB/NOPADDING");
            cipher.init(Cipher.ENCRYPT_MODE, rsa_pk);
        } catch (Exception e) {
            Log.e("RSAEncrypter","Failed to initialize cipher");
        }
    }

    public RsaEncrypter(Context c, int length) throws FileNotFoundException {
        rsa_pk = getPKfromFile(c);
        key_length = length / 8;
        try {
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");// Cipher.getInstance("RSA/ECB/NOPADDING");
            cipher.init(Cipher.ENCRYPT_MODE, rsa_pk);
        } catch (Exception e) {
            Log.e("RSAEncrypter","Failed to initialize cipher");
        }
    }



//    /**
//     * Constructor that initialize the cipher with the public key from the application package
//     * @param context The application context
//     */
//    public RsaEncrypter(Context context) {
//        this.context = context;
//        try {
//            rsa_pk = getPKfromResources();
//            rsa_pk = ClientPaths.generateKey();
//        } catch (Exception e1) {
//            Log.e("RSAEncrypter","Failed to get key");
//        }
//        key_length = 256;
//        try {
//            cipher = Cipher.getInstance("RSA/ECB/NOPADDING");
//            cipher.init(Cipher.ENCRYPT_MODE, rsa_pk);
//        } catch (Exception e) {
//            Log.e("RSAEncrypter","Failed to initialize cipher");
//        }
//    }

    /**
     * Get the public key included in the application package
     * @return The public key.
     * @throws IOException
     */
//    private PublicKey getPKfromResources() throws IOException {
//        InputStream is = context.getResources().openRawResource(
//                R.raw.public_key);
//        BufferedReader br = new BufferedReader(new InputStreamReader(is),
//                buf_size);
//
//        List<String> lines = new ArrayList<String>();
//        String line = null;
//        while ((line = br.readLine()) != null)
//            lines.add(line);
//        br.close();
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
//        String keyString = sb.toString();
//
//        PublicKey pk;
//
//        byte[] keyBytes = Base64.decode(keyString.getBytes("utf-8"), 0);
//
//        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
//
//        KeyFactory keyFactory = null;
//
//        try {
//            keyFactory = KeyFactory.getInstance("RSA");
//        } catch (NoSuchAlgorithmException e) {
//            Log.e("RSAEncrypter","RSA not suported");
//        }
//
//        try {
//            pk = keyFactory.generatePublic(spec);
//        } catch (InvalidKeySpecException e) {
//            pk=null;
//            Log.e("RSAEncrypter","Invalid Key");
//        }
//
//        return pk;
//    }

    private PublicKey getPKfromFile(Context c) throws FileNotFoundException {
        context = c;
        BufferedReader br;
        br = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.pkey)));

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


    /**
     * Get a public key from a file in external storage
     * @param key
     * @return PublicKey
     * @throws FileNotFoundException
     */
    private PublicKey getPKfromFile(String key) throws FileNotFoundException {

        BufferedReader br;
        br = new BufferedReader(new FileReader(key), buf_size);

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

    private byte[] rsa(String plain) {
        if (cipher == null)
            return null;
        byte[] ret = null;
        try {
            ret = cipher.doFinal(plain.getBytes("utf-8"));
        } catch (Exception e) {
        }
        return ret;
    }

    public byte[] stringEncrypter(String plain) {
        if (cipher == null)
            return null;
        if (plain.length() <= key_length) {
            return rsa(plain);
        } else {

            ArrayList<byte[]> longencryptedbytes = new ArrayList<byte[]>();
            ArrayList<String> longtext = splitEqually(plain, key_length);
            Iterator<String> its = longtext.iterator();

            byte[] encrypted = null;

            int aux = 0;

            while (its.hasNext()) {
                encrypted = rsa(its.next());
                longencryptedbytes.add(encrypted);
                aux += encrypted.length;
            }

            Iterator<byte[]> itb = longencryptedbytes.iterator();

            byte[] ret = new byte[aux];
            int aux3 = 0;

            while (itb.hasNext()) {
                encrypted = itb.next();
                for (int i = 0; i < encrypted.length; i++) {
                    ret[aux3] = encrypted[i];
                    aux3++;
                }
            }

            return ret;
        }
    }

    public byte[] fileEncrypter(String in) throws IOException {

        char[] buf = new char[key_length];
        byte[] encrypted = null;
        byte[] ret = null;

        BufferedReader br = null;
        br = new BufferedReader(new FileReader(in), buf_size);

        while (br.read(buf, 0, 256) > 0) {
            encrypted = rsa(new String(buf));
            ret = merge(ret,encrypted);
        }
        br.close();
        return ret;
    }

    /**
     * Merge two bytes array into one.
     * @param a
     * @param b
     * @return The new merged byte array
     */

    private byte[] merge(byte[] a, byte[] b) {
        byte[] ret;
        if (a == null && b == null)
            return null;
        if (a == null) {
            ret = new byte[b.length];
            for (int i = 0; i < b.length; i++) {
                ret[i] = b[i];
            }
        } else if (b == null) {
            ret = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                ret[i] = a[i];
            }
        } else {
            ret = new byte[a.length + b.length];
            for (int i = 0; i < a.length; i++) {
                ret[i] = a[i];
            }
            for (int i = a.length; i < a.length + b.length; i++) {
                ret[i] = b[i - a.length];
            }
        }
        return ret;
    }

    public void fileEncrypter(String in, String out, boolean append) throws IOException {

        char[] buf = new char[key_length];
        byte[] encrypted = null;

        BufferedReader br = null;
        br = new BufferedReader(new FileReader(in), buf_size);
        BufferedOutputStream bw = null;
        bw = new BufferedOutputStream(new FileOutputStream(out, append), buf_size);

        while (br.read(buf, 0, 256) > 0) {
            encrypted = rsa(new String(buf));
            if (encrypted != null)
                bw.write(encrypted);
        }
        br.close();
        bw.flush();
        bw.close();
    }

    /**
     * Function to split a String into an array of String of equal size
     * @param text String to split
     * @param size Max size of the new Strings
     * @return An ArrayList containing the split String
     */

    private static ArrayList<String> splitEqually(String text, int size) {
        ArrayList<String> ret = new ArrayList<String>(
                (text.length() + size - 1) / size);
        for (int start = 0; start < text.length(); start += size) {
            ret.add(text.substring(start, Math.min(text.length(), start + size)));
        }
        return ret;
    }
}
