package net.kenevans.android.hxmmonitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

public class SessionManagerActivity extends ListActivity implements IConstants {
	private SessionListAdapter mSessionListAdapter;
	private HxMMonitorDbAdapter mDbAdapter;
	private File mDataDir;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// setContentView(R.layout.activity_session_manager);

		// Set result OK in case the user backs out
		setResult(Activity.RESULT_OK);

		// Get the database name from the default preferences
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String prefString = prefs.getString(PREF_DATA_DIRECTORY, null);
		if (prefString == null) {
			Utils.errMsg(this, "Cannot find the name of the data directory");
			return;
		}

		// Open the database
		mDataDir = new File(prefString);
		if (mDataDir == null) {
			Utils.errMsg(this, "Database directory is null");
			return;
		}
		if (!mDataDir.exists()) {
			Utils.errMsg(this, "Cannot find database directory: " + mDataDir);
			mDataDir = null;
			return;
		}
		mDbAdapter = new HxMMonitorDbAdapter(this, mDataDir);
		mDbAdapter.open();
	}

	@Override
	protected void onResume() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume");
		super.onResume();
		refresh();
	}

	// @Override
	// protected void onActivityResult(int requestCode, int resultCode, Intent
	// data) {
	// switch (requestCode) {
	// case REQUEST_SELECT_DEVICE_CODE:
	// if (resultCode == Activity.RESULT_OK) {
	// String deviceName = data.getStringExtra(DEVICE_NAME_CODE);
	// String deviceAddress = data.getStringExtra(DEVICE_ADDRESS_CODE);
	// // Use this instead of getPreferences to be application-wide
	// SharedPreferences.Editor editor = PreferenceManager
	// .getDefaultSharedPreferences(this).edit();
	// editor.putString(DEVICE_NAME_CODE, deviceName);
	// editor.putString(DEVICE_ADDRESS_CODE, deviceAddress);
	// editor.commit();
	// }
	// break;
	// }
	// super.onActivityResult(requestCode, resultCode, data);
	// }

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
		super.onPause();
		if (mSessionListAdapter != null) {
			mSessionListAdapter.clear();
		}
	}

	@Override
	protected void onDestroy() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
		super.onDestroy();
		if (mDbAdapter != null) {
			mDbAdapter.close();
			mDbAdapter = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_session_manager, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
		case R.id.menu_plot:
			plot();
			return true;
		case R.id.menu_discard:
			discard();
			return true;
		case R.id.menu_merge:
			merge();
			return true;
		case R.id.menu_split:
			split();
			return true;
		case R.id.menu_save:
			save();
			return true;
		case R.id.menu_refresh:
			refresh();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final Session session = mSessionListAdapter.getSession(position);
		if (session == null) {
			return;
		}
		// final Intent data = new Intent();
		// // data.putExtra(DEVICE_NAME_CODE, session.getName());
		// // data.putExtra(DEVICE_ADDRESS_CODE, session.getAddress());
		// setResult(RESULT_OK, data);
		// finish();
	}

	/**
	 * Calls the plot activity for the selected sessions.
	 */
	/**
	 * Calls the plot activity.
	 */
	public void plot() {
		ArrayList<Session> checkedSessions = mSessionListAdapter
				.getCheckedSessions();
		if (checkedSessions == null || checkedSessions.size() == 0) {
			Utils.errMsg(this, "There are no sessions to plot");
			return;
		}
		if (checkedSessions.size() > 1) {
			Utils.errMsg(this,
					"Only one session may be checked for this operation");
			return;
		}
		Session session = checkedSessions.get(0);
		long startDate = session.getStartDate();
		long endDate = session.getEndDate();
		Intent intent = new Intent(SessionManagerActivity.this,
				PlotActivity.class);
		// Plot the session
		intent.putExtra(PLOT_SESSION_CODE, true);
		intent.putExtra(PLOT_SESSION_START_TIME, startDate);
		intent.putExtra(PLOT_SESSION_END_TIME, endDate);
		startActivityForResult(intent, REQUEST_PLOT_CODE);
	}

	/**
	 * Merges the selected sessions.
	 */
	public void merge() {
		Utils.infoMsg(this, "Not implented yet");
	}

	/**
	 * Splits the selected sessions.
	 */
	public void split() {
		Utils.infoMsg(this, "Not implented yet");
	}

	/**
	 * Saves the selected sessions.
	 */
	public void save() {
		ArrayList<Session> checkedSessions = mSessionListAdapter
				.getCheckedSessions();
		if (checkedSessions == null || checkedSessions.size() == 0) {
			Utils.errMsg(this, "There are no sessions to discard");
			return;
		}
		if (mDataDir == null) {
			Utils.errMsg(this, "Cannot determine directory for save");
			return;
		}
		final String DELIM = ",";
		int nErrors = 0;
		String errMsg = "Error saving sessions:\n";
		String fileNames = "Saved to:\n";
		BufferedWriter out = null;
		Cursor cursor = null;
		String fileName = null;
		long startDate = Long.MIN_VALUE;
		long endDate = Long.MIN_VALUE;
		File file = null;
		FileWriter writer = null;
		for (Session session : checkedSessions) {
			startDate = session.getStartDate();
			endDate = session.getEndDate();
			fileName = session.getName() + ".csv";
			try {
				file = new File(mDataDir, fileName);
				writer = new FileWriter(file);
				out = new BufferedWriter(writer);
				cursor = mDbAdapter.fetchAllHrRrDateDataForTimes(startDate,
						endDate);
				int indexDate = cursor.getColumnIndex(COL_DATE);
				int indexHr = cursor.getColumnIndex(COL_HR);
				int indexRr = cursor.getColumnIndex(COL_RR);
				// Loop over items
				cursor.moveToFirst();
				String dateStr, hrStr, rrStr, line;
				long dateNum = Long.MIN_VALUE;
				while (cursor.isAfterLast() == false) {
					dateStr = "<Unknown>";
					if (indexDate > -1) {
						dateNum = cursor.getLong(indexDate);
						dateStr = sessionSaveFormatter
								.format(new Date(dateNum));
					}
					hrStr = "<Unknown>";
					if (indexHr > -1) {
						hrStr = cursor.getString(indexHr);
					}
					rrStr = "<Unknown>";
					if (indexHr > -1) {
						rrStr = cursor.getString(indexRr);
					}
					line = dateStr + DELIM + hrStr + DELIM + rrStr + "\n";
					out.write(line);
					cursor.moveToNext();
				}
				fileNames += "  " + file.getName() + "\n";
			} catch (Exception ex) {
				nErrors++;
				errMsg += "  " + session.getName();
			} finally {
				try {
					cursor.close();
				} catch (Exception ex) {
					// Do nothing
				}
				try {
					out.close();
				} catch (Exception ex) {
					// Do nothing
				}
			}
		}
		String msg = "Directory:\n" + mDataDir + "\n";
		if (nErrors > 0) {
			msg += errMsg;
		}
		msg += fileNames;
		if (nErrors > 0) {
			Utils.errMsg(this, msg);
		} else {
			Utils.infoMsg(this, msg);
		}
	}

	/**
	 * Prompts to discards the selected sessions. The method doDiscard will do
	 * the actual discarding, if the user confirms.
	 * 
	 * @see #doDiscard()
	 */
	public void discard() {
		ArrayList<Session> checkedSessions = mSessionListAdapter
				.getCheckedSessions();
		if (checkedSessions == null || checkedSessions.size() == 0) {
			Utils.errMsg(this, "There are no sessions to discard");
			return;
		}
		String msg = SessionManagerActivity.this.getString(
				R.string.session_delete_prompt, checkedSessions.size());
		new AlertDialog.Builder(SessionManagerActivity.this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.confirm)
				.setMessage(msg)
				.setPositiveButton(R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								doDiscard();
							}
						}).setNegativeButton(R.string.cancel, null).show();
	}

	/**
	 * Discards the selected sessions.
	 */
	public void doDiscard() {
		ArrayList<Session> checkedSessions = mSessionListAdapter
				.getCheckedSessions();
		if (checkedSessions == null || checkedSessions.size() == 0) {
			Utils.errMsg(this, "There are no sessions to discard");
			return;
		}
		long startDate = Long.MIN_VALUE, endDate = Long.MIN_VALUE;
		for (Session session : checkedSessions) {
			startDate = session.getStartDate();
			endDate = session.getEndDate();
			mDbAdapter.deleteAllDataForTimes(startDate, endDate);
			refresh();
		}
	}

	/**
	 * Refreshes the sessions by recreating the list adapter.
	 */
	public void refresh() {
		// Initialize the list view adapter
		mSessionListAdapter = new SessionListAdapter();
		setListAdapter(mSessionListAdapter);
	}

	/**
	 * Creates a session name form the given date.
	 * 
	 * @param date
	 * @return
	 */
	public static String sessionNameFromDate(long date) {
		return SESSION_NAME_PREFIX + fileNameFormatter.format(new Date(date));
	}

	// Adapter for holding sessions
	private class SessionListAdapter extends BaseAdapter {
		private ArrayList<Session> mSessions;
		private LayoutInflater mInflator;

		public SessionListAdapter() {
			super();
			mSessions = new ArrayList<Session>();
			mInflator = SessionManagerActivity.this.getLayoutInflater();
			Cursor cursor = null;
			int nItems = 0;
			try {
				if (mDbAdapter != null) {
					cursor = mDbAdapter.fetchAllSessionStartEndData();
					// // DEBUG
					// Log.d(TAG,
					// this.getClass().getSimpleName()
					// + ": SessionListAdapter: " + "rows="
					// + cursor.getCount() + " cols="
					// + cursor.getColumnCount());
					// String[] colNames = cursor.getColumnNames();
					// for (String colName : colNames) {
					// Log.d(TAG, "  " + colName);
					// }

					int indexStartDate = cursor
							.getColumnIndexOrThrow(COL_START_DATE);
					int indexEndDate = cursor.getColumnIndex(COL_END_DATE);
					// int indexTmp = cursor.getColumnIndex(COL_TMP);

					// Loop over items
					cursor.moveToFirst();
					long startDate = Long.MIN_VALUE;
					long endDate = Long.MIN_VALUE;
					String name;
					while (cursor.isAfterLast() == false) {
						nItems++;
						startDate = cursor.getLong(indexStartDate);
						endDate = cursor.getLong(indexEndDate);

						// // DEBUG
						// double duration = endDate - startDate;
						// int durationHours = (int) (duration / 3600000.);
						// int durationMin = (int) (duration / 60000.)
						// - durationHours * 60;
						// int durationSec = (int) (duration / 1000.)
						// - durationHours * 3600 - durationMin * 60;
						// Log.d(TAG, "duration: " + durationHours + " hr "
						// + durationMin + " min " + +durationSec + " sec");
						// name = "Temporary Session ";

						// String tempStr = cursor.getString(indexTmp);
						// temp = tempStr.equals("1");
						// name = "Session ";
						// if (temp) {
						// name = "Temporary Session ";
						// }
						name = sessionNameFromDate(startDate);
						addSession(new Session(name, startDate, endDate));
						cursor.moveToNext();
					}
				}
				cursor.close();
			} catch (Exception ex) {
				Utils.excMsg(SessionManagerActivity.this,
						"Error getting sessions", ex);
			} finally {
				try {
					cursor.close();
				} catch (Exception ex) {
					// Do nothing
				}
			}
			Log.d(TAG, "Session list created with " + nItems + " items");
		}

		public void addSession(Session session) {
			if (!mSessions.contains(session)) {
				mSessions.add(session);
			}
		}

		public Session getSession(int position) {
			return mSessions.get(position);
		}

		public void clear() {
			mSessions.clear();
		}

		@Override
		public int getCount() {
			return mSessions.size();
		}

		@Override
		public Object getItem(int i) {
			return mSessions.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder = null;
			// General ListView optimization code.
			if (view == null) {
				view = mInflator.inflate(R.layout.listitem_session, viewGroup,
						false);
				viewHolder = new ViewHolder();
				viewHolder.sessionCheckbox = (CheckBox) view
						.findViewById(R.id.session_checkbox);
				viewHolder.sessionStart = (TextView) view
						.findViewById(R.id.session_start);
				viewHolder.sessionDuration = (TextView) view
						.findViewById(R.id.session_end);
				view.setTag(viewHolder);

				viewHolder.sessionCheckbox
						.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								CheckBox cb = (CheckBox) v;
								Session session = (Session) cb.getTag();
								boolean checked = cb.isChecked();
								session.setChecked(checked);
							}
						});
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			Session session = mSessions.get(i);
			final String sessionName = session.getName();
			if (sessionName != null && sessionName.length() > 0) {
				viewHolder.sessionCheckbox.setText(sessionName);
				Date startDate = new Date(session.getStartDate());
				String startStr = mediumFormatter.format(startDate);
				double duration = session.getDuration();
				int durationDays = (int) (duration / (3600000. * 24));

				int durationHours = (int) (duration / 3600000.) - durationDays;
				int durationMin = (int) (duration / 60000.) - durationHours
						* 60;
				int durationSec = (int) (duration / 1000.) - durationHours
						* 3600 - durationMin * 60;
				String durString = "";
				if (durationDays > 0) {
					durString += durationDays + " day ";
				}
				if (durationHours > 0) {
					durString += durationHours + " hr ";
				}
				if (durationMin > 0) {
					durString += durationMin + " min ";
				}
				durString += durationSec + " sec";
				viewHolder.sessionStart.setText(startStr);
				viewHolder.sessionDuration.setText(durString);
			} else {
				viewHolder.sessionCheckbox.setText(R.string.unknown_device);
				viewHolder.sessionStart.setText("");
				viewHolder.sessionDuration.setText("");
			}
			// Set the tag for the CheckBox to the session and set its state
			viewHolder.sessionCheckbox.setSelected(session.isChecked());
			viewHolder.sessionCheckbox.setTag(session);
			return view;
		}

		/**
		 * Get a list of checked sessions.
		 * 
		 * @return
		 */
		public ArrayList<Session> getCheckedSessions() {
			ArrayList<Session> checkedSessions = new ArrayList<Session>();
			for (Session session : mSessions) {
				if (session.isChecked()) {
					checkedSessions.add(session);
				}
			}
			return checkedSessions;
		}

	}

	static class ViewHolder {
		CheckBox sessionCheckbox;
		TextView sessionStart;
		TextView sessionDuration;
	}

}
