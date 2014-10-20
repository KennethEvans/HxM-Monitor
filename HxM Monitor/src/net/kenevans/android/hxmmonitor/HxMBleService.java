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
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.PendingIntent;
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
/**
 * @author evans
 *
 */
public class HxMBleService extends Service implements IConstants {
	private final static String TAG = "HxM BLEService";

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;
	private HxMMonitorDbAdapter mDbAdapter;
	private int mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
	private int mSessionState = SESSION_IDLE;
	private long mLastHrDate;
	private int mLastBat = INVALID_INT;
	private int mLastHr = INVALID_INT;
	private String mLastRr = INVALID_STRING;
	private int mLastAct = INVALID_INT;
	private int mLastPa = INVALID_INT;

	private BluetoothGattCharacteristic mCharBat;
	private BluetoothGattCharacteristic mCharHr;
	private BluetoothGattCharacteristic mCharCustom;
	private boolean mDoBat = true;
	private boolean mDoHr = true;
	private boolean mDoCustom = true;
	private boolean mSessionInProgress = false;
	/**
	 * Timer to change the session back to SESSION_WAITING_HR if it is in
	 * SESSION_WAITING_CUSTOM too long.
	 */
	private Timer mTimeoutTimer;
	/**
	 * TimerTask to change the session back to SESSION_WAITING_HR if it is in
	 * SESSION_WAITING_CUSTOM too long.
	 */
	private TimerTask mTimeoutTask;
	// TODO Is this needed?
	private long mSessionStartTime;
	private final IBinder mBinder = new LocalBinder();

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
	public final static String ACTION_ERROR = PACKAGE_NAME + ".ACTION_ERROR";

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

