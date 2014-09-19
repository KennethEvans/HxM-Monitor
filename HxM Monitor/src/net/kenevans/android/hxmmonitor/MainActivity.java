package net.kenevans.android.hxmmonitor;

import java.io.File;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends Activity {
	private HxMMonitorDbAdapter mDbHelper;
	private File mDataDir;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
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
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
