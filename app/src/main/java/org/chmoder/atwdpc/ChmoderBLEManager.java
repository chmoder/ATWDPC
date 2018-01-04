package org.chmoder.atwdpc;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by tcross on 6/5/2017.
 */

public class ChmoderBLEManager<T extends Activity> {
    private final static String TAG = ChmoderBLEManager.class.getSimpleName();
    private Handler mHandler;
    private T activity;
    private BroadcastReceiver mBroadcastReceiver = getBroadcastReceiver();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattCharacteristic mSetupWIFICharacteristic;
    private BluetoothGattCharacteristic mGetSSIDCharacteristic;

    private Boolean isSettingUpWIFI = false;
    private Integer checkSSIDStartTime = null;

    private static final String UUID_WIFI_PROVISIONING_SERVICE = "e6217646-4738-11e7-a919-92ebcb67fe33";
    private static final String UUID_WIFI_PROVISIONING_SERVICE_GET_SSID = "dd22a957-dd9c-4a3d-9bbf-acd5b464168a";
    private static final String UUID_WIFI_PROVISIONING_SERVICE_SETUP_WIFI = "97869662-523f-11e7-b114-b2f933d5fe66";

    ChmoderBLEManager(T activity, Handler mHandler) {
        this.activity = activity;
        this.mHandler = mHandler;
        final BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        this.mBluetoothAdapter = bluetoothManager.getAdapter();

        activity.registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        if(mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        } else {
            mBluetoothAdapter.enable();
        }
    }

