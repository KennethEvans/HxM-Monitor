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
	public static final String PREF_ = "dataDirectory";

	// Information
	/** Key for information URL sent to InfoActivity. */
	public static final String INFO_URL = "InformationURL";

	// Database
	/** Database column for the id. Identifies the row. */
	public static final String COL_ID = "_id";
	/** Database column for the date. */
	public static final String COL_DATE = "date";
	/** Database column for the start date. */
	public static final String COL_START_DATE = "strartdate";
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
	public static final int REQUEST_SELECT_DEVICE = 10;
	/** Request code for enabling Bluetooth. */
	public static final int REQUEST_ENABLE_BT = 11;
	/** Request code for test. */
	public static final int REQUEST_TEST = 12;

	/** Request code for displaying a message. */
	public static final int DISPLAY_MESSAGE = 0;
	/**
	 * Result code for ACTIVITY_DISPLAY_MESSAGE indicating the previous message.
	 */
	public static final int RESULT_PREV = 1000;
	/**
	 * Result code for ACTIVITY_DISPLAY_MESSAGE indicating the next message.
	 */
	public static final int RESULT_NEXT = 1001;

	/** The intent code for device name. */
	public static final String DEVICE_NAME_CODE = PACKAGE_NAME + "deviceName";
	/** The intent code for device address. */
	public static final String DEVICE_ADDRESS_CODE = PACKAGE_NAME
			+ "deviceAddress";
	/** The intent code for extra data. */
	public final static String EXTRA_DATA = PACKAGE_NAME + ".extraData";
	/** The intent code for UUID. */
	public final static String EXTRA_UUID = PACKAGE_NAME + ".extraUuid";

	/** The static format string to use for formatting dates. */
	// public static final String longFormat = "MMM dd, yyyy HH:mm:ss Z";
	public static final String longFormat = "hh:mm a MMM dd, yyyy";
	public static final SimpleDateFormat longFormatter = new SimpleDateFormat(
			longFormat, Locale.US);

	/** The static format string to use for formatting dates. */
	public static final String mediumFormat = "MMM dd, yyyy HH:mm:ss";
	public static final SimpleDateFormat mediumFormatter = new SimpleDateFormat(
			mediumFormat, Locale.US);

	/** The static short format string to use for formatting dates. */
	public static final String shortFormat = "M/d/yy h:mm a";
	public static final SimpleDateFormat shortFormatter = new SimpleDateFormat(
			shortFormat, Locale.US);

}
