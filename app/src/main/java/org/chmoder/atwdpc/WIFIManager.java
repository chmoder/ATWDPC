package org.chmoder.atwdpc;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * Created by tcross on 5/28/2017.
 */

public class WIFIManager {
    Context mContext;
    WifiManager mainWifi;

    public WIFIManager(Context context) {
        this.mContext = context;
        mainWifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public Boolean isConnected() {
        WifiInfo connectionInfo = mainWifi.getConnectionInfo();
        return !connectionInfo.getSSID().equals("unknown ssid");
    }

    public String getSSID() {
        WifiInfo currentWifi = mainWifi.getConnectionInfo();
        return currentWifi.getSSID().replaceAll("\"", "");
    }
}
