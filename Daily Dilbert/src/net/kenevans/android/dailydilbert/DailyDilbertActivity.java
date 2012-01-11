package net.kenevans.android.dailydilbert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author evans
 * 
 */
public class DailyDilbertActivity extends Activity implements IConstants {
	/** Where the image URL is found for todays strip. */
	private static final String urlPrefix = "http://www.dilbert.com";
	/** Where the image URL is found for archive strips. Append yyyy-mm-dd. */
	private static final String dateUrlPrefix = "http://www.dilbert.com/strips/comic/";
	/** The first available strip is on April 16, 1989. */
	private static final CalendarDay cDayFirst = new CalendarDay(1989, 3, 16);
	/** Directory on the SD card where strips are saved */
	private static final String SD_CARD_DILBERT_DIRECTORY = "Dilbert";
	/** Filename of the cached current image. */
	private static final String CACHE_FILENAME = "Current.png";

	private CalendarDay cDay = CalendarDay.invalid();
	private Bitmap bitmap;
	private ImagePanel mPanel;
	private TextView mInfo;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: cDay=" + cDay);
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		try {
			setContentView(R.layout.main);
			mInfo = (TextView) findViewById(R.id.info);
			mPanel = (ImagePanel) findViewById(R.id.panel);
		} catch (Exception ex) {
			Utils.excMsg(this, "Error getting resources", ex);
		}

