package com.jblakd.appblemeridiantof;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 123;

    private static final int REQUEST_ENABLE_BT = 0;
    private static final long SCAN_PERIOD_MS = 5000;

    private static final int SELECT_DEVICES_VIEWS_CODE = 0;
    private static final int DEVICE_CONNECTED_VIEWS_CODE = 1;
    private static final int LOCATION_NOT_GRANTED_VIEWS_CODE = 2;
    private int currentViewsCode = SELECT_DEVICES_VIEWS_CODE;

    private static final int START_FRAME_TYPE = 0;
    private static final int MIDDLE_FRAME_TYPE = 1;
    private static final int END_FRAME_TYPE = 2;
    private int currentFrameType;

    private static final int IMAGE_WIDTH_PIXELS = 80;
    private static final int IMAGE_HEIGHT_PIXELS = 62;
    private static final int IMAGE_TOTAL_PIXELS = IMAGE_WIDTH_PIXELS * IMAGE_HEIGHT_PIXELS;
    private static final int IMAGE_SCALE_FACTOR = 6;

    private boolean startFrameReceived;
    private static final int TOF_OUT_OF_RANGE = 0;
    private int tofDistance;

    // The range of allowed temperatures. Temperatures outside this range will be saturated.
    private int[] rangeCelsius = new int[] {20, 50};
    // To be calculated later during runtime and stored here
    private int[] rangeTenthKelvin;

    // Array to store each pixel as normalised values between the temperature ranges.
    private float[] imgArray = new float[IMAGE_TOTAL_PIXELS];
    // Array to store each pixel as 32-bit color ints
    private int[] imgArrayColorInts = new int[IMAGE_TOTAL_PIXELS];
    // Arrays to store the pre-rendered 32-bit color ints.
    private int[] rgbColorsArray;
    private Bitmap imgBitmap;

    // Counter for how many 16 bit words i.e. pixels received over bluetooth so far.
    // One complete image should have IMAGE_TOTAL_PIXELS words
    private int wordCount = 0;

    private int testIndex = 200;

    Button buttonMultiPurpose;
    TextView textViewScanStatus;
    TextView textViewDeviceListTitle;
    TextView textViewTofDistanceTitle;
    TextView textViewTofDistance;
    TextView textViewDebug;
    TextView textViewHighestTempTitle;
    TextView textViewHighestTemp;
    ImageView imageViewThermal;
    Canvas canvas;
    Paint paint = new Paint();

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
        // TODO: Scale for all device screen sizes programmatically using percentages
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonMultiPurpose = findViewById(R.id.buttonMultiPurpose);
        listViewBleDevice = findViewById(R.id.BleDeviceList);
        textViewScanStatus = findViewById(R.id.textViewScanStatus);
        textViewDeviceListTitle = findViewById(R.id.textViewDeviceListTitle);
        textViewTofDistanceTitle = findViewById(R.id.textViewTofDistanceTitle);
        textViewTofDistance = findViewById(R.id.textViewTofDistance);
        textViewDebug = findViewById(R.id.textViewDebug);
        textViewHighestTempTitle = findViewById(R.id.textViewHighestTempTitle);
        textViewHighestTemp = findViewById(R.id.textViewHighestTemp);
        imageViewThermal = findViewById(R.id.imageViewThermal);

        rangeTenthKelvin = new int[] {celsiusToTenthKelvin(rangeCelsius[0]), celsiusToTenthKelvin(rangeCelsius[1])};
        // Pre-rendering RGB values into color ints so Color.rgb() doesn't have to be called whenever a pixel needs rendering
        rgbColorsArray = new int[] {Color.rgb(255,2,240), Color.rgb(255,0,208), Color.rgb(255,0,144),
                Color.rgb(255,0,80), Color.rgb(255,0,16), Color.rgb(255,30,0), Color.rgb(255,70,0),
                Color.rgb(255,110,0), Color.rgb(255,150,0), Color.rgb(255,190,0), Color.rgb(255,230,0),
                Color.rgb(215,255,0), Color.rgb(62,255,0), Color.rgb(0,255,92), Color.rgb(0,255,131),
                Color.rgb(0,255,244), Color.rgb(0,180,255), Color.rgb(0,116,255), Color.rgb(0,50,255),
                Color.rgb(0,0,255)};

        // Check Location Permission
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                    LOCATION_REQUEST_CODE);
        }

        toggleViews(SELECT_DEVICES_VIEWS_CODE);

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
                if (listBluetoothDevice.size() == 0) {
                    textViewScanStatus.setText("0 devices found. Please grant location permissions if you haven't done so.");
                } else if (listBluetoothDevice.size() == 1) {
                    textViewScanStatus.setText(listBluetoothDevice.size() + " device found. Try again?");
                } else {
                    textViewScanStatus.setText(listBluetoothDevice.size() + " devices found. Try again?");
                }

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

                        if (listBluetoothDevice.size() == 0) {
                            textViewScanStatus.setText("0 devices found. Please grant location permissions if you haven't done so.");
                        } else if (listBluetoothDevice.size() == 1) {
                            textViewScanStatus.setText(listBluetoothDevice.size() + " device found. Try again?");
                        } else {
                            textViewScanStatus.setText(listBluetoothDevice.size() + " devices found. Try again?");
                        }

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

            // Buffer
            byte[] value = characteristic.getValue();

            // Frame classification
            if (String.format("%x", value[0]).equals("54")) {
                if (String.format("%x", value[1]).equals("7b")) {
                    System.out.println("startFrame");
                    currentFrameType = START_FRAME_TYPE;
                }
            } else if (String.format("%x", value[value.length-1]).equals("44")) {
                if (String.format("%x", value[value.length-2]).equals("7d")) {
                    System.out.println("endFrame");
                    currentFrameType = END_FRAME_TYPE;
                }
            } else {
                middleFrameCount += 1;
                System.out.println("middleFrame " + middleFrameCount);
                textViewDebug.setText("middleFrame (" + middleFrameCount + "/40)");
                currentFrameType = MIDDLE_FRAME_TYPE;
            }

            if (currentFrameType == START_FRAME_TYPE) {
                startFrameReceived = true;
                tofDistance = getInt16(value, 2);

                // Clear variables in both startFrame and endFrame just to be safe
                // In case either the startFrame or endFrame is missed.
                wordCount = 0;
                imgArray = new float[IMAGE_TOTAL_PIXELS];
                middleFrameCount = 0;
            }

            if (currentFrameType == MIDDLE_FRAME_TYPE) {
                // Load data into imgArray
                for (int i = 0; i < value.length - 1; i += 2) {
                    imgArray[wordCount] = normaliseRange(rangeTenthKelvin[0], rangeTenthKelvin[1], getInt16(value, i));
                    wordCount++;
                }
            }

            // Just-before-end code
            if (currentFrameType == END_FRAME_TYPE) {
                // Load data into imgArray
                for (int i = 0; i < value.length - 3; i += 2) {
                    int int16ValueAti = getInt16(value, i);
                    imgArray[wordCount] = normaliseRange(rangeTenthKelvin[0], rangeTenthKelvin[1], int16ValueAti);
                    wordCount++;
                }

                // Draw and update data
                if (middleFrameCount == 40 && wordCount == IMAGE_TOTAL_PIXELS) {
                    // Update and display TOF distance data
                    textViewTofDistance.setText(String.valueOf(tofDistance));

//                    imgArray[testIndex] = (float) 1.0;
//                    testIndex += 10;

                    imgArrayColorInts = imgArrayToColours(imgArray);
                    // Alas, the height is actually the width. Now we need to rotate the image clockwise by 90 degrees.
                    imgBitmap = Bitmap.createBitmap(imgArrayColorInts, IMAGE_WIDTH_PIXELS, IMAGE_HEIGHT_PIXELS, Bitmap.Config.ARGB_8888);

                    imgBitmap = rotateBitmap(imgBitmap, -90);
                    Bitmap mutableBitmap = Bitmap.createScaledBitmap(imgBitmap,
                            IMAGE_HEIGHT_PIXELS*IMAGE_SCALE_FACTOR, IMAGE_WIDTH_PIXELS*IMAGE_SCALE_FACTOR, false);
//                    Bitmap mutableBitmap = imgBitmap.copy(Bitmap.Config.ARGB_8888, true);

                    // Associate the Canvas object with the bitmap so that drawing on the canvas draws on the bitmap
                    canvas = new Canvas(mutableBitmap);

                    paint.setColor(Color.rgb(0,0,0));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setFilterBitmap(false);
//                    int[] testIndexCoordinate = indexToCoordinates(IMAGE_WIDTH_PIXELS, IMAGE_HEIGHT_PIXELS, testIndex);
//                    System.out.println(testIndex + " becomes [" + testIndexCoordinate[0] + ", " + testIndexCoordinate[1] + "]");
//                    canvas.drawCircle(testIndexCoordinate[0], testIndexCoordinate[1], 4, paint);
//                    testIndex += 10;

                    int highestElementIndex = findHighestElementIndex(imgArray);
                    float highestTemperatureCelsius = denormaliseRange(rangeCelsius[0], rangeCelsius[1], imgArray[highestElementIndex]);
                    textViewHighestTemp.setText(String.valueOf(highestTemperatureCelsius) + "°C");
                    int[] highestElementCoordinate = indexToCoordinates(IMAGE_WIDTH_PIXELS, IMAGE_HEIGHT_PIXELS, highestElementIndex);
                    System.out.println(highestElementIndex + " becomes [" + highestElementCoordinate[0] + ", " + highestElementCoordinate[1] + "]");
//                    canvas.drawCircle(highestElementCoordinate[0]*IMAGE_SCALE_FACTOR,
//                            highestElementCoordinate[1]*IMAGE_SCALE_FACTOR, 3*IMAGE_SCALE_FACTOR, paint);
                    drawCrosshair(highestElementCoordinate[0], highestElementCoordinate[1], 1, canvas, paint);
//                    canvas.drawCircle(1,1,4,paint);


                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageViewThermal.setImageBitmap(mutableBitmap);
                        }
                    });

                    System.out.println(wordCount + " wordCount received. " + imgArray.length + " items in imgArray.");
                } else {
                    textViewDebug.setText("Did not receive all frames.");
                }

                // Clear variables in both startFrame and endFrame just to be safe
                // In case either the startFrame or endFrame is missed.
                wordCount = 0;
                imgArray = new float[IMAGE_TOTAL_PIXELS];
                middleFrameCount = 0;
            }
        }
    };

    private void toggleViews(int viewsCode) {
        currentViewsCode = viewsCode;

        if (currentViewsCode == SELECT_DEVICES_VIEWS_CODE) {
            textViewTofDistanceTitle.setVisibility(View.GONE);
            textViewTofDistance.setVisibility(View.GONE);
            textViewDebug.setVisibility(View.GONE);
            imageViewThermal.setVisibility(View.GONE);
            textViewHighestTempTitle.setVisibility(View.GONE);
            textViewHighestTemp.setVisibility(View.GONE);

            listViewBleDevice.setVisibility(View.VISIBLE);
            textViewScanStatus.setVisibility(View.VISIBLE);
            textViewDeviceListTitle.setVisibility(View.VISIBLE);

            buttonMultiPurpose.setText("Start/stop scan");
        } else if (currentViewsCode == DEVICE_CONNECTED_VIEWS_CODE) {
            listViewBleDevice.setVisibility(View.GONE);
            textViewScanStatus.setVisibility(View.GONE);
            textViewDeviceListTitle.setVisibility(View.GONE);

            textViewTofDistanceTitle.setVisibility(View.VISIBLE);
            textViewTofDistance.setVisibility(View.VISIBLE);
            textViewHighestTempTitle.setVisibility(View.VISIBLE);
            textViewHighestTemp.setVisibility(View.VISIBLE);
            textViewDebug.setVisibility(View.VISIBLE);
            imageViewThermal.setVisibility(View.VISIBLE);

            buttonMultiPurpose.setText("Disconnect from N_Meridian");
        } else if (currentViewsCode == LOCATION_NOT_GRANTED_VIEWS_CODE) {
            //Everything should be GONE
            buttonMultiPurpose.setVisibility(View.GONE);
            listViewBleDevice.setVisibility(View.GONE);
            textViewScanStatus.setVisibility(View.GONE);
            textViewDeviceListTitle.setVisibility(View.GONE);
            textViewTofDistanceTitle.setVisibility(View.GONE);
            textViewTofDistance.setVisibility(View.GONE);
            textViewDebug.setVisibility(View.GONE);
            imageViewThermal.setVisibility(View.GONE);
        }
    }

    public int getInt16(byte[] data, int startIndex) {
        int result = 0;
        result = result | ((data[startIndex] & 0xFF) << 8);
        result = result | (data[startIndex + 1] & 0xFF);

        return result;
    }

    public float normaliseRange(int xMin, int xMax, int xVal) {
        float result = ((float) xVal - (float) xMin) / ((float) xMax - (float) xMin);
        return result;
    }

    public float denormaliseRange(float xMin, float xMax, float norm) {
        return norm*(xMax - xMin) + xMin;
    }

    public int celsiusToTenthKelvin(int celsius) {
        return (10*celsius + 2731);
    }

    public float tenthKelvinToCelsius(int tenthKelvin) {
        return (float) (( (float) tenthKelvin / 10) - 273.1);
    }

    public int[] imgArrayToColours(float imgArrayInput[]) {
        if (imgArrayInput.length != IMAGE_TOTAL_PIXELS) {
            System.err.println("imgArrayToColours() error: imgArray not complete.");
            return null;
        }

        int result[] = new int[IMAGE_TOTAL_PIXELS];

        for (int i = 0; i < IMAGE_TOTAL_PIXELS; i++) {
            if (imgArrayInput[i] > 0.95) {
                result[i] = rgbColorsArray[0];
            } else if (imgArrayInput[i] > 0.90) {
                result[i] = rgbColorsArray[1];
            } else if (imgArrayInput[i] > 0.85) {
                result[i] = rgbColorsArray[2];
            } else if (imgArrayInput[i] > 0.80) {
                result[i] = rgbColorsArray[3];
            } else if (imgArrayInput[i] > 0.75) {
                result[i] = rgbColorsArray[4];
            } else if (imgArrayInput[i] > 0.70) {
                result[i] = rgbColorsArray[5];
            } else if (imgArrayInput[i] > 0.65) {
                result[i] = rgbColorsArray[6];
            } else if (imgArrayInput[i] > 0.60) {
                result[i] = rgbColorsArray[7];
            } else if (imgArrayInput[i] > 0.55) {
                result[i] = rgbColorsArray[8];
            } else if (imgArrayInput[i] > 0.50) {
                result[i] = rgbColorsArray[9];
            } else if (imgArrayInput[i] > 0.45) {
                result[i] = rgbColorsArray[10];
            } else if (imgArrayInput[i] > 0.40) {
                result[i] = rgbColorsArray[11];
            } else if (imgArrayInput[i] > 0.35) {
                result[i] = rgbColorsArray[12];
            } else if (imgArrayInput[i] > 0.30) {
                result[i] = rgbColorsArray[13];
            } else if (imgArrayInput[i] > 0.25) {
                result[i] = rgbColorsArray[14];
            } else if (imgArrayInput[i] > 0.20) {
                result[i] = rgbColorsArray[15];
            } else if (imgArrayInput[i] > 0.15) {
                result[i] = rgbColorsArray[16];
            } else if (imgArrayInput[i] > 0.10) {
                result[i] = rgbColorsArray[17];
            } else if (imgArrayInput[i] > 0.05) {
                result[i] = rgbColorsArray[18];
            } else {
                result[i] = rgbColorsArray[19];
            }
        }

        return result;
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    // In this app the thermal image bitmap information is stored in a 1D array
    // This function returns the XY coordinate of a particular pixel given its index in the 1D array
    public int[] indexToCoordinates(int width, int height, int index) {
       int[] result = new int[2];

//       result[0] = index % width; // This is correct for an unrotated bitmap
//       result[1] = index / width; // This is correct for an unrotated bitmap

        result[1] = width - (index % width);    // This is correct for a rotated bitmap
        result[0] = index / width;              // This is correct for a rotated bitmap
       return result;
    }

    public int findHighestElementIndex(float[] array) {
        int result = 0;
        float highest = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > highest) {
                highest = array[i];
                result = i;
            }
        }
        return result;
    }

    public void drawCrosshair(float cx, float cy, float radius, Canvas inputCanvas, Paint inputPaint) {
        float cxScaled = (float) ((cx * IMAGE_SCALE_FACTOR) + (0.5 * IMAGE_SCALE_FACTOR));
        float cyScaled = (float) ((cy * IMAGE_SCALE_FACTOR) - (0.5 * IMAGE_SCALE_FACTOR));
        float radiusScaled = radius * IMAGE_SCALE_FACTOR;

        inputCanvas.drawCircle(cxScaled, cyScaled, radiusScaled, inputPaint);
    }

    // Toast message function
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

// DD:2C:22:52:77:D9 is N_Meridian
// Descriptor: 00002902-0000-1000-8000-00805f9b34fb