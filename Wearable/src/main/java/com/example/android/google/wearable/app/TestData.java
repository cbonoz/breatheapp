package com.example.android.google.wearable.app;

import java.util.Arrays;

/**
 * Created by YooJung on 1/14/2016.
 */
class TestData {
    public String device_id;
    public int fev1;	// 0.01 Litres
    public int pef;	// litres/min
    public int fev1_best;	// 0.01 Litres
    public int pef_best;	// litres/min
    public int fev1_percent;	// %
    public int pef_percent;	// %
    public int green_zone;		// %
    public int yellow_zone;	// %
    public int orange_zone;	// %
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;
    public Boolean good_test;
    public int sw_number;

    // Construct test data from byte array
    public TestData(byte[] message) {
        // Expecting a single test data from asma-1
        // Device Identifier = C, Message Identifier = TD
        if ((char)message[1] == 'C' && (char)message[2] == 'T' && (char)message[3] == 'D') {
            this.device_id = new String(Arrays.copyOfRange(message,4,14));
            this.fev1 = Integer.parseInt(new String(Arrays.copyOfRange(message,14,17)));
            this.pef = Integer.parseInt(new String(Arrays.copyOfRange(message,17,20)));
            this.fev1_best = Integer.parseInt(new String(Arrays.copyOfRange(message,20,23)));
            this.pef_best = Integer.parseInt(new String(Arrays.copyOfRange(message,23,26)));
            this.fev1_percent = Integer.parseInt(new String(Arrays.copyOfRange(message,26,29)));
            this.pef_percent = Integer.parseInt(new String(Arrays.copyOfRange(message,29,32)));
            this.green_zone = Integer.parseInt(new String(Arrays.copyOfRange(message,32,35)));
            this.yellow_zone = Integer.parseInt(new String(Arrays.copyOfRange(message,35,38)));
            this.orange_zone = Integer.parseInt(new String(Arrays.copyOfRange(message,38,41)));
            this.year = Integer.parseInt(new String(Arrays.copyOfRange(message,41,43)));
            this.month = Integer.parseInt(new String(Arrays.copyOfRange(message,43,45)));
            this.day = Integer.parseInt(new String(Arrays.copyOfRange(message,45,47)));
            this.hour = Integer.parseInt(new String(Arrays.copyOfRange(message,47,49)));
            this.minute = Integer.parseInt(new String(Arrays.copyOfRange(message,49,51)));
            this.second = Integer.parseInt(new String(Arrays.copyOfRange(message,51,53)));
            this.good_test = (char)message[53] == '0';
            this.sw_number = Integer.parseInt(new String(Arrays.copyOfRange(message, 54, 57)));
        }
    }

    public float[] toArray() {
//        float[] values = {fev1, pef, fev1_best, pef_best, fev1_percent, pef_percent, green_zone, yellow_zone, orange_zone};
        float[] values = {fev1, pef, good_test ? 1 : 0};
        return values;
    }
}

