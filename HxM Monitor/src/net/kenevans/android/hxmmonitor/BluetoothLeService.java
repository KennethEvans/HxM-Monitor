/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kenevans.android.hxmmonitor;

import java.util.Date;
import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
/**
 * @author evans
 *
 */
public class BluetoothLeService extends Service implements IConstants {
	private final static String TAG = "HxM BLEService";

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;
	private int mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
	private int mSessionState = SESSION_IDLE;
	private int mLastBat;
	private int mLastHr;
	private String mLastRr;
	private int mLastAct;
	private int mLastPa;
	private BluetoothGattCharacteristic mCharBat;
	private BluetoothGattCharacteristic mCharHr;
	private BluetoothGattCharacteristic mCharCustom;
	private boolean mSessionInProgress = false;
	private boolean mTemporarySession = true;
	// TODO Is this needed?
	private long mSessionStartTime;

	// private static final int STATE_DISCONNECTED = 0;
	// private static final int STATE_CONNECTING = 1;
	// private static final int STATE_CONNECTED = 2;

	public final static String ACTION_GATT_CONNECTED = PACKAGE_NAME
			+ ".ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = PACKAGE_NAME
			+ ".ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = PACKAGE_NAME
			+ ".ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE = PACKAGE_NAME
			+ ".ACTION_DATA_AVAILABLE";

