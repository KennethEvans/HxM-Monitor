package net.kenevans.android.touchimage;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public class TouchImageActivity extends Activity implements IConstants {
	private TouchImageView mImageView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
		super.onCreate(savedInstanceState);

		// Remove title bar (Call before setContentView)
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Remove notification bar (Call before setContentView)
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.main);
		mImageView = (TouchImageView) findViewById(R.id.imageview);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
		case R.id.test:
			setNewImage();
			return true;
		case R.id.reset:
			reset();
			return true;
		}
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume:");
		
		// Restore the state
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		String fileName = prefs.getString(PREF_FILENAME, null);
		Log.d(TAG, "  fileName=" + fileName);
		if (fileName == null) {
			mImageView.setImageResource(R.drawable.test);
		} else {
			setNewImage();
		}
		mImageView.setFitImageMode(TouchImageView.IMAGEFITTED
				| TouchImageView.IMAGECENTERED);
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onPause:");
		super.onPause();
	}

	/**
	 * Get a Bitmap from a file. Static version that can be called by other
	 * Activities.
	 * 
	 * @param context
	 *            The context to use.
	 * @param cDay
	 * @return The Bitmap or null on failure.
	 */
	public static Bitmap getBitmap(Context context, File file) {
		Log.d(TAG, context.getClass().getSimpleName() + ": getBitmap");
		Bitmap bitmap = null;
		try {
			bitmap = BitmapFactory.decodeFile(file.getPath());
			if (bitmap == null) {
				Log.d(TAG, context.getClass().getSimpleName()
						+ ": getBitmap: Bitmap is null");
			} else {
				Log.d(TAG, context.getClass().getSimpleName()
						+ ": getBitmap: Got " + file.getPath());
			}
		} catch (Exception ex) {
			Log.d(TAG, context.getClass().getSimpleName()
					+ ": Error reading image", ex);
			// Utils.excMsg(this, "Error saving to SD card", ex);
		}
		return bitmap;
	}
	
	/**
	 * Resets to using the default image.
	 */
	private void reset() {
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putString(PREF_FILENAME, null);
		editor.commit();
		mImageView.setImageResource(R.drawable.test);
		mImageView.setFitImageMode(TouchImageView.IMAGEFITTED
				| TouchImageView.IMAGECENTERED);
	}

	/**
	 * Sets a new image.
	 */
	private void setNewImage() {
		if (mImageView == null) {
			return;
		}
		File sdCardRoot = Environment.getExternalStorageDirectory();
		File dir = new File(sdCardRoot, DEBUG_DIRNAME);
		File file = new File(dir, DEBUG_FILENAME);
		if (!file.exists()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": File does not exist " + file.getPath());
			return;
		}
		Bitmap bitmap = getBitmap(this, file);
		if (bitmap != null) {
			// Save the value here
			SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
			editor.putString(PREF_FILENAME, file.getPath());
			editor.commit();
			mImageView.setImageBitmap(bitmap);
			mImageView.fitImage();
			mImageView.setFitImageMode(TouchImageView.IMAGEFITTED
					| TouchImageView.IMAGECENTERED);
			mImageView.forceLayout();
		}
	}

}