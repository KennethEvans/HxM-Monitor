package net.kenevans.android.hxmmonitor;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code HxMBleService}, which in turn
 * interacts with the Bluetooth LE API.
 */
public class DeviceMonitorActivity extends AppCompatActivity implements IConstants {
    private TextView mConnectionState;
    private TextView mBat;
    private TextView mHr;
    private TextView mRr;
    private TextView mAct;
    private TextView mPa;
    private TextView mStatus;
    private String mDeviceName;
    private String mDeviceAddress;
    private HxMBleService mHxMBleService;
    private boolean mConnected = false;
    private HxMMonitorDbAdapter mDbAdapter;
    private boolean mDoBat = true;
    private boolean mDoHr = true;
    private boolean mDoCustom = true;
    private BluetoothGattCharacteristic mCharBat;
    private BluetoothGattCharacteristic mCharHr;
    private BluetoothGattCharacteristic mCharCustom;
    private CancelableCountDownTimer mTimer;

    /**
     * Manages the service lifecycle.
     */
    private final ServiceConnection mServiceConnection = new
            ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName,
                                               IBinder service) {
                    Log.d(TAG, "onServiceConnected: " + mDeviceName + " "
                            + mDeviceAddress);
                    mHxMBleService =
                            ((HxMBleService.LocalBinder) service).getService();
                    if (!mHxMBleService.initialize()) {
                        String msg = "Unable to initialize Bluetooth";
                        Log.e(TAG, msg);
                        Utils.errMsg(DeviceMonitorActivity.this, msg);
                        return;
                    }
                    if (mDbAdapter != null) {
                        mHxMBleService.startDatabase(mDbAdapter);
                    }
                    // Automatically connects to the device upon successful
                    // start-up
                    // initialization.
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(DeviceMonitorActivity.this);
                    boolean manuallyDisconnected = prefs.getBoolean(
                            PREF_MANUALLY_DISCONNECTED, false);
                    if (!manuallyDisconnected) {
                        boolean res = mHxMBleService.connect(mDeviceAddress);
                        Log.d(TAG, "Connect mHxMBleService result=" + res);
                        if (res) {
                            setManuallyDisconnected(false);
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.d(TAG, "onServiceDisconnected");
                    mHxMBleService = null;
                }
            };

    /**
     * Handles various events fired by the Service.
     *
     * <br>
     * <br>
     * ACTION_GATT_CONNECTED: connected to a GATT server.<br>
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.<br>
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.<br>
     * ACTION_DATA_AVAILABLE: received data from the device. This can be a
     * result of read or notification operations.<br>
     */
    private final BroadcastReceiver mGattUpdateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (HxMBleService.ACTION_GATT_CONNECTED.equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        mConnected = true;
                        updateConnectionState(R.string.connected);
                        invalidateOptionsMenu();
                    } else if (HxMBleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        mConnected = false;
                        resetDataViews();
                        updateConnectionState(R.string.disconnected);
                        invalidateOptionsMenu();
                    } else if (HxMBleService.ACTION_GATT_SERVICES_DISCOVERED
                            .equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        onServicesDiscovered(mHxMBleService.getSupportedGattServices());
                    } else if (HxMBleService.ACTION_DATA_AVAILABLE.equals(action)) {
                        // Log.d(TAG, "onReceive: " + action);
                        displayData(intent);
                    } else if (HxMBleService.ACTION_ERROR.equals(action)) {
                        // Log.d(TAG, "onReceive: " + action);
                        displayError(intent);
                    }
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

        setContentView(R.layout.activity_device_monitor);

        // Initialize the preferences if not already done
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Use this check to determine whether BLE is supported on the device.
        // Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Initializes a Bluetooth adapter. For API level 18 and above, get a
        // reference to BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            String msg = getString(R.string.error_bluetooth_manager);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        BluetoothAdapter adapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device
        if (adapter == null) {
            String msg = getString(R.string.bluetooth_not_supported);
            Utils.errMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Use this instead of getPreferences to be application-wide
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mDeviceName = prefs.getString(DEVICE_NAME_CODE, null);
        mDeviceAddress = prefs.getString(DEVICE_ADDRESS_CODE, null);
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: "
                + mDeviceName + " " + mDeviceAddress);

        ((TextView) findViewById(R.id.device_name)).setText(mDeviceName);
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);
        mBat = findViewById(R.id.bat_value);
        mHr = findViewById(R.id.hr_value);
        mRr = findViewById(R.id.rr_value);
        mStatus = findViewById(R.id.status_value);
        resetDataViews();

        Intent gattServiceIntent = new Intent(this, HxMBleService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Open the database
        mDbAdapter = new HxMMonitorDbAdapter(this);
        mDbAdapter.open();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume: mConnected="
                + mConnected + " mHxMBleService="
                + (mHxMBleService == null ? "null" : "not null"));
        super.onResume();
        // Get the settings
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mDoHr = prefs.getBoolean(PREF_MONITOR_HR, true);
        mDoCustom = prefs.getBoolean(PREF_MONITOR_CUSTOM, true);
        mDoBat = prefs.getBoolean(PREF_READ_BATTERY, true);
        boolean manuallyDisconnected = prefs.getBoolean(
                PREF_MANUALLY_DISCONNECTED, false);
        // DEBUG
        Log.d(TAG, "  mDoHr=" + mDoHr + " mDoCustom=" + mDoCustom + " mDoBat="
                + mDoBat);
        Log.d(TAG, "Starting registerReceiver");
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (!manuallyDisconnected && mDeviceAddress != null
                && mHxMBleService != null) {
            Log.d(TAG, "Starting mHxMBleService.connect");
            final boolean res = mHxMBleService.connect(mDeviceAddress);
            Log.d(TAG, "mHxMBleService.connect: result=" + res);
            if (res) {
                setManuallyDisconnected(false);
            }
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
        super.onDestroy();
        unbindService(mServiceConnection);
        // Stop the service
        Intent intent = new Intent(this, HxMBleService.class);
        mHxMBleService.stopService(intent);
        mHxMBleService = null;
        if (mDbAdapter != null) {
            mDbAdapter.close();
            mDbAdapter = null;
        }
    }

    @Override
    public void onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.getClass().getSimpleName() + ": onBackPressed");
        finish();
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_monitor, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_connect) {
            mHxMBleService.connect(mDeviceAddress);
            setManuallyDisconnected(false);
            return true;
        } else if (item.getItemId() == R.id.menu_disconnect) {
            mHxMBleService.disconnect();
            setManuallyDisconnected(true);
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.menu_select_device) {
            selectDevice();
            return true;
        } else if (item.getItemId() == R.id.menu_session_manager) {
            startSessionManager();
            return true;
        } else if (item.getItemId() == R.id.menu_plot) {
            plot();
            return true;
        } else if (item.getItemId() == R.id.menu_read_battery_level) {
            readBatteryLevel();
            return true;
        } else if (item.getItemId() == R.id.info) {
            info();
            return true;
        } else if (item.getItemId() == R.id.menu_save_database) {
            saveDatabase();
            return true;
        } else if (item.getItemId() == R.id.menu_replace_database) {
            checkReplaceDatabase();
            return true;
        } else if (item.getItemId() == R.id.choose_data_directory) {
            chooseDataDirectory();
            return true;
//        } else if (item.getItemId() == R.id.help) {
//            showHelp();
//            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            showSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent
            intent) {
        if (requestCode == REQ_GET_TREE && resultCode == RESULT_OK) {
            Uri treeUri;
            // Get Uri from Storage Access Framework.
            treeUri = intent.getData();
            // Keep them from accumulating
            net.kenevans.android.hxmmonitor.UriUtils.releaseAllPermissions(this);
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(this).edit();
            if (treeUri != null) {
                editor.putString(PREF_TREE_URI, treeUri.toString());
            } else {
                editor.putString(PREF_TREE_URI, null);
            }
            editor.apply();

            // Persist access permissions.
            if (treeUri != null) {
                this.getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else {
                Utils.errMsg(this, "Failed to get presistent access " +
                        "permissions");
            }
        } else if (requestCode == REQ_SELECT_DEVICE_CODE && resultCode == RESULT_OK) {
            mDeviceName = intent.getStringExtra(DEVICE_NAME_CODE);
            mDeviceAddress = intent.getStringExtra(DEVICE_ADDRESS_CODE);
            // Use this instead of getPreferences to be application-wide
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(this).edit();
            editor.putString(DEVICE_NAME_CODE, mDeviceName);
            editor.putString(DEVICE_ADDRESS_CODE, mDeviceAddress);
            editor.apply();
            ((TextView) findViewById(R.id.device_name))
                    .setText(mDeviceName);
            ((TextView) findViewById(R.id.device_address))
                    .setText(mDeviceAddress);
        } else if (requestCode == REQ_SETTINGS_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "onActivityResult: REQUEST_SETTINGS_CODE resultCode="
                    + resultCode);
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    /**
     * Sets the current data directory
     */
    private void chooseDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION &
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
				        startActivityForResult(intent, REQ_GET_TREE);
    }

    /**
     * Updates the connection state view on the UI thread.
     *
     * @param resourceId The resource ID.
     */
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(() -> mConnectionState.setText(resourceId));
    }

    /**
     * Calls an activity to select the device.
     */
    public void selectDevice() {
        // Scan doesn't find current device if it is connected
        if (mConnected) {
            // Confirm the user wants to scan even if the current device is
            // connected
            new AlertDialog.Builder(DeviceMonitorActivity.this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.confirm)
                    .setMessage(R.string.scan_prompt)
                    .setPositiveButton(R.string.ok,
                            (dialog, which) -> {
                                Intent intent = new Intent(
                                        DeviceMonitorActivity.this,
                                        DeviceScanActivity.class);
                                startActivity(intent);
                            }).setNegativeButton(R.string.cancel, null).show();
        } else {
            Intent intent = new Intent(DeviceMonitorActivity.this,
                    DeviceScanActivity.class);
            startActivityForResult(intent, REQ_SELECT_DEVICE_CODE);
        }
    }

    public void readBatteryLevel() {
        if (mHxMBleService != null) {
            mHxMBleService.readBatteryLevel();
        }
    }

    /**
     * Calls the plot activity.
     */
    public void plot() {
        Intent intent = new Intent(DeviceMonitorActivity.this,
                PlotActivity.class);
        // Plot the current data, not a session
        intent.putExtra(PLOT_SESSION_CODE, false);
        startActivityForResult(intent, REQ_PLOT_CODE);
    }

    /**
     * Displays info about the current configuration
     */
    private void info() {
        try {
            StringBuilder info = new StringBuilder();
            info.append("Device Name: ").append(mDeviceName).append("\n");
            info.append("Device Address: ").append(mDeviceAddress).append("\n");
            info.append("Connected: ").append(mConnected).append("\n");
            info.append("Battery: ").append(mBat.getText()).append("\n");
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            if (Build.VERSION.SDK_INT >= 23
                    && ContextCompat.checkSelfPermission(this, Manifest
                    .permission.ACCESS_COARSE_LOCATION) != PackageManager
                    .PERMISSION_GRANTED) {
                info.append("No permission granted for " +
                        "ACCESS_COARSE_LOCATION\n");
            }
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            if (treeUriStr == null) {
                info.append("Data Directory: Not set");
            } else {
                Uri treeUri = Uri.parse(treeUriStr);
                if (treeUri == null) {
                    info.append("Data Directory: Not set");
                } else {
                    info.append("Data Directory: ").append(treeUri.getPath());
                }
            }
            Utils.infoMsg(this, info.toString());
        } catch (Throwable t) {
            Utils.excMsg(this, "Error showing info", t);
        }
    }


