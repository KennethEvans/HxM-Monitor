package net.kenevans.android.hxmmonitor;

import java.io.File;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Simple database access helper class, modified from the Notes example
 * application.
 */
public class HxMMonitorExtendedDbAdapter implements IConstants {
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;
	private final Context mContext;
	private File mDataDir;
	/** Database column for the temporary flag. */
	public static final String COL_TMP = "temporary";

	/** Database creation SQL statement */
	private static final String DB_CREATE = "create table " + DB_DATA_TABLE
			+ " (_id integer primary key autoincrement, " + COL_DATE
			+ " integer not null, " + COL_START_DATE + " integer not null, "
			+ COL_TMP + " integer not null, " + COL_HR + " integer not null, "
			+ COL_RR + " text not null, " + COL_ACT + " real not null,"
			+ COL_PA + " real not null);";

	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param context
	 *            The context.
	 * @param dataDir
	 *            The location of the data.
	 */
	public HxMMonitorExtendedDbAdapter(Context context, File dataDir) {
		mContext = context;
		mDataDir = dataDir;
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new instance
	 * of the database. If it cannot be created, throw an exception to signal
	 * the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException
	 *             if the database could be neither opened or created
	 */
	public HxMMonitorExtendedDbAdapter open() throws SQLException {
		// Make sure the directory exists and is available
		if (mDataDir == null) {
			Utils.errMsg(mContext, "Cannot access database");
			return null;
		}
		try {
			if (!mDataDir.exists()) {
				mDataDir.mkdirs();
				// Try again
				if (!mDataDir.exists()) {
					Utils.errMsg(mContext,
							"Unable to create database directory at "
									+ mDataDir);
					return null;
				}
			}
			mDbHelper = new DatabaseHelper(mContext, mDataDir.getPath()
					+ File.separator + DB_NAME);
			mDb = mDbHelper.getWritableDatabase();
			// String info = mDb.toString() + "\n";
			// info += "mDataDir=" + mDataDir.getPath() + "\n";
			// info += "mDb.getPath()=" + mDb.getPath() + "\n";
			// info += "mDb.isOpen()=" + mDb.isOpen() + "\n";
			// File file = new File(mDb.getPath()).getParentFile();
			// File[] files = file.listFiles();
			// if (files == null) {
			// info += "files is  null" + "\n";
			// } else {
			// for (File file1 : files) {
			// info += "     " + file1.getName() + "\n";
			// }
			// }
			// // mDb.close();
			// // boolean res = SQLiteDatabase.deleteDatabase(file);
			// // info += "res=" + res + "\n";
			// Utils.infoMsg(mContext, info);
		} catch (Exception ex) {
			Utils.excMsg(mContext, "Error opening database at " + mDataDir, ex);
		}
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	/**
	 * Create new data using the parameters provided. If the data is
	 * successfully created return the new rowId for that entry, otherwise
	 * return a -1 to indicate failure.
	 * 
	 * @param rowId
	 * @param date
	 * @param startDate
	 * @param tmp
	 * @param hr
	 * @param rr
	 * @param activity
	 * @param pa
	 * @return
	 */
	public long createData(long rowId, long date, long startDate, boolean tmp,
			int hr, String rr, double activity, double pa) {
		if (mDb == null) {
			Utils.errMsg(mContext, "Failed to create data. Database is null.");
			return -1;
		}
		ContentValues values = new ContentValues();
		values.put(COL_DATE, date);
		values.put(COL_START_DATE, startDate);
		values.put(COL_TMP, tmp ? 1 : 0);
		values.put(COL_HR, hr);
		values.put(COL_RR, rr);
		values.put(COL_ACT, activity);
		values.put(COL_PA, pa);

		return mDb.insert(DB_DATA_TABLE, null, values);
	}

	/**
	 * Delete the data with the given rowId
	 * 
	 * @param rowId
	 *            id of data to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteData(long rowId) {
		return mDb.delete(DB_DATA_TABLE, COL_ID + "=" + rowId, null) > 0;
	}

	/**
	 * Delete all the data and recreate the table.
	 * 
	 * @return true if deleted, false otherwise
	 */
	public void recreateTable() {
		mDb.execSQL("DROP TABLE IF EXISTS " + DB_DATA_TABLE);
		mDb.execSQL(DB_CREATE);
	}

	/**
	 * Return a Cursor over the list of all notes in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllData(String filter) {
		if (mDb == null) {
			return null;
		}
		return mDb.query(DB_DATA_TABLE,
				new String[] { COL_ID, COL_DATE, COL_START_DATE, COL_TMP,
						COL_HR, COL_RR, COL_ACT, COL_PA }, filter, null,
				null, null, SORT_ASCENDING);
	}

	/**
	 * Return a Cursor positioned at the data that matches the given rowId
	 * 
	 * @param rowId
	 *            id of entry to retrieve
	 * @return Cursor positioned to matching entry, if found
	 * @throws SQLException
	 *             if entry could not be found/retrieved
	 */
	public Cursor fetchData(long rowId) throws SQLException {
		Cursor mCursor = mDb.query(true, DB_DATA_TABLE,
				new String[] { COL_ID, COL_DATE, COL_START_DATE, COL_HR,
						COL_RR, COL_ACT, COL_PA }, COL_ID + "=" + rowId,
				null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Update the data using the details provided. The data to be updated is
	 * specified using the rowId, and it is altered to use the values passed in.
	 * 
	 * @param rowId
	 * @param date
	 * @param startDate
	 * @param tmp
	 * @param hr
	 * @param rr
	 * @param activity
	 * @param pa
	 * @param comment
	 * @return
	 */
	public boolean updateData(long rowId, long date, long startDate,
			boolean tmp, int hr, String rr, double activity, double pa) {
		ContentValues values = new ContentValues();
		values.put(COL_DATE, date);
		values.put(COL_START_DATE, startDate);
		values.put(COL_TMP, tmp ? 1 : 0);
		values.put(COL_HR, hr);
		values.put(COL_RR, rr);
		values.put(COL_ACT, activity);
		values.put(COL_PA, pa);

		return mDb.update(DB_DATA_TABLE, values, COL_ID + "=" + rowId, null) > 0;
	}

	/**
	 * A SQLiteOpenHelper helper to help manage database creation and version
	 * management. Extends a custom version that writes to the SD Card instead
	 * of using the Context.
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {
		public DatabaseHelper(Context context, String dir) {
			super(context, dir, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DB_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Re-do this so nothing is lost if there is a need to change
			// the version
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + DB_DATA_TABLE);
			onCreate(db);
		}
	}

}
