package net.kenevans.android.senseviewviewer;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends ListActivity implements IConstants {
	private static File[] mFiles;
	private ArrayList<String> mFileNameList = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		// Set the file list
		reset();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id) {
		case R.id.set_file_directory:
			setFileDirectory();
			return true;
		case R.id.action_settings:
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case DirectoryPickerActivity.PICK_DIRECTORY:
			if (resultCode == RESULT_OK) {
				Bundle extras = data.getExtras();
				String path = (String) extras
						.get(DirectoryPickerActivity.CHOSEN_DIRECTORY);
				// Set the preference and call reset
				SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
						.edit();
				editor.putString(PREF_FILE_DIRECTORY, path);
				editor.commit();
				reset();
			}
			break;
		case PLOT_SELECT_CODE:
			String msg = null;
//			Bundle extras = data.getExtras();
//			if (extras != null) {
//				msg = extras.getString(MSG_CODE, null);
//			}
			if (resultCode == RESULT_OK) {
			} else if (resultCode == RESULT_BAD_FILE) {
				if (msg != null) {
					Utils.errMsg(this, msg);
				} else {
					Utils.errMsg(this, "Bad file");
				}
			} else if (resultCode == RESULT_ERROR) {
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
	 * Gets the current file directory.
	 * 
	 * @return
	 */
	private File getFileDirectory() {
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		String fileDirName = prefs.getString(PREF_FILE_DIRECTORY, null);
		File fileDir = null;
		if (fileDirName != null) {
			fileDir = new File(fileDirName);
		}
		if (fileDir == null) {
			Utils.errMsg(this, "File directory is null");
			return null;
		}
		if (!fileDir.exists()) {
			Utils.errMsg(this, "Cannot find directory: " + fileDir);
			return null;
		}
		return fileDir;
	}

	public static String getPathFromUri(Context context, Uri uri)
			throws URISyntaxException {
		if ("content".equalsIgnoreCase(uri.getScheme())) {
			String[] projection = { "_data" };
			Cursor cursor = null;

			try {
				cursor = context.getContentResolver().query(uri, projection,
						null, null, null);
				int column_index = cursor.getColumnIndexOrThrow("_data");
				if (cursor.moveToFirst()) {
					return cursor.getString(column_index);
				}
			} catch (Exception e) {
				// Eat it
			}
		} else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * Sets the current image directory
	 * 
	 * @return
	 */
	private void setFileDirectory() {
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		String fileDirName = prefs.getString(PREF_FILE_DIRECTORY, null);

		Intent intent = new Intent(this, DirectoryPickerActivity.class);
		intent.putExtra(DirectoryPickerActivity.ONLY_DIRS, true);
		// Use root as the backup
		intent.putExtra(DirectoryPickerActivity.START_DIR, "/");
		// Use the current file directory if it exists
		if (fileDirName != null) {
			File fileDir = new File(fileDirName);
			if (fileDir.exists() && fileDir.isDirectory()) {
				intent.putExtra(DirectoryPickerActivity.START_DIR, fileDirName);
			}
		}
		startActivityForResult(intent, DirectoryPickerActivity.PICK_DIRECTORY);
	}

	/**
	 * Resets the file list.
	 */
	private void reset() {
		// Get the available files
		try {
			// Clear the current list
			mFileNameList.clear();
			File dir = getFileDirectory();
			if (dir != null) {
				File[] files = dir.listFiles();
				List<File> fileList = new ArrayList<File>();
				for (File file : files) {
					if (!file.isDirectory()) {
						String ext = Utils.getExtension(file);
						if (ext.equals("csv")) {
							fileList.add(file);
							mFileNameList.add(file.getPath());
						}
					}
				}
				mFiles = new File[fileList.size()];
				fileList.toArray(mFiles);
			} else {
				mFiles = new File[0];
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Failed to get list of available files", ex);
		}

		// Set the ListAdapter
		ArrayAdapter<String> fileList = new ArrayAdapter<String>(this,
				R.layout.row, mFileNameList);
		setListAdapter(fileList);
		ListView lv = getListView();
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,
					long id) {
				if (pos < 0 || pos >= mFiles.length) {
					return;
				}
				Intent intent = new Intent(MainActivity.this,
						PlotActivity.class);
				String path = "";
				path = mFileNameList.get(pos);
				intent.putExtra(OPEN_FILE_PATH_CODE, path);
				startActivityForResult(intent, PLOT_SELECT_CODE);
			}
		});
	}

	/**
	 * @return
	 */
	public static File[] getFiles() {
		return mFiles;
	}

}
