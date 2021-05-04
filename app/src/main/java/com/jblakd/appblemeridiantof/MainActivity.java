package com.jblakd.appblemeridiantof;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_DISCOVER_BT = 1;

    TextView textViewAvailabilityBle, textViewPairedBle;
    ImageView imageViewBle;
    Button buttonOnBle, buttonOffBle, buttonDiscoverableBle, buttonGetPairedBle;

    BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewAvailabilityBle  = findViewById(R.id.textViewAvailabilityBle);
        textViewPairedBle        = findViewById(R.id.textViewPairedBle);
        imageViewBle             = findViewById(R.id.imageViewBle);
        buttonOnBle              = findViewById(R.id.buttonOnBle);
        buttonOffBle             = findViewById(R.id.buttonOffBle);
        buttonDiscoverableBle    = findViewById(R.id.buttonDiscoverableBle);
        buttonGetPairedBle       = findViewById(R.id.buttonGetPairedBle);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check whether BLE is available
        if (bluetoothAdapter == null) {
            textViewAvailabilityBle.setText("Bluetooth is not available.");
        } else {
            textViewAvailabilityBle.setText("Bluetooth is available.");
        }

        // Set image according to BLE status (on or off)
        if (bluetoothAdapter.isEnabled()) {
            imageViewBle.setImageResource(R.drawable.ic_action_on);
        } else {
            imageViewBle.setImageResource(R.drawable.ic_action_off);
        }

        // buttonOnBle onClick listener
        buttonOnBle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!bluetoothAdapter.isEnabled()) {
                    showToast("Turning on Bluetooth...");

                    // Intent to turn on Bluetooth
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent, REQUEST_ENABLE_BT);
                } else {
                    showToast("Bluetooth is already on.");
                }
            }
        });

        // buttonOffBle onClick listener
        buttonOffBle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.disable();
                    showToast("Turning Bluetooth off...");
                    imageViewBle.setImageResource(R.drawable.ic_action_off);
                } else {
                    showToast("Bluetooth is already off.");
                }
            }
        });

        // buttonDiscoverableBle onClick listener
        buttonDiscoverableBle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!bluetoothAdapter.isDiscovering()) {
                    showToast("Making your device discoverable...");
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    startActivityForResult(intent, REQUEST_DISCOVER_BT);
                }
            }
        });

        // buttonGetPairedBle onClick listener
        buttonGetPairedBle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothAdapter.isEnabled()) {
                    textViewPairedBle.setText("Paired Devices:");

                    Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();

                    for (BluetoothDevice device: devices) {
                        textViewPairedBle.append("\nDevice: " + device.getName() + ", " + device);
                    }
                } else {
                    // Bluetooth is off and cannot get paired devices
                    showToast("First turn on Bluetooth to get paired devices.");
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    // Bluetooth is on
                    imageViewBle.setImageResource(R.drawable.ic_action_on);
                    showToast("Bluetooth is on.");
                } else {
                    // User not allowed to turn bluetooth on
                    showToast("Bluetooth unable to be turned on.");
                }
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Toast message function
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}