package com.jblakd.appblemeridiantof;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final long SCAN_PERIOD_MS = 5000;

    Button buttonStartBleScan;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isBluetoothScanning;
    private Handler bluetoothHandler = new Handler();

    // These lists hold the BLE devices found during scanning and their names
    List<BluetoothDevice> listBluetoothDevice;
    List<String> listBluetoothDeviceName;

    // The array adapter will be used to display the list of devices found during scanning
    ArrayAdapter<String> arrayAdapterBleDevice;
//    ArrayAdapter<BluetoothDevice> arrayAdapterBleDevice;

    // This is the list view in the layout that holds the items
    ListView listViewBleDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonStartBleScan = findViewById(R.id.buttonStartBleScan);
        listViewBleDevice = findViewById(R.id.BleDeviceList);

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



        //***************************** Start BLE Scan Button *********************************************//
        buttonStartBleScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothAdapter != null) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    scanBluetoothDevice();
                }
                showToast("Scanning for devices...");
            }
        });

        listViewBleDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                System.out.println(listBluetoothDevice.get(position).getName() + " - " + listBluetoothDevice.get(position).getAddress());
                showToast(listBluetoothDevice.get(position).getName() + " - " + listBluetoothDevice.get(position).getAddress());
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
                    }
                }, SCAN_PERIOD_MS);

                isBluetoothScanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
                System.out.println("Scanning started.");
            } else {
                isBluetoothScanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
                System.out.println("Scanning stopped.");
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
    // Toast message function
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

// DD:2C:22:52:77:D9 is N_Meridian
