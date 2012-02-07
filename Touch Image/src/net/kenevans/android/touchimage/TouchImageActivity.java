package net.kenevans.android.touchimage;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class TouchImageActivity extends Activity implements IConstants {
	private TouchImageView mImageView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
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
	protected void onResume() {
		super.onResume();
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume:");
		// Call this first
		mImageView.setImageResource(R.drawable.test);
		mImageView.setFitImageMode(TouchImageView.IMAGEFITTED
				| TouchImageView.IMAGECENTERED);
	}

}