	@Override
	public void onCreate() {
		super.onCreate();
		// Post a notification the service is running
		Intent activityIntent = new Intent(this, DeviceMonitorActivity.class);
		PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0,
				activityIntent, Notification.FLAG_ONGOING_EVENT);
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
				this)
				.setSmallIcon(R.drawable.hxmmonitor)
				.setContentTitle(getString(R.string.service_notification_title))
				.setContentText(getString(R.string.service_notification_text))
				.setContentIntent(viewPendingIntent);
		NotificationManagerCompat notificationManager = NotificationManagerCompat
				.from(this);
		notificationManager
				.notify(NOTIFICATION_ID, notificationBuilder.build());
	}

	@Override
	public void onDestroy() {
		// Cancel the notification
		NotificationManagerCompat notificationManager = NotificationManagerCompat
				.from(this);
		notificationManager.cancel(NOTIFICATION_ID);
		super.onDestroy();
	}

	// /**
	// * Broadcast an error using ACTION_ERROR.
	// *
	// * @param msg
	// */
	// private void broadcastError(final String msg) {
	// final Intent intent = new Intent(ACTION_ERROR);
	// intent.putExtra(EXTRA_MSG, msg);
	// sendBroadcast(intent);
	// }

	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action,
			final BluetoothGattCharacteristic characteristic) {
		Date now = new Date();
		long date = now.getTime();
		// Set this to "" to not get the date in the return value
		// String dateStr = " @ " + millisecTimeFormater.format(now);
		String dateStr = "";
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
			mLastHrDate = date;
			// DEBUG
			Log.d(TAG, String.format("Received heart rate measurement: %d",
					mLastHr));
			if (mDbAdapter != null && (mCharCustom == null || !mDoCustom)) {
				mDbAdapter.createData(mLastHrDate, mSessionStartTime, mLastHr,
						mLastRr, INVALID_INT, INVALID_INT);
			}
			intent.putExtra(EXTRA_HR, String.valueOf(values.getHr() + dateStr));
			intent.putExtra(EXTRA_RR, values.getRr() + dateStr);
			intent.putExtra(EXTRA_DATA, values.getInfo());
			if (mSessionInProgress && mSessionState == SESSION_WAITING_HR) {
				incrementSessionState();
			}
		} else if (UUID_CUSTOM_MEASUREMENT.equals(characteristic.getUuid())) {
			HxMCustomValues values = new HxMCustomValues(characteristic, date);
			mLastAct = values.getActivity();
			mLastPa = values.getPa();
			// DEBUG
			Log.d(TAG, String.format("Received custom measurement: %d %d",
					mLastAct, mLastPa));
			if (mDbAdapter != null) {
				if (mCharHr == null || !mDoHr) {
					mDbAdapter.createData(date, mSessionStartTime, INVALID_INT,
							INVALID_STRING, mLastAct, mLastPa);
				} else {
					mDbAdapter.createData(mLastHrDate, mSessionStartTime,
							mLastHr, mLastRr, mLastAct, mLastPa);
				}
			}
			intent.putExtra(EXTRA_ACT,
					String.valueOf(values.getActivity() + dateStr));
			intent.putExtra(EXTRA_PA, String.valueOf(values.getPa() + dateStr));
			intent.putExtra(EXTRA_DATA, dateStr + values.getInfo());
			if (mSessionInProgress && mSessionState == SESSION_WAITING_CUSTOM) {
				incrementSessionState();
			}
		} else if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {
			mLastBat = characteristic.getIntValue(
					BluetoothGattCharacteristic.FORMAT_UINT8, 0);
			Log.d(TAG, String.format("Received battery level: %d", mLastBat));
			intent.putExtra(EXTRA_BAT, String.valueOf(mLastBat) + dateStr);
			intent.putExtra(EXTRA_DATA,
					String.valueOf("Battery Level: " + mLastBat));
			if (mSessionInProgress && mSessionState == SESSION_WAITING_BAT) {
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
		HxMBleService getService() {
			return HxMBleService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "onBind");
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(TAG, "onUnbind");
		// After using a given device, you should make sure that
		// BluetoothGatt.close() is called
		// such that resources are cleaned up properly. In this particular
		// example, close() is
		// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		Log.d(TAG, "initialize");
		// For API level 18 and above, get a reference to BluetoothAdapter
		// through BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter");
			return false;
		}

		return true;
	}

	/**
	 * Starts writing to the database with the given adapter.
	 * 
	 * @param adapter
	 * @return
	 */
	public boolean startDatabase(HxMMonitorDbAdapter adapter) {
		Log.d(TAG, "startDatabase");
		mDbAdapter = adapter;
		if (mDbAdapter == null) {
			return false;
		}
		return true;
	}

	/**
	 * Stops writing to the the database.
	 */
	public void stopDatabase() {
		Log.d(TAG, "stopDatabase");
		mDbAdapter = null;
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
		Log.d(TAG, "connect");
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG,
					"connect: BluetoothAdapter not initialized or unspecified address");
			return false;
		}

		// Previously connected device. Try to reconnect.
		if (mBluetoothDeviceAddress != null
				&& address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {
			Log.d(TAG,
					"connect: Trying to use an existing mBluetoothGatt for connection");
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
			Log.w(TAG, "Device not found.  Unable to connect");
			return false;
		}
		// We want to directly connect to the device, so we are setting the
		// autoConnect parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection");
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
		Log.d(TAG, "disconnect");
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
		Log.d(TAG, "close");
		stopDatabase();
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
		boolean resSet, resWrite;
		if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
			// DEBUG
			Log.d(TAG,
					"setCharacteristicNotification for UUID_HEART_RATE_MEASUREMENT "
							+ enabled);
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
			resSet = descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			resWrite = mBluetoothGatt.writeDescriptor(descriptor);
			if (!resSet || !resWrite) {
				Log.d(TAG,
						"setCharacteristicNotification for UUID_HEART_RATE_MEASUREMENT resStart="
								+ resSet + " resWrite=" + resWrite);
			}
		}

		// This is specific to Custom Measurement
		if (UUID_CUSTOM_MEASUREMENT.equals(characteristic.getUuid())) {
			Log.d(TAG,
					"setCharacteristicNotification for UUID_CUSTOM_MEASUREMENT "
							+ enabled);
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
			resSet = descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			resWrite = mBluetoothGatt.writeDescriptor(descriptor);
			if (!resSet || !resWrite) {
				Log.d(TAG,
						"setCharacteristicNotification for UUID_CUSTOM_MEASUREMENT resStart="
								+ resSet + " resWrite=" + resWrite);
			}
		}

		// // This is specific to Battery Level
		// if (UUID_BATTERY_LEVEL.equals(characteristic.getUuid())) {
		// // DEBUG
		// Log.d(TAG, "setCharacteristicNotification for UUID_BATTERY_LEVEL "
		// + enabled);
		// BluetoothGattDescriptor descriptor = characteristic
		// .getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
		// resSet = descriptor
		// .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		// resWrite = mBluetoothGatt.writeDescriptor(descriptor);
		// if (!resSet || !resWrite) {
		// Log.d(TAG,
		// "setCharacteristicNotification for UUID_BATTERY_LEVEL resStart="
		// + resSet + " resWrite=" + resWrite);
		// }
		// }
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
		// DEBUG
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

	// /**
	// * Sets the state to SESSION_WAITING_BAT if there is a session in
	// progress.
	// *
	// * @return If successful.
	// */
	// public boolean checkBattery() {
	// Log.d(TAG, "checkBattery");
	// if (!mSessionInProgress || mCharBat == null) {
	// return false;
	// }
	// // Stop notifying for existing characteristics
	// if (mSessionInProgress = true && mCharHr != null) {
	// setCharacteristicNotification(mCharHr, false);
	// }
	// if (mSessionInProgress = true && mCharCustom != null) {
	// setCharacteristicNotification(mCharCustom, false);
	// }
	// mSessionState = SESSION_WAITING_BAT;
	// readCharacteristic(mCharBat);
	//
	// return true;
	// }

	/**
	 * Starts the timer for incrementing the session state if no value is
	 * received.
	 */
	private void startTimeoutTimer() {
		if (mTimeoutTimer != null) {
			stopTimeoutTimer();
		}
		mTimeoutTask = new TimerTask() {
			@Override
			public void run() {
				mTimeoutTimer = null;
				Log.d(TAG, "mTimeoutTimer: Timed out: state=" + mSessionState);
				if (mSessionInProgress) {
					if (mSessionState == SESSION_WAITING_CUSTOM) {
						if (mDbAdapter != null && mCharCustom == null) {
							mDbAdapter.createData(mLastHrDate,
									mSessionStartTime, mLastHr, mLastRr,
									INVALID_INT, INVALID_INT);
						}
						Log.d(TAG,
								"  mTimeoutTimer: SESSION_WAITING_CUSTOM: incrementSessionState:");
						incrementSessionState();
					} else if (mSessionState == SESSION_WAITING_BAT) {
						Log.d(TAG,
								"  mTimeoutTimer: SESSION_WAITING_BAT: incrementSessionState:");
						incrementSessionState();
					} else {
						Log.d(TAG, "  mTimeoutTimer: mSessionState="
								+ mSessionState);
					}
				}
			}

			// DEBUG
			@Override
			public boolean cancel() {
				Log.d(TAG, "mTimeoutTask cancelled");
				return super.cancel();
			}
		};
		Log.d(TAG, "New timer about to be constructed");
		try {
			mTimeoutTimer = new Timer();
			// // DEBUG
			// Log.d(TAG, "New timer about to be scheduled");
			mTimeoutTimer.schedule(mTimeoutTask, CHARACTERISTIC_TIMER_TIMEOUT);
			Log.d(TAG, "New timer scheduled");
		} catch (Exception ex) {
			Log.e(TAG, "Exception creating timeout timer: " + ex.getMessage());
		}
	}

	/**
	 * Stops the timer for incrementing the session state if no value is
	 * received. Does nothing if there is no timer.
	 */
	private void stopTimeoutTimer() {
		Log.d(TAG, "stopTimeoutTimer: ");
		if (mTimeoutTimer != null) {
			Log.d(TAG, "  mTimeoutTimer is not null");
			if (mTimeoutTask != null) {
				mTimeoutTask.cancel();
			}
			mTimeoutTimer.purge();
			mTimeoutTask = null;
			mTimeoutTimer.cancel();
			mTimeoutTimer = null;
		} else {
			Log.d(TAG, "  mTimeoutTimer is null");
		}
	}

	/**
	 * Returns if a session is in progress
	 * 
	 * @return
	 */
	public boolean getSessionInProgress() {
		return mSessionInProgress;
	}

	// /**
	// * Sets which updates are handled.
	// *
	// * @param flags
	// * One of DO_NOTHING, DO_BAT, DO_HR, or DO_CUSTOM.
	// */
	// void setEnabledFlags(int flags) {
	// Log.d(TAG, this.getClass().getSimpleName() + ".setEnabledFlags");
	// boolean doBat = (flags & DO_BAT) != 0;
	// boolean doHr = (flags & DO_HR) != 0;
	// boolean doCustom = (flags & DO_CUSTOM) != 0;
	// Log.d(TAG, "  mDoBat=" + mDoBat + " mDoHr=" + mDoHr + " mDoCustom="
	// + mDoCustom);
	// if (doBat == mDoBat && doHr == mDoHr && doCustom == mDoCustom) {
	// // No change
	// return;
	// }
	// mDoBat = doBat;
	// mDoHr = doHr;
	// mDoCustom = doCustom;
	// if (!mSessionInProgress) {
	// return;
	// }
	// if (mSessionState == SESSION_IDLE) {
	// incrementSessionState();
	// }
	// if (!mDoBat && mSessionState == SESSION_WAITING_BAT) {
	// incrementSessionState();
	// } else if (!mDoHr && mSessionState == SESSION_WAITING_HR) {
	// incrementSessionState();
	// } else if (!mDoCustom && mSessionState == SESSION_WAITING_CUSTOM) {
	// incrementSessionState();
	// }
	// }

	/**
	 * Starts a session.
	 * 
	 * @param charBat
	 * @param charHr
	 * @param charCustom
	 * @return If successful.
	 */
	public boolean startSession(BluetoothGattCharacteristic charBat,
			BluetoothGattCharacteristic charHr,
			BluetoothGattCharacteristic charCustom, boolean doBat,
			boolean doHr, boolean doCustom) {
		Log.d(TAG, "startSession: mSessionState=" + mSessionState
				+ " mTimeoutTimer=" + mTimeoutTimer);
		mDoBat = doBat;
		mDoHr = doHr;
		mDoCustom = doCustom;
		// DEBUG
		String batVal = mCharBat == null ? "null" : String.format("%8x",
				mCharBat.hashCode());
		String hrVal = mCharHr == null ? "null" : String.format("%8x",
				mCharHr.hashCode());
		String customVal = mCharCustom == null ? "null" : String.format("%8x",
				mCharCustom.hashCode());
		Log.d(TAG, "  mCharBat=" + batVal + " mCharHr=" + hrVal
				+ " mCharCustom=" + customVal);
		batVal = charBat == null ? "null" : String.format("%8x",
				charBat.hashCode());
		hrVal = charHr == null ? "null" : String.format("%8x",
				charHr.hashCode());
		customVal = charCustom == null ? "null" : String.format("%8x",
				charCustom.hashCode());
		Log.d(TAG, "  charBat=" + batVal + " charHr=" + hrVal + " charCustom="
				+ customVal);
		Log.d(TAG, "  mDoBat=" + mDoBat + " mDoHr=" + mDoHr + " mDoCustom="
				+ mDoCustom);
		boolean res = true;
		mSessionStartTime = new Date().getTime();

		// // DEBUG Check permissions
		// checkPermissions(charBat, charHr, charCustom);

		// Cancel a running timer
		stopTimeoutTimer();

		// Stop notifying for existing characteristics
		if (mCharHr != null) {
			setCharacteristicNotification(mCharHr, false);
		}
		if (mCharCustom != null) {
			setCharacteristicNotification(mCharCustom, false);
		}

		// Initialize for the new values
		mCharBat = charBat;
		mCharHr = charHr;
		mCharCustom = charCustom;
		mLastBat = INVALID_INT;
		mLastHr = INVALID_INT;
		mLastRr = INVALID_STRING;
		mLastHrDate = new Date().getTime();
		mLastAct = INVALID_INT;
		mLastPa = INVALID_INT;
		if (mCharBat != null && mDoBat) {
			mSessionState = SESSION_WAITING_BAT;
			startTimeoutTimer();
			readCharacteristic(mCharBat);
		} else if (mCharHr != null && mDoHr) {
			mSessionState = SESSION_WAITING_HR;
			setCharacteristicNotification(mCharHr, true);
		} else if (mCharCustom != null && mDoCustom) {
			mSessionState = SESSION_WAITING_CUSTOM;
			setCharacteristicNotification(mCharCustom, true);
		} else {
			mSessionState = SESSION_IDLE;
			res = false;
		}
		mSessionInProgress = res;
		Log.d(TAG, "  startSession new mSessionState=" + mSessionState);
		return res;
	}

	/**
	 * Stops a session.
	 */
	public void stopSession() {
		Log.d(TAG, "stopSession");
		// Stop the custom timer
		stopTimeoutTimer();
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
		mLastHr = -1;
		mLastRr = null;
		mSessionState = SESSION_IDLE;
		mSessionInProgress = false;
	}

	/**
	 * Performs the logic to set the next session state. The logic is to first
	 * read the BAT. On receiving a value for BAT, set notification for HR. On
	 * receiving a value for HR, set notification for Custom. On receiving a
	 * value for Custom, set notification for HR. The logic is modified if one
	 * or more of the Characteristics is null. The result is that it appears to
	 * get both the HR and Custom values for each update from the device. The
	 * Custom comes within .1 sec of the HR, and loops back to waiting for HR.
	 * This necessary because Android apparently allows only one request on the
	 * stack at a time.
	 */
	public void incrementSessionState() {
		// DEBUG
		Log.d(TAG, "incrementSessionState: mSessionState=" + mSessionState);
		stopTimeoutTimer();
		if (!mSessionInProgress) {
			mSessionState = SESSION_IDLE;
			return;
		}
		// DEBUG
		int oldSessionState = mSessionState;
		switch (mSessionState) {
		case SESSION_IDLE:
			mLastBat = INVALID_INT;
			mLastHr = INVALID_INT;
			mLastRr = INVALID_STRING;
			mLastHrDate = new Date().getTime();
			mLastAct = INVALID_INT;
			mLastPa = INVALID_INT;
			if (mCharHr != null) {
				// setCharacteristicNotification(mCharHr, false);
			}
			if (mCharCustom != null) {
				// setCharacteristicNotification(mCharCustom, false);
			}
			if (mCharBat != null && mDoBat) {
				mSessionState = SESSION_WAITING_BAT;
				startTimeoutTimer();
				readCharacteristic(mCharBat);
			} else if (mCharHr != null && mDoHr) {
				mSessionState = SESSION_WAITING_HR;
				setCharacteristicNotification(mCharHr, true);
			} else if (mCharCustom != null && mDoCustom) {
				mSessionState = SESSION_WAITING_CUSTOM;
				setCharacteristicNotification(mCharCustom, true);
			} else {
				mSessionState = SESSION_IDLE;
			}
			break;
		case SESSION_WAITING_BAT:
			if (mCharHr != null && mDoHr) {
				mSessionState = SESSION_WAITING_HR;
				if (mCharCustom != null) {
					// setCharacteristicNotification(mCharCustom, false);
				}
				setCharacteristicNotification(mCharHr, true);
			} else if (mCharCustom != null && mDoCustom) {
				mSessionState = SESSION_WAITING_CUSTOM;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				if (mCharHr != null) {
					// setCharacteristicNotification(mCharHr, false);
				}
				setCharacteristicNotification(mCharCustom, true);
			} else if (mCharBat != null && mDoBat) {
				mSessionState = SESSION_WAITING_BAT;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
				// startTimeoutTimer();
				// readCharacteristic(mCharBat);
			} else {
				mSessionState = SESSION_IDLE;
				mSessionState = SESSION_WAITING_BAT;
				mLastBat = INVALID_INT;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
			}
			// Else leave it as is
			break;
		case SESSION_WAITING_HR:
			// // DEBUG
			// Log.d(TAG,
			// "incrementSessionState: SESSION_WAITING_HR mTimeoutTimer="
			// + mTimeoutTimer);
			if (mCharCustom != null && mDoCustom) {
				mSessionState = SESSION_WAITING_CUSTOM;
				if (mCharHr != null) {
					// setCharacteristicNotification(mCharHr, false);
				}
				setCharacteristicNotification(mCharCustom, true);
				// Start a timer to go back to HR if no custom notifications
				// received
				startTimeoutTimer();
			} else if (mCharHr != null && mDoHr) {
				mSessionState = SESSION_WAITING_HR;
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
				setCharacteristicNotification(mCharHr, true);
			} else if (mCharBat != null && mDoBat) {
				mSessionState = SESSION_WAITING_BAT;
				mLastBat = INVALID_INT;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
				startTimeoutTimer();
				readCharacteristic(mCharBat);
			} else {
				mSessionState = SESSION_IDLE;
				mLastBat = INVALID_INT;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
			}
			// Else leave it as is
			break;
		case SESSION_WAITING_CUSTOM:
			// // DEBUG
			// Log.d(TAG,
			// "incrementSessionState: SESSION_WAITING_CUSTOM mTimeoutTimer="
			// + mTimeoutTimer);
			if (mCharHr != null && mDoHr) {
				mSessionState = SESSION_WAITING_HR;
				if (mCharCustom != null) {
					// setCharacteristicNotification(mCharCustom, false);
				}
				setCharacteristicNotification(mCharHr, true);
			} else if (mCharCustom != null && mDoCustom) {
				mSessionState = SESSION_WAITING_CUSTOM;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				if (mCharHr != null) {
					// setCharacteristicNotification(mCharHr, false);
				}
				setCharacteristicNotification(mCharCustom, true);
			} else if (mCharBat != null && mDoBat) {
				mSessionState = SESSION_WAITING_BAT;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
				startTimeoutTimer();
				readCharacteristic(mCharBat);
			} else {
				mSessionState = SESSION_IDLE;
				mLastBat = INVALID_INT;
				mLastHr = INVALID_INT;
				mLastRr = INVALID_STRING;
				mLastHrDate = new Date().getTime();
				mLastAct = INVALID_INT;
				mLastPa = INVALID_INT;
				if (mCharHr != null) {
					setCharacteristicNotification(mCharHr, false);
				}
				if (mCharCustom != null) {
					setCharacteristicNotification(mCharCustom, false);
				}
			}
			break;
		}
		// DEBUG
		Log.d(TAG, "incrementSessionState: oldState=" + oldSessionState
				+ " new State=" + mSessionState);
	}
}
