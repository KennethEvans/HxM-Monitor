//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.hxmmonitor;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
	/** Tag to associate with log messages. */
	public static final String TAG = "HxM Monitor";
	/** Name of the package for this application. */
	public static final String PACKAGE_NAME = "net.kenevans.android.hxmmonitor";

	// Base
	/** Base string for standard UUIDS. These UUIDs differ in characters 4-7. */
	public static final String BASE_UUID = "00000000-0000-1000-8000-00805f9b34fb";

	// Services
	public static final UUID UUID_BATTERY_SERVICE = UUID
			.fromString("0000180f-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_DEVICE_INFORMATION_SERVICE = UUID
			.fromString("0000180a-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_HEART_RATE_SERVICE = UUID
			.fromString("0000180d-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_HXM_CUSTOM_DATA_SERVICE = UUID
			.fromString("befdff10-c979-11e1-9b21-0800200c9a66");

	// Characteristics
	public static final UUID UUID_BATTERY_LEVEL = UUID
			.fromString("00002a19-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_CUSTOM_MEASUREMENT = UUID
			.fromString("befdff11-c979-11e1-9b21-0800200c9a66");
	public static final UUID UUID_DEVICE_NAME = UUID
			.fromString("00002a00-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_HEART_RATE_MEASUREMENT = UUID
			.fromString("00002a37-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_TEST_MODE = UUID
			.fromString("befdffb1-c979-11e1-9b21-0800200c9a66");
	public static final UUID UUID_BODY_SENSOR_LOCATION = UUID
			.fromString("00002a38-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_MODEL_NUMBER_STRING = UUID
			.fromString("00002a24-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_FIRMWARE_REVISION_STRING = UUID
			.fromString("00002a26-0000-1000-8000-00805f9b34fb");
	public static final UUID UUID_APPEARANCE = UUID
			.fromString("00002a01-0000-1000-8000-00805f9b34fb");

	public static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID
			.fromString("00002902-0000-1000-8000-00805f9b34fb");

	/** Directory on the SD card where the database is stored */
	public static final String SD_CARD_DB_DIRECTORY = "HxM Monitor";
	/**
	 * Name of the file that will be restored. It would typically be a file that
	 * was previously saved and then renamed.
	 */
	public static final String RESTORE_FILE_NAME = "restore.txt";
	/** Simple name of the database. */
	public static final String DB_NAME = "HxMMonitor.db";
	/** Simple name of the table. */
	public static final String DB_TABLE = "data";
	/** The database version */
	public static final int DB_VERSION = 1;

	// Preferences
	public static final String PREF_DATA_DIRECTORY = "dataDirectory";

	// Information
	/** Key for information URL sent to InfoActivity. */
	public static final String INFO_URL = "InformationURL";

	// Session state
	public static final int SESSION_IDLE = 0;
	public static final int SESSION_WAITING_BAT = 1;
	public static final int SESSION_WAITING_HR = 2;
	public static final int SESSION_WAITING_CUSTOM = 3;

	// Timer
	/** Timer timeout for accumulating characteristics (ms). */
	public static final long CHARACTERISTIC_TIMER_TIMEOUT = 100;
	/** Timer interval for accumulating characteristics (ms). */
	public static final long CHARACTERISTIC_TIMER_INTERVAL = 1;

	// Database
	/** Database column for the id. Identifies the row. */
	public static final String COL_ID = "_id";
	/** Database column for the date. */
	public static final String COL_DATE = "date";
	/** Database column for the start date. */
	public static final String COL_START_DATE = "startdate";
	/** Database column for the heart rate. */
	public static final String COL_HR = "hr";
	/** Database column for the R-Rl. */
	public static final String COL_RR = "rr";
	/** Database column for the activity. */
	public static final String COL_ACTIVITY = "activity";
	/** Database column for the activity. */
	public static final String COL_PA = "peakacceleration";
	/** Database column for the temporary flag. */
	public static final String COL_TMP = "temporary";

	/** SQL sort command for date ascending */
	public static final String SORT_ASCENDING = COL_DATE + " ASC";

	/** Default scan period for device scan. */
	public static final long SCAN_PERIOD = 10000;

	// Messages
	/** Request code for selecting a device. */
	public static final int REQUEST_SELECT_DEVICE_CODE = 10;
	/** Request code for enabling Bluetooth. */
	public static final int REQUEST_ENABLE_BT_CODE = 11;
	/** Request code for test. */
	public static final int REQUEST_TEST_CODE = 12;
	/** Request code for plotting. */
	public static final int REQUEST_PLOT_CODE = 13;

	/** Request code for displaying a message. */
	public static final int DISPLAY_MESSAGE = 0;

	/** The intent code for device name. */
	public static final String DEVICE_NAME_CODE = PACKAGE_NAME + "deviceName";
	/** The intent code for device address. */
	public static final String DEVICE_ADDRESS_CODE = PACKAGE_NAME
			+ "deviceAddress";
	/** The intent code for extra data. */
	public final static String EXTRA_DATA = PACKAGE_NAME + ".extraData";
	/** The intent code for UUID. */
	public final static String EXTRA_UUID = PACKAGE_NAME + ".extraUuid";
	/** The intent code for the date. */
	public final static String EXTRA_DATE = PACKAGE_NAME + ".extraDate";
	/** The intent code for the heart rate. */
	public final static String EXTRA_HR = PACKAGE_NAME + ".extraHr";
	/** The intent code for the R-R values. */
	public final static String EXTRA_RR = PACKAGE_NAME + ".extraRr";
	/** The intent code for the activity value. */
	public final static String EXTRA_ACT = PACKAGE_NAME + ".extraAct";
	/** The intent code for the peak acceleration. */
	public final static String EXTRA_PA = PACKAGE_NAME + ".extraPa";
	/** The intent code for the battery level. */
	public final static String EXTRA_BAT = PACKAGE_NAME + ".extraBattery";
	/** The intent code for a message. */
	public final static String EXTRA_MSG = PACKAGE_NAME + ".extraMessage";

	// Messages
	/** Intent code for a message. */
	public static final String MSG_CODE = "MessageCode";

	// Result codes
	/** Result code for an error. */
	public static final int RESULT_ERROR = 1001;

	/** The static long formatter to use for formatting dates. */
	public static final SimpleDateFormat longFormatter = new SimpleDateFormat(
			"MMM dd, yyyy HH:mm:ss Z", Locale.US);
	// public static final SimpleDateFormat longFormatter = new
	// SimpleDateFormat(
	// "hh:mm a MMM dd, yyyy", Locale.US);

	/** The static formatter to use for formatting dates. */
	public static final SimpleDateFormat mediumFormatter = new SimpleDateFormat(
			"MMM dd, yyyy HH:mm:ss", Locale.US);

	/** The static short formatter to use for formatting dates. */
	public static final SimpleDateFormat shortFormatter = new SimpleDateFormat(
			"M/d/yy h:mm a", Locale.US);

	/** The static millisecond time formatter to use for formatting dates. */
	public static final SimpleDateFormat millisecTimeFormater = new SimpleDateFormat(
			"hh:mm.ss.SSS", Locale.US);

}