	// Implements callback methods for GATT events that the app cares about. For
	// example, connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			Log.i(TAG, "onConnectionStateChange: status="
					+ (status == BluetoothGatt.GATT_SUCCESS ? "GATT_SUCCESS"
							: status));
			if (status != BluetoothGatt.GATT_SUCCESS) {
				Log.i(TAG,
						"onConnectionStateChange: Aborting: status is not GATT_SUCCESS");
				return;
			}
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				mConnectionState = BluetoothProfile.STATE_CONNECTED;
				// Stop any session
				stopSession();
				broadcastUpdate(intentAction);
				Log.i(TAG, "onConnectionStateChange: Connected to GATT server");
				// Attempts to discover services after successful connection.
				Log.i(TAG,
						"onConnectionStateChange: Attempting to start service discovery: "
								+ mBluetoothGatt.discoverServices());
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
				Log.i(TAG,
						"onConnectionStateChange: Disconnected from GATT server");
				// Stop any session
				stopSession();
				broadcastUpdate(intentAction);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
			} else {
				Log.w(TAG, "onCharacteristicRead received: " + status);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			if (mSessionInProgress) {
				incrementSessionState();
			}
			broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
		}
	};

	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action,
			final BluetoothGattCharacteristic characteristic) {
		Date now = new Date();
		long date = now.getTime();
		String dateStr = " @ " + millisecTimeFormater.format(now);
		final Intent intent = new Intent(action);
		intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
		intent.putExtra(EXTRA_DATE, date);

		// This is special handling for the Heart Rate Measurement profile.
		// parsing is carried out as per profile specifications:
		// http: //
		// developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
		if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
			HeartRateValues values = new HeartRateValues(characteristic, date);
			mLastHr = values.getHr();
			mLastRr = values.getRr();
			Log.d(TAG, String.format("Received heart rate measurement: %d",
					mLastHr));
			intent.putExtra(EXTRA_HR, String.valueOf(values.getHr() + dateStr));
			intent.putExtra(EXTRA_RR, values.getRr() + dateStr);
			intent.putExtra(EXTRA_DATA, values.getString());
			if (mSessionInProgress && mSessionState == SESSION_WAITING_HR) {
				incrementSessionState();
			}
			// Log.d(TAG, String.format("Received HR: %d", values.getHr()));
		} else if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {
			final int iVal = characteristic.getIntValue(
					BluetoothGattCharacteristic.FORMAT_UINT8, 0);
			mLastBat = iVal;
			Log.d(TAG, String.format("Received battery level: %d", iVal));
			intent.putExtra(EXTRA_BAT, String.valueOf(iVal) + dateStr);
			intent.putExtra(EXTRA_DATA,
					String.valueOf("Battery Level: " + iVal));
			if (mSessionInProgress && mSessionState == SESSION_WAITING_BAT) {
				incrementSessionState();
			}
		} else if (UUID_CUSTOM_MEASUREMENT.equals(characteristic.getUuid())) {
			HxMCustomValues values = new HxMCustomValues(characteristic, date);
			mLastAct = values.getActivity();
			mLastPa = values.getPa();
			Log.d(TAG,
					String.format("Received custom measurement: %d", mLastAct));
			intent.putExtra(EXTRA_ACT,
					String.valueOf(values.getActivity() + dateStr));
			intent.putExtra(EXTRA_PA, String.valueOf(values.getPa() + dateStr));
			intent.putExtra(EXTRA_DATA, dateStr + values.getString());
			if (mSessionInProgress && mSessionState == SESSION_WAITING_CUSTOM) {
				incrementSessionState();
			}
		} else {
			// For all other profiles, writes the data formatted in HEX.
			final byte[] data = characteristic.getValue();
			if (data != null && data.length > 0) {
				final StringBuilder stringBuilder = new StringBuilder(
						data.length);
				for (byte byteChar : data) {
					stringBuilder.append(String.format("%02X ", byteChar));
				}
				intent.putExtra(
						EXTRA_DATA,
						BleNamesResolver
								.resolveCharacteristicName(characteristic
										.getUuid().toString())
								+ "\n"
								+ new String(data)
								+ "\n"
								+ stringBuilder.toString());
			} else {
				intent.putExtra(
						EXTRA_DATA,
						BleNamesResolver
								.resolveCharacteristicName(characteristic
										.getUuid().toString())
								+ "\n" + ((data == null) ? "null" : "No data"));
			}
		}
		sendBroadcast(intent);
	}

	public class LocalBinder extends Binder {
		BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// After using a given device, you should make sure that
		// BluetoothGatt.close() is called
		// such that resources are cleaned up properly. In this particular
		// example, close() is
		// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter
		// through
		// BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address
	 *            The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The
	 *         connection result is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG,
					"connect: BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		// Previously connected device. Try to reconnect.
		if (mBluetoothDeviceAddress != null
				&& address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {
			Log.d(TAG,
					"connect: Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
				mConnectionState = BluetoothProfile.STATE_CONNECTING;
				return true;
			} else {
				return false;
			}
		}

		final BluetoothDevice device = mBluetoothAdapter
				.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the
		// autoConnect parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		mConnectionState = BluetoothProfile.STATE_CONNECTING;
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The
	 * disconnection result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure
	 * resources are released properly.
	 */
	public void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	/**
	 * Request a read on a given {@code BluetoothGattCharacteristic}. The read
	 * result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * callback.
	 *
	 * @param characteristic
	 *            The characteristic to read from.
	 */
	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		boolean res = mBluetoothGatt.readCharacteristic(characteristic);
		if (!res) {
			String name = BleNamesResolver
					.resolveCharacteristicName(characteristic.getUuid()
							.toString());
			Log.d(TAG, "readCharacteristic failed for " + name);
			if ((mCharCustom.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
				Log.d(TAG, name + " is not readable");
			}
		}
	}

	/**
	 * Enables or disables notification on a given characteristic.
	 *
	 * @param characteristic
	 *            Characteristic to act on.
	 * @param enabled
	 *            If true, enable notification. False otherwise.
	 */
	public void setCharacteristicNotification(
			BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		boolean res = mBluetoothGatt.setCharacteristicNotification(
				characteristic, enabled);
		if (!res) {
			Log.d(TAG,
					"setCharacteristicNotification failed for "
							+ BleNamesResolver
									.resolveCharacteristicName(characteristic
											.getUuid().toString()));
		}

		// TODO This is client specific, not characteristic specific. It is the
		// same code for all characteristics.
		// This is specific to Heart Rate Measurement.
		if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}

		// This is specific to Battery Level
		if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}

		// This is specific to Custom Measurement
		if (UUID_CUSTOM_MEASUREMENT.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}

		// This is specific to Test Mode
		if (UUID_TEST_MODE.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}
	}

	/**
	 * Retrieves a list of supported GATT services on the connected device. This
	 * should be invoked only after {@code BluetoothGatt#discoverServices()}
	 * completes successfully.
	 *
	 * @return A {@code List} of supported services.
	 */
	public List<BluetoothGattService> getSupportedGattServices() {
		if (mBluetoothGatt == null)
			return null;

		return mBluetoothGatt.getServices();
	}

	/**
	 * Returns the connection state.
	 * 
	 * @return
	 */
	public int getConnectionState() {
		return mConnectionState;
	}

	/**
	 * Writes READ, NOTIFY, WRITE properties to the Log. Use for debugging.
	 * 
	 * @param charBat
	 * @param charHr
	 * @param charCustom
	 */
	void checkPermissions(BluetoothGattCharacteristic charBat,
			BluetoothGattCharacteristic charHr,
			BluetoothGattCharacteristic charCustom) {
		// Check permissions
		if ((charBat.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
			Log.d(TAG, "incrementSessionState: charBat: Not Readable");
		} else {
			Log.d(TAG, "incrementSessionState: charBat: Readable");
		}
		if ((charBat.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
			Log.d(TAG, "incrementSessionState: charBat: Not Notifiable");
		} else {
			Log.d(TAG, "incrementSessionState: charBat: Notifiable");
		}
		if ((charBat.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
			Log.d(TAG, "incrementSessionState: charBat: Not Writeable");
		} else {
			Log.d(TAG, "incrementSessionState: charBat: Writeable");
		}

		if ((charHr.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
			Log.d(TAG, "incrementSessionState: charHr: Not Readable");
		} else {
			Log.d(TAG, "incrementSessionState: charHr: Readable");
		}
		if ((charHr.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
			Log.d(TAG, "incrementSessionState: charHr: Not Notifiable");
		} else {
			Log.d(TAG, "incrementSessionState: charHr: Notifiable");
		}
		if ((charHr.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
			Log.d(TAG, "incrementSessionState: charHr: Not Writeable");
		} else {
			Log.d(TAG, "incrementSessionState: charHr: Writeable");
		}

		if ((charCustom.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
			Log.d(TAG, "incrementSessionState: charCustom: Not Readable");
		} else {
			Log.d(TAG, "incrementSessionState: charCustom: Readable");
		}
		if ((charCustom.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
			Log.d(TAG, "incrementSessionState: charCustom: Not Notifiable");
		} else {
			Log.d(TAG, "incrementSessionState: charCustom: Notifiable");
		}
		if ((charCustom.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
			Log.d(TAG, "incrementSessionState: charCustom: Not Writeable");
		} else {
			Log.d(TAG, "incrementSessionState: charCustom: Writeable");
		}
	}

	/**
	 * Sets the state to SESSION_WAITING_BAT if there is a session in progress.
	 * 
	 * @return If successful.
	 */
	public boolean checkBattery() {
		if (!mSessionInProgress || mCharBat == null) {
			return false;
		}
		// Stop notifying for existing characteristics
		if (mSessionInProgress = true && mCharHr != null) {
			setCharacteristicNotification(mCharHr, false);
		}
		if (mSessionInProgress = true && mCharCustom != null) {
			setCharacteristicNotification(mCharCustom, false);
		}
		mSessionState = SESSION_WAITING_BAT;
		readCharacteristic(mCharBat);

		return true;
	}

	/**
	 * Stops a session.
	 * 
	 * @param charBat
	 * @param charHr
	 * @param charCustom
	 * @param temporary
	 *            If the session is temporary.
	 * @return If successful.
	 */
	public boolean startSession(BluetoothGattCharacteristic charBat,
			BluetoothGattCharacteristic charHr,
			BluetoothGattCharacteristic charCustom, boolean temporary) {
		boolean res = true;
		mSessionStartTime = new Date().getTime();
		mTemporarySession = temporary;
		
		// // DEBUG Check permissions
		// checkPermissions(charBat, charHr, charCustom);

		// Stop notifying for existing characteristics
		if (mSessionInProgress = true && mCharHr != null) {
			setCharacteristicNotification(charHr, false);
		}
		if (mSessionInProgress = true && mCharCustom != null) {
			setCharacteristicNotification(mCharCustom, false);
		}
		if (charBat != null) {
			mSessionState = SESSION_WAITING_BAT;
			readCharacteristic(charBat);
		} else if (charHr != null) {
			mSessionState = SESSION_WAITING_HR;
			setCharacteristicNotification(charHr, true);
		} else if (charCustom != null) {
			mSessionState = SESSION_WAITING_CUSTOM;
			setCharacteristicNotification(charCustom, true);
		} else {
			mSessionState = SESSION_IDLE;
			res = false;
		}
		mCharBat = charBat;
		mCharHr = charHr;
		mCharCustom = charCustom;
		mLastBat = -1;
		mLastHr = -1;
		mLastRr = null;
		mLastAct = -1;
		mLastPa = -1;
		mSessionInProgress = res;

		return res;
	}

	/**
	 * Stops a session.
	 */
	public void stopSession() {
		// Stop notifying for existing characteristics
		if (mSessionInProgress = true && mCharHr != null) {
			setCharacteristicNotification(mCharHr, false);
		}
		if (mSessionInProgress = true && mCharCustom != null) {
			setCharacteristicNotification(mCharCustom, false);
		}
		mCharBat = null;
		mCharHr = null;
		mCharCustom = null;
		mLastBat = -1;
		mLastHr = -1;
		mLastRr = null;
		mLastAct = -1;
		mLastPa = -1;
		mSessionState = SESSION_IDLE;
		mSessionInProgress = false;
	}

	/**
	 * Performs the logic to set the next session state.
	 */
	public void incrementSessionState() {
		if (!mSessionInProgress) {
			mSessionState = SESSION_IDLE;
			return;
		}
		int oldSessionState = mSessionState;
		switch (mSessionState) {
		case SESSION_WAITING_BAT:
			if (mCharHr != null) {
				mSessionState = SESSION_WAITING_HR;
				setCharacteristicNotification(mCharHr, true);
			} else if (mCharCustom != null) {
				mSessionState = SESSION_WAITING_CUSTOM;
				setCharacteristicNotification(mCharCustom, true);
			}
			// Else leave it as is
			break;
		case SESSION_WAITING_HR:
			if (mCharCustom != null) {
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
					mSessionState = SESSION_WAITING_CUSTOM;
					readCharacteristic(mCharCustom);
				} else {
					mSessionState = SESSION_WAITING_CUSTOM;
					setCharacteristicNotification(mCharCustom, true);
				}
			}
			// Else leave it as is
			break;
		case SESSION_WAITING_CUSTOM:
			if (mCharHr != null) {
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				mSessionState = SESSION_WAITING_HR;
				setCharacteristicNotification(mCharHr, true);
			}
			// Else leave it as is
			break;
		}
		Log.d(TAG, "incrementSessionState: oldState=" + oldSessionState
				+ " new State=" + mSessionState);
	}

}
