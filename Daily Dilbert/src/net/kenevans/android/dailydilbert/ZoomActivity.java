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

package net.kenevans.android.dailydilbert;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class ZoomActivity extends Activity implements IConstants {
	private ScrollImageView mImageView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Remove title bar (Call before setContentView)
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Remove notification bar (Call before setContentView)
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.zoom);
		mImageView = (ScrollImageView) findViewById(R.id.imageview);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Get the cDay values
		Bundle extras = getIntent().getExtras();
		int year = extras != null ? extras.getInt(YEAR) : -1;
		int month = extras != null ? extras.getInt(MONTH) : -1;
		int day = extras != null ? extras.getInt(DAY) : -1;
		CalendarDay cDay = null;
		if (year != -1 && month != -1 && day != -1) {
			cDay = new CalendarDay(year, month, day);
			cDay.set(year, month, day);
		}
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume: cDay=" + cDay);
		if (cDay == null) {
			Utils.errMsg(this, "Invalid day");
			return;
		}
		Bitmap bitmap = DailyDilbertActivity.getCachedBitmap(this, cDay);
		if (bitmap == null) {
			Utils.errMsg(this, "Could not get bitmap");
			return;
		}
		mImageView.setBitmap(bitmap);
		mImageView.invalidate();
	}

}
