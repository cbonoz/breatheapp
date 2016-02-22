package com.breatheplatform.beta.encryption;

/**
 * Created by cbono on 1/26/16.
 */
/**
 * Copyright 2013 NIC Chile Research Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
/**
 * This class provides the functionality of encrypting data using AES and transferring its symmetric key using RSA
 * @author adkmobile@niclabs.cl
 *
 */

public class HybridEncrypter implements Encrypter {

    private RsaEncrypter rsa;
    private AesEncrypter aes;

    private byte[] symmetric_key;
    private boolean algorithmnotsupported = false;

    /**
     * Constructor that initialize the RSA cipher with the public key provided by the user
     * and the AES cipher with a random key based on the password provided
     *
     * @param path_rsa_key Path to the pem file, in the external storage that holds the public key
     * @param key_size Number of bits of the public key
     * @param human_password
     */
    public HybridEncrypter(String path_rsa_key, int key_size, String human_password) throws FileNotFoundException {

        rsa = new RsaEncrypter(path_rsa_key, key_size);

        aes = new AesEncrypter(human_password);

        String salt = aes.getSalt();
        String password=aes.getPassword();
        String iv = aes.getIV();

        String symmetric_key_params = password + "\n" + salt  + "\n" + iv + "\n";
        Log.d("aes key params", symmetric_key_params);
        symmetric_key = rsa.stringEncrypter(symmetric_key_params);

        algorithmnotsupported= aes.isWorstcase();
    }

    /**
     * Constructor that initialize the RSA cipher and the AES cipher with keys provided by the user
     *
     * @param path_rsa_key Path to the pem file, in the external storage that holds the public key
     * @param key_size Number of bits of the public key
     * @param key Key used to encrypt
     * @param ivParams A IvParameterSpec instance with the bytes used as initialization vector (if ivParam is null an initialization vector will be randomly generated)
     */
//    public HybridEncrypter(String path_rsa_key, int key_size, SecretKey key, IvParameterSpec ivParams) throws FileNotFoundException {
//
//        rsa = new RsaEncrypter(path_rsa_key, key_size);
//
//        aes = new AesEncrypter(key, ivParams);
//
//        String salt = aes.getSalt();
//        String password=aes.getPassword();
//        String iv = aes.getIV();
//
//        String symmetric_key_params = password + "\n" + salt  + "\n" + iv + "\n";
//        Log.d("aes key params",symmetric_key_params);
//
//        symmetric_key = rsa.stringEncrypter(symmetric_key_params);
//
//        algorithmnotsupported= aes.isWorstcase();
//    }

    /**
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
     * @return Merged array.
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

    /**
     * Encrypt a String and return a byte array with the encrypted symmetric key using RSAEncrypter and the encrypted data using AESEncrypter
     * @param plain String to encrypt
     * @return The encrypted bytes
     */
    public byte[] stringEncrypter(String plain) {

        byte[] ret;

        byte[] encrypted_data = aes.stringEncrypter(plain);

            ret=merge(symmetric_key, encrypted_data, encrypted_data.length);

        return ret;
    }

    /**
     * Encrypt a file and return a byte array with the encrypted symmetric key using RSAEncrypter and the encrypted data using AESEncrypter
     * @param in Path to file to encrypt
     * @return The encrypted bytes
     */
    public byte[] fileEncrypter(String in) throws IOException {
        byte[] encrypted_data = aes.fileEncrypter(in);
        byte[] ret = merge(symmetric_key, encrypted_data, encrypted_data.length);
        return ret;
    }

    /**
     * Encrypt a file and save in another file writing the encrypted symmetric key using RSAEncrypter and then writing the encrypted data using AESEncrypter
     * @param in Path to the file to encrypt
     * @param out Path to the file where the encrypted data will be saved
     * @param append If true encrypted data will be appended to out, otherwise out will be overwritten
     */
    public void fileEncrypter(String in, String out, boolean append) throws IOException{
        try {
            BufferedOutputStream bw = null;
            bw = new BufferedOutputStream(new FileOutputStream(out, append), 8192);
            bw.write(symmetric_key);
            bw.flush();
            bw.close();

            aes.fileEncrypter(in, out, true);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("HybridEncrypter", "[Handled] Error writing enc file");
            return;
        }
    }
}