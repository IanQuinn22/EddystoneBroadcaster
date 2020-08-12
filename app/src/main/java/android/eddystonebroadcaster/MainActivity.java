package android.eddystonebroadcaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple app that can advertise Eddystone-UID frames. The namespace and instance parts of the
 * beacon ID are separately configurable, along with the Tx power and frequency.
 */

public class MainActivity extends Activity {
    private static final String TAG = "EddystoneAdvertiser";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int SCAN_PERIOD = 220000;
    private static final byte FRAME_TYPE_UID = 0x00;
    private static final String SHARED_PREFS_NAME = "txeddystone-uid-prefs";
    private static final String PREF_TX_POWER_LEVEL = "tx_power_level";
    private static final String PREF_TX_ADVERTISE_MODE = "tx_advertise_mode";
    private static final String PREF_NAMESPACE = "namespace";
    private static final String PREF_INSTANCE = "instance";
    private static final ParcelUuid SERVICE_UUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private SharedPreferences sharedPreferences;
    private BluetoothAdapter btAdapter;
    private BluetoothLeAdvertiser adv;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private AdvertiseCallback advertiseCallback;
    private int txPowerLevel;
    private int advertiseMode;
    private Switch txSwitch;
    private EditText message;
    private TextView chat;
    private Button listen;
    private Spinner txPower;
    private Spinner txMode;
    private Handler handler = new Handler();
    private HashMap<BluetoothDevice,String> msgMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, 0);
        txPowerLevel = sharedPreferences.getInt(PREF_TX_POWER_LEVEL,
                AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        advertiseMode = sharedPreferences.getInt(PREF_TX_ADVERTISE_MODE,
                AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        init();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                init();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (message != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(PREF_NAMESPACE, message.getText().toString());
            editor.putInt(PREF_TX_POWER_LEVEL, txPowerLevel);
            editor.putInt(PREF_TX_ADVERTISE_MODE, advertiseMode);
            editor.apply();
        }
        if (btAdapter != null && btAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    // Checks if Bluetooth advertising is supported on the device and requests enabling if necessary.
    private void init() {
        BluetoothManager manager = (BluetoothManager) getApplicationContext().getSystemService(
                Context.BLUETOOTH_SERVICE);
        btAdapter = manager.getAdapter();
        if (btAdapter == null) {
            showFinishingAlertDialog("Bluetooth Error", "Bluetooth not detected on device");
        } else if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
        } else if (!btAdapter.isMultipleAdvertisementSupported()) {
            showFinishingAlertDialog("Not supported", "BLE advertising not supported on this device");
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                bluetoothLeScanner = btAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
                filters.add(new ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build());
            }
            adv = btAdapter.getBluetoothLeAdvertiser();
            advertiseCallback = createAdvertiseCallback();
            msgMap = new HashMap<BluetoothDevice, String>();
            buildUi();
        }
    }

    // Pops an AlertDialog that quits the app on OK.
    private void showFinishingAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }

                }).show();
    }

    private AdvertiseCallback createAdvertiseCallback() {
        return new AdvertiseCallback() {

            @Override

            public void onStartFailure(int errorCode) {
                switch (errorCode) {
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        showToastAndLogError("ADVERTISE_FAILED_DATA_TOO_LARGE");
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        showToastAndLogError("ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                        break;
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        showToastAndLogError("ADVERTISE_FAILED_ALREADY_STARTED");
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        showToastAndLogError("ADVERTISE_FAILED_INTERNAL_ERROR");
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        showToastAndLogError("ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                        break;
                    default:
                        showToastAndLogError("startAdvertising failed with unknown error " + errorCode);
                        break;
                }
            }

        };
    }

    private void buildUi() {
        txSwitch = (Switch) findViewById(R.id.txSwitch);
        txSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startAdvertising();
                } else {
                    stopAdvertising();
                }
            }

        });
        message = (EditText) findViewById(R.id.message);
        message.setText("Sample");
        chat = findViewById(R.id.chat);
        txPower = (Spinner) findViewById(R.id.txPower);
        ArrayAdapter<CharSequence> txPowerAdapter = ArrayAdapter.createFromResource(
                this, R.array.tx_power_array, android.R.layout.simple_spinner_dropdown_item);
        txPowerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        txPower.setAdapter(txPowerAdapter);
        setTxPowerSelectionListener();
        txMode = (Spinner) findViewById(R.id.txMode);
        ArrayAdapter<CharSequence> txModeAdapter = ArrayAdapter.createFromResource(
                this, R.array.tx_mode_array, android.R.layout.simple_spinner_dropdown_item);
        txModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        txMode.setAdapter(txModeAdapter);
        setTxModeSelectionListener();
        listen = findViewById(R.id.listen);
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                scanLeDevice(true);
            }
        });
    }

    private void setTxPowerSelectionListener() {
        txPower.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                if (selected.equals(getString(R.string.tx_power_high))) {
                    txPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
                } else if (selected.equals(getString(R.string.tx_power_medium))) {
                    txPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
                } else if (selected.equals(getString(R.string.tx_power_low))) {
                    txPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_LOW;
                } else if (selected.equals(getString(R.string.tx_power_ultra_low))) {
                    txPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
                } else {
                    Log.e(TAG, "Unknown Tx power " + selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // NOP
            }
        });
    }

    private void setTxModeSelectionListener() {
        txMode.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                if (selected.equals(getString(R.string.tx_mode_low_latency))) {
                    advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
                } else if (selected.equals(getString(R.string.tx_mode_balanced))) {
                    advertiseMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
                } else if (selected.equals(getString(R.string.tx_mode_low_power))) {
                    advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
                } else {
                    Log.e(TAG, "Unknown Tx mode " + selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // NOP
            }
        });
    }

    private void startAdvertising() {
        Log.i(TAG, "Starting ADV, Tx power = " + txPower.getSelectedItem()
                + ", mode = " + txMode.getSelectedItem());

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(advertiseMode)
                .setTxPowerLevel(txPowerLevel)
                .setConnectable(true)
                .build();

        byte[] serviceData = null;
        String msg = message.getText().toString();
        serviceData = msg.getBytes();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .addServiceData(SERVICE_UUID, serviceData)
                .addServiceUuid(SERVICE_UUID)
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)
                .build();

        message.setError(null);
        setEnabledViews(false, message, txPower, txMode);
        adv.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
    }

    private void stopAdvertising() {
        Log.i(TAG, "Stopping ADV");
        adv.stopAdvertising(advertiseCallback);
        setEnabledViews(true, message, txPower, txMode);
    }

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.d("k","l");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);
            bluetoothLeScanner.startScan(filters,settings,leScanCallback);
        } else {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            Map<ParcelUuid,byte[]> resultMap = result.getScanRecord().getServiceData();
            String rMsg = new String(resultMap.get(SERVICE_UUID));
            BluetoothDevice device = result.getDevice();
            updateChat(device,rMsg);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private void setEnabledViews(boolean enabled, View... views) {
        for (View v : views) {
            v.setEnabled(enabled);
        }
    }

    private void updateChat(BluetoothDevice device, String message){
        if (!msgMap.containsKey(device)){
            chat.append("\n" + device.getAddress() + ": " + message);
            msgMap.put(device,message);
        }
        else if (!(msgMap.get(device).equals(message))){
            chat.append("\n" + device.getAddress() + ": " + message);
            msgMap.put(device,message);
        }
    }

    // Converts the current Tx power level value to the byte value for that power
    // in dBm at 0 meters. This is unused, but may be used in future applications.
    //
    private byte txPowerLevelToByteValue() {
        switch (txPowerLevel) {
            case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
                return (byte) -16;
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                return (byte) -26;
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                return (byte) -35;
            default:
                return (byte) -59;
        }

    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showToastAndLogError(String message) {
        showToast(message);
        Log.e(TAG, message);
    }

}