    private BroadcastReceiver getBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    switch(state) {
                        case BluetoothAdapter.STATE_OFF:
                            Log.d(TAG, "Enabling Bluetooth");
                            mBluetoothAdapter.enable();
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            Log.d(TAG, "Bluetooth is turning off");
                            break;
                        case BluetoothAdapter.STATE_ON:
                            Log.d(TAG, "finding devices...");
                            findDevices();
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            Log.d(TAG, "Bluetooth is turning on");
                            break;
                    }
                }
            }
        };
    }

    private List<ScanFilter> getScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
        ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
        ScanFilter scanFilter = scanFilterBuilder
                .setServiceUuid(
                        ParcelUuid.fromString(
                                this.lookupUUID("wifiProvisioningService", null)
                        )
                )
                .build();
        scanFilters.add(scanFilter);
        return scanFilters;
    }

    private ScanSettings getScanSettings() {
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        ScanSettings scanSettings = scanSettingsBuilder.build();
        return scanSettings;
    }

    private ScanCallback getScanCallback() {
        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                Log.d(TAG, "found device " + result.getDevice().getAddress());

                BluetoothDevice device = result.getDevice();
                mBluetoothLeScanner.stopScan(this);
                connectToGattServer(device);

            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.w(TAG, "scan failed");
            }
        };
        return scanCallback;
    }

    private void stopScan() {
        if(mBluetoothAdapter == null) {
            return;
        }

        mBluetoothAdapter.getBluetoothLeScanner().stopScan(getScanCallback());
        mBluetoothAdapter = null;
    }

    private void connectToGattServer(BluetoothDevice bluetoothDevice) {
        if(this.mBluetoothGatt == null) {
            this.mBluetoothGatt = bluetoothDevice.connectGatt(activity, false, getConnectToGattServerCallback());
        }
    }

    private void findDevices() {
        mBluetoothLeScanner = this.mBluetoothAdapter.getBluetoothLeScanner();
        List<ScanFilter> scanFilters = getScanFilters();
        ScanSettings scanSettings = getScanSettings();
        ScanCallback scanCallback = getScanCallback();

        if(!mBluetoothAdapter.isDiscovering()) {
            mBluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);
            Log.d(TAG, "scan started");
        }
    }

    private BluetoothGattCallback getConnectToGattServerCallback() {
        BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.");
                    gatt.requestMtu(512);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.");
                    mBluetoothAdapter.disable();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.d(TAG, "service discovered");
                UUID serviceUUID = UUID.fromString(lookupUUID("wifiProvisioningService", null));
                UUID getSSIDCharacteristicUUID = UUID.fromString(lookupUUID("wifiProvisioningService", "getSSID"));
                UUID setupWIFICharacteristicUUID = UUID.fromString(lookupUUID("wifiProvisioningService", "setupWIFI"));
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService wifiProvisioningService = gatt.getService(serviceUUID);
                    if(wifiProvisioningService != null) {
                        mGetSSIDCharacteristic = wifiProvisioningService.getCharacteristic(getSSIDCharacteristicUUID);
                        Log.d(TAG, "getSSID: " + mGetSSIDCharacteristic.getUuid().toString());

                        mSetupWIFICharacteristic = wifiProvisioningService.getCharacteristic(setupWIFICharacteristicUUID);
                        Log.d(TAG, "setupWIFI: " + mSetupWIFICharacteristic.getUuid().toString());

                        mBluetoothGatt.readCharacteristic(mGetSSIDCharacteristic);
                    } else {
                        Log.e(TAG, "Error getting service by UUID");
                    }
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "characteristic read");
                    String characteristicUuid = characteristic.getUuid().toString();

                    if(characteristicUuid.equals(lookupUUID("wifiProvisioningService", "getSSID"))) {
                        String remoteDeviceSSID = characteristic.getStringValue(0);
                        String noSSID = "unknown ssid";
                        Log.d(TAG, remoteDeviceSSID);

                        if((remoteDeviceSSID.contains(noSSID) || remoteDeviceSSID.contains("0.0.0.0")) && !isSettingUpWIFI) {
                            Message message = mHandler.obtainMessage(HandlerConstants.OPEN_CREATE_WIFI_DIALOG, null);
                            message.sendToTarget();
                        } else if((remoteDeviceSSID.contains(noSSID) || remoteDeviceSSID.contains("0.0.0.0")) && isSettingUpWIFI) {
                            Bundle bundle = new Bundle();
                            if(checkSSIDStartTime == null) {
                                checkSSIDStartTime = Calendar.getInstance().get(Calendar.SECOND);
                            }
                            bundle.putInt("checkSSIDStartTime", checkSSIDStartTime);
                            Message message = mHandler.obtainMessage(HandlerConstants.IS_CONNECTING_TO_WIFI, null);
                            message.setData(bundle);
                            message.sendToTarget();
                        } else {
                            isSettingUpWIFI = false;
                            checkSSIDStartTime = null;
                            Bundle bundle = new Bundle();
                            bundle.putString("wifiSSID", remoteDeviceSSID);
                            Message message = mHandler.obtainMessage(HandlerConstants.IS_CONNECTED_TO_WIFI, null);
                            message.setData(bundle);
                            message.sendToTarget();
                            Log.d(TAG, "Connected to " + remoteDeviceSSID);
                        }
                    } else if (characteristicUuid.equals(lookupUUID("wifiProvisioningService", "setupWIFI"))) {
                        Log.d(TAG, "WIFI connected!");
                    }
                } else {
                    Log.d(TAG, "error reading characteristic.  Error code: " + String.valueOf(status));
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                String characteristicUuid = characteristic.getUuid().toString();
                if(characteristicUuid.equals(lookupUUID("wifiProvisioningService", "setupWIFI"))) {
                    Log.d(TAG, "writing char");
                    isSettingUpWIFI = true;
                    sendCommandReadSSIDCharacteristic();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "MTU changed to " + String.valueOf(mtu));
                    gatt.discoverServices();
                } else {
                    Log.w(TAG, "Failed to change MTU");
                }
            }
        };
        return bluetoothGattCallback;
    }

    void close() {
        Log.d(TAG, "cleaning up bluetooth");

        activity.unregisterReceiver(mBroadcastReceiver);

        if (mBluetoothGatt == null) {
            return;
        }

        mBluetoothGatt.close();
        mBluetoothGatt = null;

        stopScan();
    }

    private String lookupUUID(String serviceName, String characteristicName) {
        if(serviceName.equals("wifiProvisioningService")) {
            if(characteristicName == null) {
                return UUID_WIFI_PROVISIONING_SERVICE;
            } else if(characteristicName.equals("getSSID")) {
                return UUID_WIFI_PROVISIONING_SERVICE_GET_SSID;
            } else if(characteristicName.equals("setupWIFI")) {
                return UUID_WIFI_PROVISIONING_SERVICE_SETUP_WIFI;
            } else{
                Log.d(TAG, "unknown characteristic name");
            }
        }
        return null;
    }

    private String toJSON(Map<String, String> jsonData) {
        Gson gson = new Gson();
        return gson.toJson(jsonData);
    }

    private Map<String, String> fromJSON(String jsonString) {
        Type typeOfHashMap = new TypeToken<HashMap<String, String>>() { }.getType();
        Gson gson = new Gson();
        return gson.fromJson(jsonString, typeOfHashMap);
    }

    void sendCommandReadSSIDCharacteristic() {
        mBluetoothGatt.readCharacteristic(mGetSSIDCharacteristic);
    }

    void sendCommandSetupWIFICharacteristic(Map<String, String> data) {
        sendCommandWriteCharacteristic(data, mSetupWIFICharacteristic);
    }

    private void sendCommandWriteCharacteristic(Map<String, String> data, BluetoothGattCharacteristic characteristic) {
        String jsonData = toJSON(data);
        characteristic.setValue(jsonData.getBytes());
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    Map<String, String> newCommand(String commandName) {
        Map<String, String> command = new HashMap<String, String>();
        command.put("name", commandName);
        return command;
    }

    public Map<String, String> newCommand(String commandName, String commandValue) {
        Map<String, String> command = newCommand(commandName);
        command.put("value", commandValue);
        return command;
    }
}
