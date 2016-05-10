package com.breatheplatform.beta.data;

import java.util.Arrays;

/**
 * Created by YooJung on 1/14/2016.
 */
public class TestData {
    public String device_id;
    public int fev1;	// 0.01 Litres
    public int pef;	// litres/min
    public Boolean good_test;


    // Construct test data from byte array
    public TestData(byte[] message) {
        // Expecting a single test data from asma-1
        // Device Identifier = C, Message Identifier = TD
        if ((char)message[1] == 'C' && (char)message[2] == 'T' && (char)message[3] == 'D') {
//            this.device_id = new String(Arrays.copyOfRange(message,4,14));
            this.fev1 = Integer.parseInt(new String(Arrays.copyOfRange(message,14,17)));
            this.pef = Integer.parseInt(new String(Arrays.copyOfRange(message,17,20)));
            this.good_test = (char)message[53] == '0';
        }
    }

    public float[] toArray() {
//        float[] values = {fev1, pef, fev1_best, pef_best, fev1_percent, pef_percent, green_zone, yellow_zone, orange_zone};
        return new float[]{fev1, pef, good_test ? 1 : 0};

    }

    public float getPef() {
        return pef;
    }
}

//./adb pull /storage/emulated/legacy/SensorData.txt ~/Downloads/Sensordata.txt

