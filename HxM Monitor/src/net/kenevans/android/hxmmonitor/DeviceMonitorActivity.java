package net.kenevans.android.hxmmonitor;

import java.io.File;
import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code HxMBleService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceMonitorActivity extends Activity implements IConstants {
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
	private File mDataDir;
	private BluetoothGattCharacteristic mCharBat;
	private BluetoothGattCharacteristic mCharHr;
	private BluetoothGattCharacteristic mCharCustom;
	private CancelableCountDownTimer mTimer;

	/**
	 * Manages the service lifecycle.
	 */
	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			Log.d(TAG, "onServiceConnected: " + mDeviceName + " "
					+ mDeviceAddress);
			mHxMBleService = ((HxMBleService.LocalBinder) service)
					.getService();
			if (!mHxMBleService.initialize()) {
				String msg = "Unable to initialize Bluetooth";
				Log.e(TAG, msg);
				Utils.errMsg(DeviceMonitorActivity.this, msg);
				return;
			}
			if (mDbAdapter != null) {
				mHxMBleService.startDatabase(mDbAdapter);
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			boolean res = mHxMBleService.connect(mDeviceAddress);
			Log.d(TAG, "Connect mHxMBleService result=" + res);
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
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (HxMBleService.ACTION_GATT_CONNECTED.equals(action)) {
				Log.d(TAG, "onReceive: " + action);
				mConnected = true;
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			} else if (HxMBleService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				Log.d(TAG, "onReceive: " + action);
				mConnected = false;
				resetDataViews();
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
			} else if (HxMBleService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				Log.d(TAG, "onReceive: " + action);
				onServicesDiscovered(mHxMBleService
						.getSupportedGattServices());
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
		setContentView(R.layout.activity_device_monitor);

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
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
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
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		mBat = (TextView) findViewById(R.id.bat_value);
		mHr = (TextView) findViewById(R.id.hr_value);
		mRr = (TextView) findViewById(R.id.rr_value);
		mAct = (TextView) findViewById(R.id.act_value);
		mPa = (TextView) findViewById(R.id.pa_value);
		mStatus = (TextView) findViewById(R.id.status_value);
		resetDataViews();

		Intent gattServiceIntent = new Intent(this, HxMBleService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		// Open the database
		mDataDir = getDataDirectory();
		if (mDataDir == null) {
			return;
		}
		mDbAdapter = new HxMMonitorDbAdapter(this, mDataDir);
		mDbAdapter.open();
	}

	@Override
	protected void onResume() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume: mConnected="
				+ mConnected + " mHxMBleService="
				+ (mHxMBleService == null ? "null" : "not null"));
		super.onResume();
		Log.d(TAG, "Starting registerReceiver");
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		// Debug
		// Intent intent = registerReceiver(mGattUpdateReceiver,
		// makeGattUpdateIntentFilter());
		// if (intent == null) {
		// Log.d(TAG, "After registerReceiver: intent is null");
		// } else {
		// Log.d(TAG, "After registerReceiver: intent is not null");
		// final String action = intent.getAction();
		// if (HxMBleService.ACTION_GATT_CONNECTED.equals(action)) {
		// Log.d(TAG, "  intent.getAction: ACTION_GATT_CONNECTED");
		// } else if (HxMBleService.ACTION_GATT_DISCONNECTED
		// .equals(action)) {
		// Log.d(TAG, "  intent.getAction: ACTION_GATT_DISCONNECTED");
		// } else if (HxMBleService.ACTION_GATT_DISCONNECTED
		// .equals(action)) {
		// Log.d(TAG, "  intent.getAction: ACTION_GATT_DISCONNECTED");
		// } else if (HxMBleService.ACTION_DATA_AVAILABLE.equals(action)) {
		// Log.d(TAG, "  intent.getAction: ACTION_DATA_AVAILABLE");
		// }
		// }
		if (mHxMBleService != null) {
			Log.d(TAG, "Starting mHxMBleService.connect");
			final boolean res = mHxMBleService.connect(mDeviceAddress);
			Log.d(TAG, "mHxMBleService.connect: result=" + res);
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
		super.onDestroy();
		unbindService(mServiceConnection);
		mHxMBleService = null;
		if (mDbAdapter != null) {
			mDbAdapter.close();
			mDbAdapter = null;
		}
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
		switch (item.getItemId()) {
		case R.id.menu_connect:
			mHxMBleService.connect(mDeviceAddress);
			return true;
		case R.id.menu_disconnect:
			mHxMBleService.disconnect();
			return true;
		case android.R.id.home:
			onBackPressed();
			return true;
		case R.id.menu_select_device:
			selectDevice();
			return true;
		case R.id.menu_session_manager:
			startSessionManager();
			return true;
		case R.id.menu_plot:
			plot();
			return true;
		case R.id.menu_settings:
			settings();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SELECT_DEVICE_CODE:
			if (resultCode == Activity.RESULT_OK) {
				String deviceName = data.getStringExtra(DEVICE_NAME_CODE);
				String deviceAddress = data.getStringExtra(DEVICE_ADDRESS_CODE);
				// Use this instead of getPreferences to be application-wide
				SharedPreferences.Editor editor = PreferenceManager
						.getDefaultSharedPreferences(this).edit();
				editor.putString(DEVICE_NAME_CODE, deviceName);
				editor.putString(DEVICE_ADDRESS_CODE, deviceAddress);
				editor.commit();
			}
			break;
		case REQUEST_PLOT_CODE:
			String msg = null;
			if (data != null) {
				msg = data.getStringExtra(MSG_CODE);
			}
			if (resultCode == RESULT_ERROR) {
				if (msg != null) {
					Utils.errMsg(this, msg);
				} else {
					Utils.errMsg(this, "Error reading file");
				}
				// } else if (resultCode == RESULT_CANCELED) {
				// if (msg != null) {
				// Utils.errMsg(this, msg);
				// } else {
				// Utils.errMsg(this, "Canceled");
				// }
			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Gets the current data directory and sets the default preference for
	 * PREF_DATA_DIRECTORY.
	 * 
	 * @return GThe directory or null on failure.
	 */
	public File getDataDirectory() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String dataDirName = prefs.getString(PREF_DATA_DIRECTORY, null);
		File dataDir = null;
		if (dataDirName != null) {
			dataDir = new File(dataDirName);
		} else {
			File sdCardRoot = Environment.getExternalStorageDirectory();
			if (sdCardRoot != null) {
				dataDir = new File(sdCardRoot, SD_CARD_DB_DIRECTORY);
				// Change the stored value (even if it is null)
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString(PREF_DATA_DIRECTORY, dataDir.getPath());
				editor.commit();
			}
		}
		if (dataDir == null) {
			Utils.errMsg(this, "Data directory is null");
		}
		if (!dataDir.exists()) {
			boolean res = dataDir.mkdir();
			if (!res) {
				Utils.errMsg(this, "Cannot find or create directory: "
						+ dataDir);
				return null;
			}
		}
		return dataDir;
	}

	/**
	 * Updates the connection state view on the UI thread.
	 * 
	 * @param resourceId
	 */
	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConnectionState.setText(resourceId);
			}
		});
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
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									Intent intent = new Intent(
											DeviceMonitorActivity.this,
											DeviceScanActivity.class);
									startActivityForResult(intent,
											REQUEST_SELECT_DEVICE_CODE);
								}

							}).setNegativeButton(R.string.cancel, null).show();
		} else {
			Intent intent = new Intent(DeviceMonitorActivity.this,
					DeviceScanActivity.class);
			startActivityForResult(intent, REQUEST_SELECT_DEVICE_CODE);
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
		startActivityForResult(intent, REQUEST_PLOT_CODE);
	}

	/**
	 * Calls the settings activity.
	 */
	public void settings() {
		Intent intent = new Intent(DeviceMonitorActivity.this,
				SettingsActivity.class);
		// Plot the current data, not a session
		intent.putExtra(SETTINGS_CODE, false);
		startActivityForResult(intent, REQUEST_SETTINGS_CODE);
	}

	/**
	 * Calls the session manager activity.
	 */
	public void startSessionManager() {
		Intent intent = new Intent(DeviceMonitorActivity.this,
				SessionManagerActivity.class);
		startActivityForResult(intent, REQUEST_SESSION_MANAGER_CODE);
	}

	/**
	 * Displays the data from an ACTION_DATA_AVAILABLE callback.
	 * 
	 * @param intent
	 */
	private void displayData(Intent intent) {
		String uuidString = null;
		UUID uuid = null;
		String value;
		try {
			uuidString = intent.getStringExtra(EXTRA_UUID);
			if (uuidString == null) {
				mStatus.setText("Received null uuid");
				return;
			}
			uuid = UUID.fromString(uuidString);
			if (uuid.equals(UUID_HEART_RATE_MEASUREMENT)) {
				value = intent.getStringExtra(EXTRA_HR);
				if (value == null) {
					mHr.setText("NA");
				} else {
					mHr.setText(value);
				}
				value = intent.getStringExtra(EXTRA_RR);
				if (value == null) {
					mRr.setText("NA");
				} else {
					mRr.setText(value);
				}
			} else if (uuid.equals(UUID_BATTERY_LEVEL)) {
				value = intent.getStringExtra(EXTRA_BAT);
				if (value == null) {
					mBat.setText("NA");
				} else {
					mBat.setText(value);
				}
			} else if (uuid.equals(UUID_CUSTOM_MEASUREMENT)) {
				value = intent.getStringExtra(EXTRA_ACT);
				if (value == null) {
					mAct.setText("NA");
				} else {
					mAct.setText(value);
				}
				value = intent.getStringExtra(EXTRA_PA);
				if (value == null) {
					mPa.setText("NA");
				} else {
					mPa.setText(value);
				}
			}
		} catch (Exception ex) {
			Log.d(TAG, "Error displaying data", ex);
			mStatus.setText("Exception: " + ex.getMessage());
			// Don't use Utils here as there may be many
			// Utils.excMsg(this, "Error displaying message", ex);
		}
	}

	/**
	 * Displays the error from an ACTION_ERROR callback.
	 * 
	 * @param intent
	 */
	private void displayError(Intent intent) {
		String msg = null;
		try {
			msg = intent.getStringExtra(EXTRA_MSG);
			if (msg == null) {
				mStatus.setText("Received null error message");
				Utils.errMsg(this, "Received null error message");
				return;
			}
			Utils.errMsg(this, msg);
		} catch (Exception ex) {
			Log.d(TAG, "Error displaying error", ex);
			mStatus.setText("Exception: " + ex.getMessage());
			Utils.excMsg(this, msg, ex);
		}
	}

	/**
	 * Resets the data view to show default values
	 */
	public void resetDataViews() {
		mBat.setText("NA");
		mHr.setText("NA");
		mRr.setText("NA");
		mAct.setText("NA");
		mPa.setText("NA");
		mStatus.setText("");
	}

	/**
	 * Sets up read or notify for this characteristic if possible.
	 * 
	 * @param characteristic
	 */
	private void onCharacteristicFound(
			BluetoothGattCharacteristic characteristic) {
		Log.d(TAG, "onCharacteristicFound: " + characteristic.getUuid());
		if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)
				|| characteristic.getUuid().equals(UUID_BATTERY_LEVEL)
				|| characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
			if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)) {
				if (mDoHr) {
					mCharHr = characteristic;
				} else {
					mCharHr = null;
				}
				mCharHr = characteristic;
			} else if (characteristic.getUuid().equals(UUID_BATTERY_LEVEL)) {
				if (mDoBat) {
					mCharBat = characteristic;
				} else {
					mCharBat = null;
				}
				mCharBat = characteristic;
			} else if (characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
				if (mDoCustom) {
					mCharCustom = characteristic;
				} else {
					mCharCustom = null;
				}
			}
			// Start a timer to wait for all characteristics to be accumulated
			if (mTimer == null) {
				Log.d(TAG, "onCharacteristicFound: new Timer created");
				mTimer = new CancelableCountDownTimer(
						CHARACTERISTIC_TIMER_TIMEOUT,
						CHARACTERISTIC_TIMER_INTERVAL) {
					@Override
					public void onTick(long millisUntilFinished) {
						if ((!mDoCustom || mCharCustom != null)
								&& (!mDoHr || mCharHr != null)
								&& (!mDoBat || mCharBat != null)
								&& mHxMBleService != null) {
							boolean res = mHxMBleService.startSession(
									mCharBat, mCharHr, mCharCustom);
							if (res) {
								mTimer.cancel();
								mTimer = null;
								Log.d(TAG,
										"onTick: New session started with all characteristics");
							} else {
								Log.d(TAG,
										"onTick: Session failed to start new session with all characteristics");
							}
						}
					}

					@Override
					public void onFinish() {
						boolean res = mHxMBleService.startSession(
								mCharBat, mCharHr, mCharCustom);
						if (!res) {
							runOnUiThread(new Runnable() {
								public void run() {
									Utils.errMsg(DeviceMonitorActivity.this,
											"Failed to start new session");
								}
							});
						} else {
							Log.d(TAG, "onTick: New session started: mCharHr="
									+ (mCharHr != null ? "found" : "null")
									+ " mCharBat="
									+ (mCharBat != null ? "found" : "null")
									+ " mCharCustom="
									+ (mCharCustom != null ? "found" : "null"));
						}
						this.cancel();
						mTimer = null;
					}
				};
				mTimer.start();
			}
		}
	}

	/**
	 * Make an IntentFilter for the actions in which we are interested.
	 * 
	 * @return
	 */
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(HxMBleService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(HxMBleService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(HxMBleService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(HxMBleService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	/**
	 * Called when services are discovered.
	 * 
	 * @param gattServices
	 */
	private void onServicesDiscovered(List<BluetoothGattService> gattServices) {
		if (gattServices == null) {
			return;
		}
		// Loop through available GATT Services
		mTimer = null;
		UUID serviceUuid = null;
		UUID charUuid = null;
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
		if (!hrFound || !batFound || !customFound || (mDoHr && mCharHr == null)
				|| (mDoBat && mCharBat == null)
				|| (mDoCustom && mCharCustom == null)) {
			String info = "Services and Characteristics not found:" + "\n";
			if (!hrFound) {
				info += "  Heart Rate" + "\n";
			} else if (mCharHr == null) {
				info += "    Heart Rate Measurement" + "\n";
			} else if (!batFound) {
				info += "  Battery" + "\n";
			} else if (mCharBat == null) {
				info += "    Battery Level" + "\n";
			} else if (!customFound) {
				info += "  HxM2 Custom Data Service" + "\n";
			} else if (mDoCustom && mCharCustom == null) {
				info += "    Custom Measurement" + "\n";
			}
			Utils.warnMsg(this, info);
			Log.d(TAG, "onServicesDiscovered: " + info);
		}
	}

}
