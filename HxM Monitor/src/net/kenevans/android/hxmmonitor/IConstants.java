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

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
	// Log tag
	/** Tag to associate with log messages. */
	public static final String TAG = "HxM Monitor";

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

	// Messages
	/** Request code for creating new data. */
	public static final int ACTIVITY_CREATE = 0;
	/** Request code for editing data. */
	public static final int ACTIVITY_EDIT = 1;

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
