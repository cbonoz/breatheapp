package com.breatheplatform.beta.encryption;

/**
 * Created by cbono on 1/26/16.
 */

import java.io.IOException;
/**
 * Interface of the methods of encryption
 * @author adkmobile@niclabs.cl
 */
public interface Encrypter {

    public byte[] stringEncrypter(String plain);


    public byte[] fileEncrypter(String in) throws IOException;


    public void fileEncrypter(String in, String out, boolean append) throws IOException;

}