		// Buttons (These need a margin to avoid edge sensitivity problems on
		// the EVO 3D)
		ImageButton button = (ImageButton) findViewById(R.id.nextbutton);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getStrip(cDay.incrementDay(1));
			}
		});
		button = (ImageButton) findViewById(R.id.prevbutton);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getStrip(cDay.incrementDay(-1));
			}
		});

		// Debug
		// RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
		// int childCount = layout.getChildCount();
		// Log.d(TAG, this.getClass().getSimpleName() + ": childCount="
		// + childCount);
		// View view;
		// for (int i = 0; i < childCount; i++) {
		// view = layout.getChildAt(i);
		// Log.d(TAG, this.getClass().getSimpleName() + ": " + i + " " + view);
		// }
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
		case R.id.next:
			getStrip(cDay.incrementDay(1));
			return true;
		case R.id.prev:
			getStrip(cDay.incrementDay(-1));
			return true;
		case R.id.first:
			getStrip(cDayFirst);
			return true;
		case R.id.today:
			getStrip(CalendarDay.invalid());
			return true;
		case R.id.date:
			getDate();
			return true;
		case R.id.save:
			save();
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
		// Cache the bitmap
		cacheBitmap();
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
		Bitmap cachedBitmap = getCachedBitmap();
		if (cachedBitmap != null) {
			bitmap = cachedBitmap;
		} else {
			bitmap = getBitmapFromURL(imageURL);
		}
		if (bitmap == null) {
			Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
		} else {
			setNewImage();
		}
	}

	/**
	 * Sets a new image in the canvas and writes the info message
	 */
	private void setNewImage() {
		setInfo(cDay.toString());
		mPanel.setBitmap(bitmap);
		mPanel.invalidate();
	}

	/**
	 * Sets the message in the info area.
	 * 
	 * @param info
	 */
	public void setInfo(String info) {
		mInfo.setText(info);
	}

	/**
	 * Gets the strip corresponding to the given calendar day.
	 * 
	 * @param cDay
	 */
	private void getStrip(CalendarDay cDay) {
		String imageURL = getImageUrl(cDay);
		if (imageURL == null) {
			return;
		}
		bitmap = getBitmapFromURL(imageURL);
		if (bitmap == null) {
			Utils.errMsg(DailyDilbertActivity.this, "Failed to get image");
		} else {
			setNewImage();
		}
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

	/**
	 * Gets the URL for the image by parsing the page at dilbert.com.
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
	 * Gets a strip using the date picker to pick a date.
	 */
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
					setNewImage();
				}
			}
		};
		DatePickerDialog dlg = new DatePickerDialog(this, dateSetListener,
				cDay.year, cDay.month, cDay.day);
		dlg.show();
	}

	/**
	 * Saves the current bitmap to the SD card.
	 */
	private void save() {
		if (cDay.isInvalid() || bitmap == null) {
			Utils.errMsg(this, "Image is invalid");
			return;
		}
		FileOutputStream out = null;
		String fileName = "Dilbert-" + cDay.toString() + ".png";
		try {
			File sdCardRoot = Environment.getExternalStorageDirectory();
			if (sdCardRoot.canWrite()) {
				File dir = new File(sdCardRoot, SD_CARD_DILBERT_DIRECTORY);
				if (dir.exists() && dir.isFile()) {
					Utils.errMsg(this, "Cannot create directory: " + dir
							+ "\nA file with that name exists.");
					return;
				}
				if (!dir.exists()) {
					Log.d(TAG, this.getClass().getSimpleName()
							+ ": create: dir=" + dir.getPath());
					boolean res = dir.mkdir();
					if (!res) {
						Utils.errMsg(this, "Cannot create directory: " + dir);
						return;
					}
				}
				File file = new File(dir, fileName);
				Log.d(TAG, this.getClass().getSimpleName() + ": save: file="
						+ file.getPath());
				out = new FileOutputStream(file);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
				Toast.makeText(getApplicationContext(), "Wrote " + fileName,
						Toast.LENGTH_LONG).show();
			} else {
				Utils.errMsg(this, "Cannot write to SD card");
				return;
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Error saving to SD card", ex);
		} finally {
			try {
				out.close();
			} catch (Exception ex) {
				// Do nothing
			}
		}
	}

	/**
	 * Caches the current bitmap to the SD card.
	 */
	private void cacheBitmap() {
		File cacheDir = getExternalFilesDir(null);
		if (cacheDir == null || !cacheDir.canWrite()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": cacheBitmap: Cache not available");
			return;
		}
		// Clear the cache
		clearCache();

		if (cDay.isInvalid() || bitmap == null) {
			return;
		}
		FileOutputStream out = null;
		String fileName = "Dilbert-" + cDay.toString() + ".png";
		try {
			File file = new File(cacheDir, fileName);
			out = new FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": cacheBitmap: Cached file=" + file.getPath());
		} catch (Exception ex) {
			// Do nothing
		} finally {
			try {
				out.close();
			} catch (Exception ex) {
				// Do nothing
			}
		}
	}

	/**
	 * Get a Bitmap from a file in the cache.
	 * 
	 * @return The Bitmap or null on failure.
	 */
	private Bitmap getCachedBitmap() {
		File cacheDir = getExternalFilesDir(null);
		if (cacheDir == null || !cacheDir.canWrite()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getCachedBitmap: Cache not available");
			return null;
		}
		String fileName = "Dilbert-" + cDay.toString() + ".png";
		File file = new File(cacheDir, fileName);
		if (!file.exists()) {
			// Clear the cache as any file is stale
			clearCache();
			return null;
		}
		// Read the bitmap or null on failure
		Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
		if (bitmap == null) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getCachedBitmap: Cached bitmap is null");
		} else {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getCachedBitmap: Got " + fileName);
		}
		return bitmap;
	}

	/**
	 * Deletes all the files in the external files directory.
	 */
	private void clearCache() {
		File cacheDir = getExternalFilesDir(null);
		if (cacheDir == null) {
			return;
		}
		String[] files = cacheDir.list();
		int len = files.length;
		if (files == null || len == 0) {
			return;
		}
		File file;
		for (String fileName : files) {
			file = new File(cacheDir, fileName);
			if (file.isFile()) {
				file.delete();
			}
		}
		Log.d(TAG, this.getClass().getSimpleName() + ": clearCache: Deleted "
				+ len + " file(s)");
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

}