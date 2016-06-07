package com.breatheplatform.beta.sensors;

import android.hardware.Sensor;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.breatheplatform.beta.ClientPaths;
import com.breatheplatform.beta.shared.Constants;


//SensorNames class
//Provides translations for Android sensor ID's to our server IDs
//Also stores String value mappings for the sensor names

public class SensorNames {
    public SparseArray<String> names;
    public SparseIntArray serverSensors;

    public SensorNames() {
        names = new SparseArray<String>();
        serverSensors = new SparseIntArray ();

        names.append(0, "Debug Sensor");
//        names.append(Sensor.TYPE_ACCELEROMETER, "Accelerometer");
//        names.append(Sensor.TYPE_AMBIENT_TEMPERATURE, "Ambient temperatur");
//        names.append(Sensor.TYPE_GAME_ROTATION_VECTOR, "Game Rotation Vector");
//        names.append(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "Geomagnetic Rotation Vector");
//        names.append(Sensor.TYPE_GRAVITY, "Gravity");
//
//        names.append(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, "Gyroscope (Uncalibrated)");
//        names.append(Sensor.TYPE_HEART_RATE, "Heart Rate");
//        names.append(Sensor.TYPE_LIGHT, "Light");
//
//        names.append(Sensor.TYPE_MAGNETIC_FIELD, "Magnetic Field");
//        names.append(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, "Magnetic Field (Uncalibrated)");
//        names.append(Sensor.TYPE_PRESSURE, "Pressure");
//        names.append(Sensor.TYPE_PROXIMITY, "Proximity");
//        names.append(Sensor.TYPE_RELATIVE_HUMIDITY, "Relative Humidity");
//        names.append(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector");
//        names.append(Sensor.TYPE_SIGNIFICANT_MOTION, "Significant Motion");
//        names.append(Sensor.TYPE_STEP_COUNTER, "Step Counter");
//        names.append(Sensor.TYPE_STEP_DETECTOR, "Step Detector");
//        names.append(65545, "PPG");

        names.append(Sensor.TYPE_LINEAR_ACCELERATION, "Linear Acceleration");
        names.append(Sensor.TYPE_GYROSCOPE, "Gyroscope");
        names.append(ClientPaths.SS_HEART_SENSOR_ID, "Ss Heart Rate");
        names.append(Constants.DUST_SENSOR_ID, "Dust Sensor");
        names.append(ClientPaths.HEART_SENSOR_ID, "Heart Rate");
        names.append(Constants.SPIRO_SENSOR_ID, "Spirometer");
        names.append(Constants.ENERGY_SENSOR_ID, "Energy");
        names.append(Constants.ACTIVITY_SENSOR_ID, "Activity");
        names.append(Constants.AIRBEAM_SENSOR_ID, "AirBeam Sensor");

        serverSensors.append(Sensor.TYPE_LINEAR_ACCELERATION, 1);
        serverSensors.append(Sensor.TYPE_HEART_RATE, 2);
        serverSensors.append(Constants.DUST_SENSOR_ID, 3);
        serverSensors.append(Constants.SPIRO_SENSOR_ID, 4);
        serverSensors.append(Constants.ENERGY_SENSOR_ID, 5);
        serverSensors.append(Sensor.TYPE_GYROSCOPE, 6);
        serverSensors.append(Constants.ACTIVITY_SENSOR_ID, 7);
        serverSensors.append(Constants.AIRBEAM_SENSOR_ID, 8);

    }

    //method for mapping sensorId to sensorName string
    public String getName(int sensorId) {
        String name = names.get(sensorId);

        if (name == null) {
            Log.d("sensornames", "getName for unknown id " + sensorId);
            return "Unknown";
        }

        return name;
    }

    //method for mapping android ID to server ID
    public Integer getServerID(int sensorId) {
        return serverSensors.get(sensorId);
    }
}