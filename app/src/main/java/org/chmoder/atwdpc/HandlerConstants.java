package org.chmoder.atwdpc;

/**
 * Created by tcross on 6/3/2017.
 */

public interface HandlerConstants {

    // Message types sent from the BluetoothChatService Handler
    public static final int OPEN_CREATE_WIFI_DIALOG = 1;
    public static final int IS_CONNECTING_TO_WIFI = 2;
    public static final int IS_CONNECTED_TO_WIFI = 3;
    public static final int MESSAGE_CONNECTED = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

}
