package com.dongzi.blelib;

public class BleGattAttributes extends GattAttributes {
    //GATT Characteristics
    public static final String CHARACTERISTIC_CONFIG_CONTROL = "33221111-5544-7766-9988-AABBCCDDEEFF";
    public static final String CHARACTERISTIC_CONFIG_PASSWORD = "33221112-5544-7766-9988-AABBCCDDEEFF";
    public static final String CHARACTERISTIC_CONFIG_STATUS = "33221113-5544-7766-9988-AABBCCDDEEFF";

    static {
        // Characteristics name.
        attributes.put(CHARACTERISTIC_CONFIG_CONTROL, "Config Control");
        attributes.put(CHARACTERISTIC_CONFIG_PASSWORD, "Config Password");
        attributes.put(CHARACTERISTIC_CONFIG_STATUS, "Config Status");
    }
}
