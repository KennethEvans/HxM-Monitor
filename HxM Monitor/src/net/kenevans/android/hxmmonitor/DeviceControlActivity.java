package net.kenevans.android.hxmmonitor;

import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements IConstants {
	private TextView mConnectionState;
	private TextView mDataField;
	private TextView mStatus;
	private String mDeviceName;
	private String mDeviceAddress;
	private BluetoothLeService mBluetoothLeService;
	private boolean mConnected = false;

	// private CharacteristicItem[] mCharacteristics = {
	// new CharacteristicItem()
	// }
	//
	// // End
	// };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_control);

		// Get the preferences here before refresh()
		// Use this instead of getPreferences to be application-wide
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		mDeviceName = prefs.getString(DEVICE_NAME_CODE, null);
		mDeviceAddress = prefs.getString(DEVICE_ADDRESS_CODE, null);
		Log.d(TAG, this.getClass().getName() + ": onCreate: " + mDeviceName
				+ " " + mDeviceAddress);

		// Sets up UI references.
		((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		mDataField = (TextView) findViewById(R.id.data_value);
		mStatus = (TextView) findViewById(R.id.status);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		Log.d(TAG, this.getClass().getName() + ": onResume");
		Log.d(TAG, this.getClass().getName() + ": mBluetoothLeService");
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean res = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "onResume: Connect mBluetoothLeService result=" + res);
		}
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getName() + ": onPause");
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gatt_services, menu);
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
			mBluetoothLeService.connect(mDeviceAddress);
			return true;
		case R.id.menu_disconnect:
			mBluetoothLeService.disconnect();
			return true;
		case android.R.id.home:
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConnectionState.setText(resourceId);
			}
		});
	}

	/**
	 * Displays the data from an ACTION_DATA_AVAILABLE callback.
	 * 
	 * @param intent
	 */
	private void displayData(Intent intent) {
		String uuidString = null;
		UUID uuid = null;
		try {
			uuidString = intent.getStringExtra(EXTRA_UUID);
			if (uuidString == null) {
				mStatus.setText(" Received null uuid");
				return;
			}
			uuid = UUID.fromString(uuidString);
			String data = intent.getStringExtra(EXTRA_DATA);
			if (data == null) {
				mStatus.setText(" Received null data");
				return;
			}
			if (uuid.equals(UUID_HEART_RATE_MEASUREMENT)) {
				mDataField.setText(data);
			}
		} catch (Exception ex) {
			Log.d(TAG, "Error displaying data", ex);
			mStatus.setText("Exception: " + ex.getMessage());
			// Don't use Utils here as there may be many
			// Utils.excMsg(this, "Error displaying message", ex);
		}
	}

	/**
	 * Sets up read or notify for this characteristic if possible.
	 * 
	 * @param characteristic
	 */
	private void onCharacteristicFound(
			BluetoothGattCharacteristic characteristic) {
		// First try to read it
		final int property = characteristic.getProperties();
		if ((property | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
			mBluetoothLeService.readCharacteristic(characteristic);
		}
		// Then set up a notification if possible
		if ((property | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
			mBluetoothLeService.setCharacteristicNotification(characteristic,
					true);
		}
	}

	/**
	 * Make an IntentFilter for the actions in which we are interested.
	 * 
	 * @return
	 */
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	/**
	 * Manages the service lifecycle.
	 */
	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			Log.d(TAG, "onServiceConnected: " + mDeviceName + " "
					+ mDeviceAddress);
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			boolean res = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect mBluetoothLeService result=" + res);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			Log.d(TAG, "onServiceDisconnected");
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device. This can be a
	// result of read or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			Log.d(TAG, "onReceive: " + action);
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				mConnected = false;
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				onServicesDiscovered(mBluetoothLeService
						.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(intent);
			}
		}
	};

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
		UUID serviceUuid = null;
		UUID charUuid = null;
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
						onCharacteristicFound(characteristic);
					}
				}
			}
		}
		if (!hrFound || !batFound || !customFound) {
			String info = "Services not found:" + "\n";
			if (!hrFound) {
				info += "  Heart Rate" + "\n";
			} else if (!batFound) {
				info += "  Battery" + "\n";
			} else if (!customFound) {
				info += "  HxM Custom" + "\n";
			}
			Utils.warnMsg(this, info);
		}
	}

}