//    /**
//     * Show the help.
//     */
//    private void showHelp() {
//        try {
//            // Start theInfoActivity
//            Intent intent = new Intent();
//            intent.setClass(this, InfoActivity.class);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
//                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//            intent.putExtra(INFO_URL, "file:///android_asset/bcm.html");
//            startActivity(intent);
//        } catch (Exception ex) {
//            Utils.excMsg(this, getString(R.string.help_show_error), ex);
//        }
//    }

    /**
     * Calls the settings activity.
     */
    public void showSettings() {
        Intent intent = new Intent(DeviceMonitorActivity.this,
                SettingsActivity.class);
        intent.putExtra(SETTINGS_CODE, false);
        startActivityForResult(intent, REQ_SETTINGS_CODE);
    }

    /**
     * Calls the session manager activity.
     */
    public void startSessionManager() {
        Intent intent = new Intent(DeviceMonitorActivity.this,
                SessionManagerActivity.class);
        startActivityForResult(intent, REQ_SESSION_MANAGER_CODE);
    }

    /**
     * Displays the data from an ACTION_DATA_AVAILABLE callback.
     *
     * @param intent The intent.
     */
    private void displayData(Intent intent) {
        String uuidString;
        UUID uuid;
        String value;
        try {
            uuidString = intent.getStringExtra(EXTRA_UUID);
            if (uuidString == null) {
                mStatus.setText(R.string.null_uuid_msg);
                return;
            }
            uuid = UUID.fromString(uuidString);
            if (uuid.equals(UUID_HEART_RATE_MEASUREMENT)) {
                value = intent.getStringExtra(EXTRA_HR);
                if (value == null) {
                    mHr.setText(R.string.not_available);
                } else {
                    mHr.setText(value);
                }
                value = intent.getStringExtra(EXTRA_RR);
                if (value == null) {
                    mRr.setText(R.string.not_available);
                } else {
                    mRr.setText(value);
                }
            } else if (uuid.equals(UUID_BATTERY_LEVEL)) {
                value = intent.getStringExtra(EXTRA_BAT);
                if (value == null) {
                    mBat.setText(R.string.not_available);
                } else {
                    mBat.setText(value);
                }
            } else if (uuid.equals(UUID_CUSTOM_MEASUREMENT)) {
                value = intent.getStringExtra(EXTRA_ACT);
                if (value == null) {
                    mAct.setText(R.string.not_available);
                } else {
                    mAct.setText(value);
                }
                value = intent.getStringExtra(EXTRA_PA);
                if (value == null) {
                    mPa.setText(R.string.not_available);
                } else {
                    mPa.setText(value);
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, "Error displaying data", ex);
            mStatus.setText(this.getString(R.string.exception_msg_format,
                    ex.getMessage()));
            // Don't use Utils here as there may be many
            // Utils.excMsg(this, "Error displaying message", ex);
        }
    }

    /**
     * Sets the PREF_MANUALLY_DISCONNECTED preference in PreferenceManager
     * .getDefaultSharedPreferences.
     *
     * @param state The value for PREF_MANUALLY_DISCONNECTED.
     */
    private void setManuallyDisconnected(boolean state) {
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(this).edit();
        editor.putBoolean(PREF_MANUALLY_DISCONNECTED, state);
        editor.apply();
    }

    /**
     * Displays the error from an ACTION_ERROR callback.
     *
     * @param intent The Intent with the message for the error.
     */
    private void displayError(Intent intent) {
        String msg = null;
        try {
            msg = intent.getStringExtra(EXTRA_MSG);
            if (msg == null) {
                mStatus.setText(R.string.null_error_msg);
                Utils.errMsg(this, "Received null error message");
                return;
            }
            Utils.errMsg(this, msg);
        } catch (Exception ex) {
            Log.d(TAG, "Error displaying error", ex);
            mStatus.setText(this.getString(R.string.exception_msg_format,
                    ex.getMessage()));
            Utils.excMsg(this, msg, ex);
        }
    }

    // /**
    // * Sets the enabled flags in the service from the values of mDoBat, mDoHr,
    // * and mDoCustom.
    // */
    // private void setEnabledFlags() {
    // Log.d(TAG, this.getClass().getSimpleName() + ".setEnabledFlags");
    // if (mHxMBleService != null && mHxMBleService.getSessionInProgress()) {
    // int flags = DO_NOTHING;
    // if (mDoBat) {
    // flags |= DO_BAT;
    // }
    // if (mDoHr) {
    // flags |= DO_HR;
    // }
    // if (mDoCustom) {
    // flags |= DO_CUSTOM;
    // }
    // mHxMBleService.setEnabledFlags(flags);
    // }
    // }

    /**
     * Starts a session using the proper starting Characteristics depending on
     * mDoBat, mDoHr, and mDoCustom.
     *
     * @return Whether the service started the session successfully.
     */
    boolean startSession() {
        Log.d(TAG, "DeviceMonitorActivity.startSession: mDoBat=" + mDoBat
                + " mDoHr=" + mDoHr + " mDoCustom=" + mDoCustom);
        Log.d(TAG, "  mCharBat=" + mCharBat + " mCharHr=" + mCharHr
                + " mCharCustom=" + mCharCustom);
        boolean res = mHxMBleService.startSession(mCharBat, mCharHr,
                mCharCustom, mDoBat, mDoHr, mDoCustom);
        String msg = "Doing";
        if (mDoBat) {
            msg += " BAT";
        }
        if (mDoHr) {
            msg += " HR";
        }
        if (mDoCustom) {
            msg += " CUSTOM";
        }
        mStatus.setText(msg);
        return res;
    }

    /**
     * Resets the data view to show default values
     */
    public void resetDataViews() {
        mBat.setText(R.string.not_available);
        mHr.setText(R.string.not_available);
        mRr.setText(R.string.not_available);
        mStatus.setText("");
    }

    private void saveDatabase() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        try {
            String format = "yyyy-MM-dd-HHmmss";
            SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
            Date now = new Date();
            String fileName = String.format(saveDatabaseTemplate,
                    df.format(now));
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "application/vnd.sqlite3", fileName);
            if (docUri == null) {
                Utils.errMsg(this, "Could not create document Uri");
                return;
            }
            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(docUri, "rw");
            File src = new File(getExternalFilesDir(null), DB_NAME);
            Log.d(TAG, "saveDatabase: docUri=" + docUri);
            try (FileChannel in =
                         new FileInputStream(src).getChannel();
                 FileChannel out =
                         new FileOutputStream(pfd.getFileDescriptor()).getChannel()) {
                out.transferFrom(in, 0, in.size());
            } catch (Exception ex) {
                String msg = "Error copying source database from "
                        + docUri.getLastPathSegment() + " to "
                        + src.getPath();
                Log.e(TAG, msg, ex);
                Utils.excMsg(this, msg, ex);
            }
            Utils.infoMsg(this, "Wrote " + docUri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error saving to SD card";
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Does the preliminary checking for restoring the database, prompts if
     * it is OK to delete the current one, and call restoreDatabase to actually
     * do the replace.
     */
    private void checkReplaceDatabase() {
        Log.d(TAG, "checkReplaceDatabase");
        // Find the .db files in the data directory
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no tree Uri set");
            return;
        }
        Uri treeUri = Uri.parse(treeUriStr);
        final List<UriUtils.UriData> children =
                UriUtils.getChildren(this, treeUri, ".db");
        final int len = children.size();
        if (len == 0) {
            Utils.errMsg(this, "There are no .db files in the data directory");
            return;
        }
        // Sort them by date with newest first
        Collections.sort(children,
                (data1, data2) -> Long.compare(data2.modifiedTime,
                        data1.modifiedTime));

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[children.size()];
        String displayName;
        UriUtils.UriData uriData;
        for (int i = 0; i < len; i++) {
            uriData = children.get(i);
            displayName = uriData.displayName;
            if (displayName == null) {
                displayName = uriData.uri.getLastPathSegment();
            }
            items[i] = displayName;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.select_replace_database));
        builder.setSingleChoiceItems(items, 0,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= len) {
                        Utils.errMsg(DeviceMonitorActivity.this,
                                "Invalid item");
                        return;
                    }
                    // Confirm the user wants to delete all the current data
                    new AlertDialog.Builder(DeviceMonitorActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.confirm)
                            .setMessage(R.string.delete_prompt)
                            .setPositiveButton(R.string.ok,
                                    (dialog1, which) -> {
                                        dialog1.dismiss();
                                        Log.d(TAG, "Calling replaceDatabase: " +
                                                "uri="
                                                + children.get(item).uri);
                                        replaceDatabase(children.get(item).uri);
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Replaces the database without prompting.
     *
     * @param uri The Uri.
     */
    private void replaceDatabase(Uri uri) {
        Log.d(TAG, "replaceDatabase: uri=" + uri.getLastPathSegment());
        if (uri == null) {
            Log.d(TAG, this.getClass().getSimpleName()
                    + "replaceDatabase: Source database is null");
            Utils.errMsg(this, "Source database is null");
            return;
        }
        String lastSeg = uri.getLastPathSegment();
        if (!UriUtils.exists(this, uri)) {
            String msg = "Source database does not exist " + lastSeg;
            Log.d(TAG, this.getClass().getSimpleName()
                    + "replaceDatabase: " + msg);
            Utils.errMsg(this, msg);
            return;
        }
        // Copy the data base to app storage
        File dest = null;
        try {
            String destFileName = UriUtils.getFileNameFromUri(uri);
            dest = new File(getExternalFilesDir(null), destFileName);
            dest.createNewFile();
            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(uri, "rw");
            try (FileChannel in =
                         new FileInputStream(pfd.getFileDescriptor()).getChannel();
                 FileChannel out =
                         new FileOutputStream(dest).getChannel()) {
                out.transferFrom(in, 0, in.size());
            } catch (Exception ex) {
                String msg = "Error copying source database from "
                        + uri.getLastPathSegment() + " to "
                        + dest.getPath();
                Log.e(TAG, msg, ex);
                Utils.excMsg(this, msg, ex);
            }
        } catch (Exception ex) {
            String msg = "Error getting source database" + uri;
            Log.e(TAG, msg, ex);
            Utils.excMsg(this, msg, ex);
        }
        try {
            // Replace (Use null for default alias)
            mDbAdapter.replaceDatabase(dest.getPath(), null);
            Utils.infoMsg(this,
                    "Restored database from " + uri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error replacing data from " + dest.getPath();
            Log.e(TAG, msg, ex);
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Sets up read or notify for this characteristic if possible.
     *
     * @param characteristic The Characteristic found.
     */
    private void onCharacteristicFound(
            BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "onCharacteristicFound: " + characteristic.getUuid());
        if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)
                || characteristic.getUuid().equals(UUID_BATTERY_LEVEL)
                || characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
            if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)) {
                mCharHr = characteristic;
            } else if (characteristic.getUuid().equals(UUID_BATTERY_LEVEL)) {
                mCharBat = characteristic;
            } else if (characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
                mCharCustom = characteristic;
            }
            // Start a timer to wait for all characteristics to be accumulated
            // Unless already started
            if (mTimer == null) {
                Log.d(TAG,
                        "onCharacteristicFound: new CancelableCountDownTimer " +
                                "created");
                mTimer = new CancelableCountDownTimer(
                        CHARACTERISTIC_TIMER_TIMEOUT,
                        CHARACTERISTIC_TIMER_INTERVAL) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        if (mCharCustom != null && mCharHr != null
                                && mCharBat != null) {
                            boolean res = startSession();
                            if (res) {
                                mTimer.cancel();
                                mTimer = null;
                                // DEBUG
                                Log.d(TAG,
                                        "onTick: New session has been started" +
                                                " with all characteristics " +
                                                "found");
                            } else {
                                Log.d(TAG,
                                        "onTick: Failed to start new session " +
                                                "with all characteristics " +
                                                "found");
                            }
                        }
                    }

                    @Override
                    public void onFinish() {
                        this.cancel();
                        mTimer = null;
                        // Start it anyway
                        boolean res = startSession();
                        Log.d(TAG,
                                "onTick: onFinish: New session has been " +
                                        "started " +
                                        "anyway");
                        if (!res) {
                            runOnUiThread(() -> {
                                Log.d(TAG,
                                        "onTick: onFinish: Failed to start " +
                                                "new " +
                                                "session anyway");
                                Utils.errMsg(DeviceMonitorActivity.this,
                                        "onTick: onFinish: Failed to start " +
                                                "new " +
                                                "session");
                            });
                        }
                    }
                };
                mTimer.start();
            }
        }
    }

    /**
     * Make an IntentFilter for the actions in which we are interested.
     *
     * @return The IntentFilter.
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HxMBleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(HxMBleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(HxMBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(HxMBleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * Called when services are discovered.
     *
     * @param gattServices The list of Gatt services.
     */
    private void onServicesDiscovered(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {
            return;
        }
        // Loop through available GATT Services
        UUID serviceUuid;
        UUID charUuid;
        mCharBat = null;
        mCharHr = null;
        mCharCustom = null;
        boolean hrFound = false, batFound = false, customFound = false;
        for (BluetoothGattService gattService : gattServices) {
            serviceUuid = gattService.getUuid();
            if (serviceUuid.equals(UUID_HEART_RATE_SERVICE)) {
                hrFound = true;
                // Loop through available Characteristics
                for (BluetoothGattCharacteristic characteristic : gattService
                        .getCharacteristics()) {
                    charUuid = characteristic.getUuid();
                    if (charUuid.equals(UUID_HEART_RATE_MEASUREMENT)) {
                        mCharHr = characteristic;
                        onCharacteristicFound(characteristic);
                    }
                }
            } else if (serviceUuid.equals(UUID_BATTERY_SERVICE)) {
                batFound = true;
                // Loop through available Characteristics
                for (BluetoothGattCharacteristic characteristic : gattService
                        .getCharacteristics()) {
                    charUuid = characteristic.getUuid();
                    if (charUuid.equals(UUID_BATTERY_LEVEL)) {
                        mCharBat = characteristic;
                        onCharacteristicFound(characteristic);
                    }
                }
            } else if (serviceUuid.equals(UUID_HXM_CUSTOM_DATA_SERVICE)) {
                customFound = true;
                // Loop through available Characteristics
                for (BluetoothGattCharacteristic characteristic : gattService
                        .getCharacteristics()) {
                    charUuid = characteristic.getUuid();
                    if (charUuid.equals(UUID_CUSTOM_MEASUREMENT)) {
                        mCharCustom = characteristic;
                        onCharacteristicFound(characteristic);
                    }
                }
            }
        }
        if ((mDoHr && (!hrFound || mCharHr == null))
                || (mDoBat && (!batFound || mCharBat == null))
                || (mDoCustom && (!customFound || mCharCustom == null))) {
            String info = "Services and Characteristics not found:" + "\n";
            if (mDoHr && !hrFound) {
                info += "  Heart Rate" + "\n";
            } else if (mDoHr && mCharHr == null) {
                info += "    Heart Rate Measurement" + "\n";
            } else if (mDoBat && !batFound) {
                info += "  Battery" + "\n";
            } else if (mDoBat && mCharBat == null) {
                info += "    Battery Level" + "\n";
            } else if (mDoCustom && !customFound) {
                info += "  HxM2 Custom Data Service" + "\n";
            } else if (mDoCustom && mCharCustom == null) {
                info += "    Custom Measurement" + "\n";
            }
            Utils.warnMsg(this, info);
            Log.d(TAG, "onServicesDiscovered: " + info);
        }
    }

}
