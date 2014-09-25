package net.kenevans.android.hxmmonitor;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MonitorActivity extends Activity implements IConstants {
	private HxMMonitorDbAdapter mDbHelper;
	private File mDataDir;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
		TextView textView = (TextView) findViewById(R.id.Hello);
		textView.setText(this.getDatabasePath(getExternalFilesDir(null)
				.getPath())
				+ "\n"
				+ Environment.getExternalStorageDirectory().getPath()
				+ "\n"
				+ Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));

		String state = Environment.getExternalStorageState();
		mDataDir = null;
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			mDataDir = this.getExternalFilesDir(null);
		}
		if (mDataDir == null) {
			Utils.errMsg(
					this,
					"This application needs external storage to store the database, but none was found.");
			return;
		}

		// Open the database
		mDataDir = this.getExternalFilesDir(null);
		mDbHelper = new HxMMonitorDbAdapter(this, mDataDir);
		mDbHelper.open();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_monitor, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		Intent intent;
		switch (id) {
		case R.id.action_settings:
			Utils.infoMsg(this, "Not supported yet");
			return true;
		case R.id.menu_select_device:
			intent = new Intent(this, DeviceScanActivity.class);
			startActivityForResult(intent, REQUEST_SELECT_DEVICE_CODE);
			return true;
		case R.id.menu_test:
			intent = new Intent(this, DeviceMonitorActivity.class);
			startActivityForResult(intent, REQUEST_TEST_CODE);
			return true;
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_SELECT_DEVICE_CODE) {
			if (resultCode == Activity.RESULT_OK) {
				String deviceName = data.getStringExtra(DEVICE_NAME_CODE);
				String deviceAddress = data.getStringExtra(DEVICE_ADDRESS_CODE);
				// Use this instead of getPreferences to be application-wide
				SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this)
						.edit();
				editor.putString(DEVICE_NAME_CODE, deviceName);
				editor.putString(DEVICE_ADDRESS_CODE, deviceAddress);
				editor.commit();
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

}
