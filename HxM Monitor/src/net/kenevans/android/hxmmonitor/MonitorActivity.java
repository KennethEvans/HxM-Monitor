package net.kenevans.android.hxmmonitor;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
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
		switch (id) {
		case R.id.action_settings:
			Utils.infoMsg(this, "Not supported yet");
			return true;
		case R.id.menu_select_device:
			startScan();
			return true;
		}
		return true;
	}

	/**
	 * Call and Activity to scan for devices.
	 */
	void startScan() {
//		Utils.infoMsg(this, "startScan");
		Intent intent = new Intent(this, DeviceScanActivity.class);
		startActivityForResult(intent, REQUEST_SELECT_DEVICE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_SELECT_DEVICE) {
			if (resultCode == Activity.RESULT_OK) {
				// TODO
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

}
