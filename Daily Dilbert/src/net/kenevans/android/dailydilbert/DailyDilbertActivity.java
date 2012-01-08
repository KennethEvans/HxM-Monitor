package net.kenevans.android.dailydilbert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.DatePicker;

public class DailyDilbertActivity extends Activity implements IConstants {
	private static final String urlPrefix = "http://www.dilbert.com";
	private static final String dateUrlPrefix = "http://www.dilbert.com/strips/comic/";
	/** The first available strip is on April 16, 1989. */
	private static final CalendarDay cDayFirst = new CalendarDay(1989, 3, 16);
	private static final float scaleFactor = .99f;
	private CalendarDay cDay = CalendarDay.invalid();
	private Bitmap bitmap;

	private Panel mPanel;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: cDay=" + cDay);
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mPanel = new Panel(this, null);
		setContentView(mPanel);
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
		String imageURL = null;
		switch (id) {
		case R.id.next:
			imageURL = getImageUrl(cDay.incrementDay(1));
			if (imageURL == null) {
				return true;
			}
			bitmap = getBitmapFromURL(imageURL);
			if (bitmap == null) {
				Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
			} else {
				mPanel.setBitmap(bitmap);
				mPanel.invalidate();
			}
			return true;
		case R.id.prev:
			imageURL = getImageUrl(cDay.incrementDay(-1));
			if (imageURL == null) {
				return true;
			}
			bitmap = getBitmapFromURL(imageURL);
			if (bitmap == null) {
				Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
			} else {
				mPanel.setBitmap(bitmap);
				mPanel.invalidate();
			}
			return true;
		case R.id.first:
			imageURL = getImageUrl(cDayFirst);
			if (imageURL == null) {
				return true;
			}
			bitmap = getBitmapFromURL(imageURL);
			if (bitmap == null) {
				Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
			} else {
				mPanel.setBitmap(bitmap);
				mPanel.invalidate();
			}
			return true;
		case R.id.today:
			imageURL = getImageUrl(CalendarDay.invalid());
			if (imageURL == null) {
				return true;
			}
			bitmap = getBitmapFromURL(imageURL);
			if (bitmap == null) {
				Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
			} else {
				mPanel.setBitmap(bitmap);
				mPanel.invalidate();
			}
			return true;
		case R.id.date:
			getDate();
			return true;
		case R.id.share:
			return true;
		}
		return false;
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onPause: cDay=" + cDay);
		super.onPause();
		// Retain the state
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putInt("year", cDay.year);
		editor.putInt("month", cDay.month);
		editor.putInt("day", cDay.day);
		editor.commit();
	}

	@Override
	protected void onResume() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume(1): cDay="
				+ cDay);
		super.onResume();
		// Restore the state
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		int year = prefs.getInt("year", -1);
		int month = prefs.getInt("month", -1);
		int day = prefs.getInt("day", -1);
		cDay.set(year, month, day);
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume(2): cDay="
				+ cDay);
		String imageURL = getImageUrl(cDay);
		if (imageURL == null) {
			return;
		}
		bitmap = getBitmapFromURL(imageURL);
		if (bitmap == null) {
			Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
		} else {
			mPanel.setBitmap(bitmap);
			mPanel.invalidate();
		}
	}

	public Bitmap getTestBitmap() {
		Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.dilbert);
		return bitmap;
	}

	/**
	 * Gets a new image by parsing the page at dilbert.com.
	 */
	public String getImageUrl(CalendarDay cDay) {
		String imageUrlString = null;
		try {
			URL url = null;
			String dateString = null;
			// Get it from the Dilbert site
			CalendarDay cDayNow = CalendarDay.now();
			if (cDay.isInvalid()) {
				this.cDay = cDayNow;
				dateString = cDay.toString();
				url = new URL(urlPrefix);
				// statusBar.setText("Today " + dateString);
			} else {
				dateString = cDay.toString();
				// Check it is not in the future as the site doesn't give a
				// sensible result in this case
				Calendar cal = cDay.getCalendar();
				Calendar now = cDayNow.getCalendar();
				if (cal.after(now)) {
					Utils.errMsg(this, dateString + " is in the future");
					return null;
				}
				// Check it isn't before the first one
				if (cal.before(cDayFirst)) {
					Utils.errMsg(this, dateString
							+ " is before the first available strip");
					return null;
				}
				// Store it
				this.cDay = cDay;

				url = new URL(dateUrlPrefix + dateString);
				// statusBar.setText(dateString);
			}

			// Look for /dyn/str_strip/xxx.strip.gif
			String regex = "(/dyn/str_strip/.*\\.strip\\.gif)";
			Pattern pattern = Pattern.compile(regex);
			BufferedReader br = new BufferedReader(new InputStreamReader(
					url.openStream()));
			String line;
			while ((line = br.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					imageUrlString = urlPrefix + matcher.group();
					// System.out.println(matcher.group());
					// System.out.println(retVal);
				}
			}
			if (br != null) {
				br.close();
			}

			// May 2011: Seems to have the Sunday strip at strip.sunday.gif
			// Try this if the above fails
			if (imageUrlString == null) {
				regex = "(/dyn/str_strip/.*\\.strip\\.sunday\\.gif)";
				pattern = Pattern.compile(regex);
				br = new BufferedReader(new InputStreamReader(url.openStream()));
				while ((line = br.readLine()) != null) {
					Matcher matcher = pattern.matcher(line);
					if (matcher.find()) {
						imageUrlString = urlPrefix + matcher.group();
						// System.out.println(matcher.group());
						// System.out.println(retVal);
					}
				}
				if (br != null) {
					br.close();
				}
			}
			if (imageUrlString == null) {
				Utils.errMsg(DailyDilbertActivity.this,
						"Failed to find image URL");
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Getting image URL failed:", ex);
		}
		return imageUrlString;
	}

	/**
	 * Get a Bitmap from a URL.
	 * 
	 * @param src
	 *            The String URL.
	 * @return The Bitmap.
	 */
	public static Bitmap getBitmapFromURL(String src) {
		try {
			URL url = new URL(src);
			HttpURLConnection connection = (HttpURLConnection) url
					.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private void getDate() {
		DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
			@Override
			public void onDateSet(DatePicker view, int year, int month, int day) {
				cDay.set(year, month, day);
				String imageURL = getImageUrl(cDay);
				if (imageURL == null) {
					return;
				}
				bitmap = getBitmapFromURL(imageURL);
				if (bitmap == null) {
					Utils.errMsg(DailyDilbertActivity.this,
							"Failed to get image");
				} else {
					mPanel.setBitmap(bitmap);
					mPanel.invalidate();
				}
			}
		};
		DatePickerDialog dlg = new DatePickerDialog(this, dateSetListener,
				cDay.year, cDay.month, cDay.day);
		dlg.show();
	}

	/**
	 * Class to manage a day represented by the year, month, and
	 * day-of-the-month. These values are defines as for a Calendar. That is,
	 * the month starts with 0 for January.
	 * 
	 * @see java.util.Calendar
	 */
	static class CalendarDay {
		public int year;
		public int month;
		public int day;

		/**
		 * Constructor.
		 * 
		 * @param year
		 * @param month
		 * @param day
		 */
		public CalendarDay(int year, int month, int day) {
			set(year, month, day);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			// Note the month starts with 1 for the string output
			return String.format("%04d-%02d-%02d", year, month + 1, day);
		}

		/**
		 * Get the Date corresponding to this instance.
		 * 
		 * @return
		 */
		public Date getDate() {
			Calendar cal = getCalendar();
			return cal.getTime();
		}

		/**
		 * Set the values for this instance from the given Date.
		 * 
		 * @param date
		 */
		public void set(Date date) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			set(cal);
		}

		/**
		 * Get the Calendar corresponding to this instance.
		 * 
		 * @return
		 */
		public Calendar getCalendar() {
			Calendar cal = Calendar.getInstance();
			cal.set(year, month, day);
			return cal;
		}

		/**
		 * Set the values for this instance from the given Calendar.
		 * 
		 * @param cal
		 */
		public void set(Calendar cal) {
			year = cal.get(Calendar.YEAR);
			month = cal.get(Calendar.MONTH);
			day = cal.get(Calendar.DAY_OF_MONTH);
		}

		/**
		 * Sets the values for this instance.
		 * 
		 * @param year
		 * @param month
		 * @param day
		 */
		public void set(int year, int month, int day) {
			this.year = year;
			this.month = month;
			this.day = day;
		}

		/**
		 * Returns if this instance represents an invalid calendar day. An
		 * invalid calendar day has the year, month, and day equal to -1.
		 * 
		 * @return
		 */
		public boolean isInvalid() {
			return (year == -1 && month == -1 && day == -1);
		}

		/**
		 * Returns a new CalendarDay with its values incremented by the
		 * specified number of days from this one. If the amount is negative, it
		 * decrements the values. The year, month, and day will be adjusted to
		 * proper values. (Dec 31, 2012 incremented by 1 is Jan 1, 2013.)
		 * 
		 * @param number
		 *            The amount by which to increment.
		 */
		public CalendarDay incrementDay(int number) {
			Calendar cal = getCalendar();
			cal.add(Calendar.DAY_OF_MONTH, number);
			return new CalendarDay(cal.get(Calendar.YEAR),
					cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
		}

		/**
		 * Returns a CalendarDay representing the current time.
		 * 
		 * @return
		 */
		public static CalendarDay now() {
			Calendar cal = Calendar.getInstance();
			int year = cal.get(Calendar.YEAR);
			int month = cal.get(Calendar.MONTH);
			int day = cal.get(Calendar.DAY_OF_MONTH);
			return new CalendarDay(year, month, day);
		}

		/**
		 * Returns a CalendarDay representing an invalid calendar day. An
		 * invalid calendar day has the year, month, and day equal to -1.
		 * 
		 * @return
		 */
		public static CalendarDay invalid() {
			return new CalendarDay(-1, -1, -1);
		}

	}

	class Panel extends View {
		Bitmap bitmap;

		public Panel(Context context, Bitmap bitmap) {
			super(context);
			this.bitmap = bitmap;
		}

		@Override
		public void onDraw(Canvas canvas) {
			if (bitmap == null) {
				return;
			}
			canvas.drawColor(Color.BLACK);

			// Center it
			int cHeight = canvas.getHeight();
			int cWidth = canvas.getWidth();
			int bHeight = bitmap.getHeight();
			int bWidth = bitmap.getWidth();
			Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: canvas=("
					+ cWidth + "," + cHeight + ")");
			Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: bitmap=("
					+ bWidth + "," + bHeight + ")");

			// Calculate the scale to make it fit
			float scaleWidth = ((float) cWidth) / bWidth;
			float scaleHeight = ((float) cHeight) / bHeight;
			Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: scale=("
					+ scaleWidth + "," + scaleHeight + ")");

			// Keep the aspect ratio
			if (scaleHeight > scaleWidth) {
				scaleHeight = scaleWidth;
			} else {
				scaleWidth = scaleHeight;
			}
			Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: adjScale=("
					+ scaleWidth + "," + scaleHeight + ")");

			// Create a matrix for the scaling
			Matrix matrix = new Matrix();
			// resize the bit map
			matrix.postScale(scaleFactor * scaleWidth, scaleFactor
					* scaleHeight);
			// rotate the Bitmap
			// matrix.postRotate(45);

			// Recreate the bitmap
			Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bWidth,
					bHeight, matrix, true);
			bHeight = newBitmap.getHeight();
			bWidth = newBitmap.getWidth();
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": onDraw: newBitmap=(" + bWidth + "," + bHeight + ")");

			// Center it
			int x0 = (cWidth - bWidth) / 2;
			int y0 = (cHeight - bHeight) / 2;
			Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: x=(" + x0
					+ "," + y0 + ")");
			canvas.drawBitmap(newBitmap, x0, y0, null);
		}

		public Bitmap getBitmap() {
			return bitmap;
		}

		public void setBitmap(Bitmap bitmap) {
			this.bitmap = bitmap;
		}

	}

}