package com.jblakd.appblemeridiantof;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
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
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final long SCAN_PERIOD_MS = 5000;

    private static final int SELECT_DEVICES_VIEWS_CODE = 0;
    private static final int DEVICE_CONNECTED_VIEWS_CODE = 1;
    private int currentViewsCode = SELECT_DEVICES_VIEWS_CODE;

    Button buttonMultiPurpose;
    TextView textViewScanStatus;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isBluetoothScanning;
    private Handler bluetoothHandler = new Handler();
    BluetoothGatt bluetoothGatt;
    BluetoothGattService bluetoothGattService;
    BluetoothGattCharacteristic bluetoothGattCharacteristic;
    BluetoothGattDescriptor bluetoothGattDescriptor;

    // These lists hold the BLE devices found during scanning and their names
    List<BluetoothDevice> listBluetoothDevice;
    List<String> listBluetoothDeviceName;

    // The array adapter will be used to display the list of devices found during scanning
    ArrayAdapter<String> arrayAdapterBleDevice;
//    ArrayAdapter<BluetoothDevice> arrayAdapterBleDevice;

    // This is the list view in the layout that holds the items
    ListView listViewBleDevice;

    int middleFrameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonMultiPurpose = findViewById(R.id.buttonMultiPurpose);
        listViewBleDevice = findViewById(R.id.BleDeviceList);
        textViewScanStatus = findViewById(R.id.textViewScanStatus);

        // Initialise the BLE adapter
        final BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = null;
        if (bluetoothManager != null)  {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                System.out.println("bluetoothManager.getAdapter() successful.");
            } else {
                System.out.println("bluetoothManager.getAdapter() not successful.");
            }
        }

        // Create arrays to hold BLE info found during scanning
        listBluetoothDevice = new ArrayList<>();
        listBluetoothDeviceName = new ArrayList<>();
        // Create an array adapter and associate it with the list in the layout that displays the values
        arrayAdapterBleDevice = new ArrayAdapter<>(this, R.layout.ble_device_list, R.id.ble_name, listBluetoothDeviceName);
//        arrayAdapterBleDevice = new ArrayAdapter<>(this, R.layout.ble_device_list, R.id.ble_name, listBluetoothDevice);
        listViewBleDevice.setAdapter(arrayAdapterBleDevice);



        //***************************** Multi-Purpose Button *********************************************//
        buttonMultiPurpose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentViewsCode == SELECT_DEVICES_VIEWS_CODE) {
                    if (bluetoothAdapter != null) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                        scanBluetoothDevice();
                    }
                }
                if (currentViewsCode == DEVICE_CONNECTED_VIEWS_CODE) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                    toggleViews(SELECT_DEVICES_VIEWS_CODE);
                }
            }
        });

        //************************** ListView Item Click Listener *****************************************//
        listViewBleDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothLeScanner.stopScan(leScanCallback);
                textViewScanStatus.setText(listBluetoothDevice.size() + " device(s) found. Try again?");
                buttonMultiPurpose.setText("Start/stop scan");
                // Implement more rigorous checks here
                if (!(listBluetoothDevice.get(position).getName().equals("N_Meridian"))) {
                    showToast("Device is not N_Meridian");
                    return;
                }
//                showToast("Connected to: " + listBluetoothDevice.get(position).getName());
                bluetoothGatt = listBluetoothDevice.get(position).connectGatt(getApplicationContext(), false, gattCallback);

                toggleViews(DEVICE_CONNECTED_VIEWS_CODE);
            }
        });
    }

    private void scanBluetoothDevice() {
        System.out.println("scanBluetoothDevice() entered.");
        if (bluetoothLeScanner != null) {
            if (!isBluetoothScanning) {
                // Stop scanning after the defined period
                bluetoothHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isBluetoothScanning = false;
                        bluetoothLeScanner.stopScan(leScanCallback);
                        textViewScanStatus.setText(listBluetoothDevice.size() + " device(s) found. Try again?");
                        if (currentViewsCode == SELECT_DEVICES_VIEWS_CODE) {
                            buttonMultiPurpose.setText("Start/stop scan");
                        }
                    }
                }, SCAN_PERIOD_MS);

                isBluetoothScanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
                textViewScanStatus.setText("Scanning for 5 seconds...");
                buttonMultiPurpose.setText("Start/stop scan");
            } else {
                isBluetoothScanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
                textViewScanStatus.setText(listBluetoothDevice.size() + " device(s) found. Try again?");
                buttonMultiPurpose.setText("Start/stop scan");
            }
        }
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                        if(!listBluetoothDevice.contains(result.getDevice())) {
                            if (result.getDevice().getName() != null) {
                                listBluetoothDevice.add(result.getDevice());
                                listBluetoothDeviceName.add(result.getDevice().getName());
                                arrayAdapterBleDevice.notifyDataSetChanged(); // Update the list on the screen
                            }
                        }
                }
            };

    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                System.out.println("Connected to GATT server.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                System.out.println("Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // services are discoverd
                System.out.println("Discovered GATT services.");

                bluetoothGattService = gatt.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"));
                if (bluetoothGattService != null) {
                    System.out.println("Successfully got service with UUID: " + bluetoothGattService.getUuid().toString());
                } else {
                    System.out.println("Service 6e400001-b5a3-f393-e0a9-e50e24dcca9e not found");
                }

                bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"));
                if (bluetoothGattCharacteristic != null) {
                    System.out.println("Successfully got characteristic with UUID: " + bluetoothGattCharacteristic.getUuid().toString());
                    // Enable local notifications
                    gatt.setCharacteristicNotification(bluetoothGattCharacteristic, true);
                    // Enable remote notifications
                    bluetoothGattDescriptor = bluetoothGattCharacteristic.getDescriptors().get(0);
                    bluetoothGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(bluetoothGattDescriptor);
                } else {
                    System.out.println("Characteristic 6e400003-b5a3-f393-e0a9-e50e24dcca9e not found");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (!(characteristic.getUuid().equals(bluetoothGattCharacteristic.getUuid()))) {
                return;
            }
//            System.out.println("Characteristic " + characteristic.getUuid().toString() + " changed.");
            byte[] value = characteristic.getValue();

            if (String.format("%x", value[0]).equals("54")) {
                if (String.format("%x", value[1]).equals("7b")) {
                    System.out.println("startFrame");
                    middleFrameCount = 0;
                }
            } else if (String.format("%x", value[value.length-1]).equals("44")) {
                if (String.format("%x", value[value.length-2]).equals("7d")) {
                    System.out.println("endFrame");
                }
            } else {
                middleFrameCount += 1;
                System.out.println("middleFrame " + middleFrameCount);
            }
        }
    };

    private void toggleViews(int viewsCode) {
        currentViewsCode = viewsCode;

        if (currentViewsCode == SELECT_DEVICES_VIEWS_CODE) {
            listViewBleDevice.setVisibility(View.VISIBLE);
            textViewScanStatus.setVisibility(View.VISIBLE);
            buttonMultiPurpose.setText("Start/stop scan");
        } else if (currentViewsCode == DEVICE_CONNECTED_VIEWS_CODE) {
            listViewBleDevice.setVisibility(View.GONE);
            textViewScanStatus.setVisibility(View.GONE);
            buttonMultiPurpose.setText("Disconnect from N_Meridian");
        }
    }
    // Toast message function
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

// DD:2C:22:52:77:D9 is N_Meridian
// Descriptor: 00002902-0000-1000-8000-00805f9b34fb