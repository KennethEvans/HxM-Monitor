package net.kenevans.android.dailydilbert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
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
	/** Directory on the SD card where strips are saved */
	private static final String SD_CARD_DILBERT_DIRECTORY = "Dilbert";
	/**
	 * Number of files to keep in the cache when trimming. The cache should
	 * never contain more than CACHE_MAX_FILES + 1 at any time.
	 */
	private static int CACHE_MAX_FILES = 5;

	private GestureDetector gestureDetector;
	private CalendarDay cDay = CalendarDay.invalid();
	private Bitmap bitmap;
	private ImagePanel mPanel;
	private TextView mInfo;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: cDay=" + cDay);
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: screen="
				+ getResources().getDisplayMetrics().widthPixels + ","
				+ getResources().getDisplayMetrics().heightPixels);
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: dpi="
				+ getResources().getDisplayMetrics().xdpi + ","
				+ getResources().getDisplayMetrics().ydpi);
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: density="
				+ getResources().getDisplayMetrics().density);
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate: densityDpi="
				+ getResources().getDisplayMetrics().densityDpi + " (LOW="
				+ DisplayMetrics.DENSITY_LOW + " MEDIUM="
				+ DisplayMetrics.DENSITY_MEDIUM + " HIGH="
				+ DisplayMetrics.DENSITY_HIGH);
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

		// Set up gestures
		gestureDetector = new GestureDetector(new MyGestureDetector());

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
			getStrip(CalendarDay.first());
			return true;
		case R.id.today:
			getStrip(CalendarDay.now());
			return true;
		case R.id.date:
			getDate();
			return true;
		case R.id.save:
			save();
			return true;
		case R.id.share:
			share();
			return true;
		}
		return false;
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onPause: cDay=" + cDay);
		super.onPause();
		// No need to save the state as it is saved when the image is set
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
		getStrip(cDay);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (gestureDetector.onTouchEvent(event))
			return true;
		else
			return false;
	}

	/**
	 * Gets the strip corresponding to the given calendar day.
	 * 
	 * @param cDay
	 */
	private void getStrip(CalendarDay cDay) {
		// See if it is in the cache
		Bitmap newBitmap = getCachedBitmap(cDay);
		if (newBitmap != null) {
			this.cDay = cDay;
			bitmap = newBitmap;
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getStrip: Got bitmap from cache for " + cDay);
			mInfo.setTextColor(Color.WHITE);
		} else {
			String imageURL = getImageUrl(cDay);
			if (imageURL == null) {
				return;
			}
			bitmap = getBitmapFromURL(imageURL);
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getStrip: Got bitmap from URL for " + this.cDay);
			mInfo.setTextColor(Color.CYAN);
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
		// Retain the state
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putInt("year", cDay.year);
		editor.putInt("month", cDay.month);
		editor.putInt("day", cDay.day);
		editor.commit();
		// Cache the bitmap
		cacheBitmap();
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
	 * Gets the URL for the image by parsing the page at dilbert.com. This
	 * method contains the logic for dealing with the Dilbert site.
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
				if (cal.compareTo(CalendarDay.first().getCalendar()) == -1) {
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
				getStrip(cDay);
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

	// /**
	// * Shares the current image file.
	// */
	// private void share1() {
	// File cacheDir = getExternalFilesDir(null);
	// if (cacheDir == null || !cacheDir.canWrite()) {
	// Utils.errMsg(this, "Image cache is not available");
	// return;
	// }
	// String fileName = "Dilbert-" + cDay.toString() + ".png";
	// File file = new File(cacheDir, fileName);
	// if (!file.exists()) {
	// Utils.errMsg(this, "Image file is not in cache");
	// return;
	// }
	// // Uri uri = Uri.parse(file.getPath());
	//
	// String url = null;
	// try {
	// url = Media.insertImage(this.getContentResolver(),
	// file.getAbsolutePath(), file.getName(), file.getName());
	// } catch(Exception ex) {
	// Utils.excMsg(this, "Failed to insert image", ex);
	// }
	// if(url == null) {
	// Utils.errMsg(this, "Could not insert image");
	// }
	// Uri uri = Uri.parse(url);
	//
	//
	// // Start the intent
	// Intent intent = new Intent(Intent.ACTION_SEND);
	// intent.setType("image/png");
	// intent.putExtra(Intent.EXTRA_STREAM, uri);
	// startActivity(Intent.createChooser(intent, "Share image using"));
	// }

	/**
	 * Shares the current image file.
	 */
	private void share() {
		// Was not able to send the file from the cache.
		// Using Media.insertImage() worked, but unsure of the consequences.
		// Using openFileOutput works. Not sure how to clean it up.
		if (cDay.isInvalid() || bitmap == null) {
			Utils.errMsg(this, "Image is invalid");
			return;
		}
		// Use the same name each time to avoid accumulating storage.
		String fileName = "Dilbert.png";
		FileOutputStream out = null;
		try {
			out = openFileOutput(fileName, MODE_WORLD_READABLE);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			out.close();
		} catch (Exception ex) {
			Utils.excMsg(this, "Error saving to SD card", ex);
		} finally {
			try {
				out.close();
			} catch (Exception ex) {
				// Do nothing
			}
		}

		File file = getFileStreamPath(fileName);
		if (file == null) {
			Utils.errMsg(this, "Could not retrieve the image file");
			return;
		}
		Uri uri = Uri.fromFile(file);
		if (uri == null) {
			Utils.errMsg(this, "Could not retrieve the image file URI");
			return;
		}

		// Start the intent
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("image/png");
		intent.putExtra(Intent.EXTRA_STREAM, uri);
		// This does the same as just using intent, but changes the string on
		// the chooser
		startActivity(Intent.createChooser(intent, "Share image using"));
	}

	/**
	 * Caches the current bitmap to the SD card.
	 */
	private void cacheBitmap() {
		File cacheDir = getExternalFilesDir(null);
		if (cacheDir == null || !cacheDir.canWrite()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": cacheBitmap: Cache is not available");
			return;
		}
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
			// Trim the cache
			trimCache();
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
	private Bitmap getCachedBitmap(CalendarDay cDay) {
		File cacheDir = getExternalFilesDir(null);
		if (cacheDir == null || !cacheDir.canWrite()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": getCachedBitmap: Cache not available");
			return null;
		}
		String fileName = "Dilbert-" + cDay.toString() + ".png";
		File file = new File(cacheDir, fileName);
		if (!file.exists()) {
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
	 * Trims the number of files in the cache to CACHE_MAX_FILES.
	 */
	private void trimCache() {
		File cacheDir = getExternalFilesDir(null);
		if (cacheDir == null) {
			return;
		}
		File[] files = cacheDir.listFiles();
		int len = files.length;
		Log.d(TAG, this.getClass().getSimpleName() + ": trimCache: files: "
				+ len);
		File file1;
		for (int i = 0; i < len; i++) {
			file1 = files[i];
			Log.d(TAG, this.getClass().getSimpleName() + ": trimCache: file: "
					+ file1.getPath() + " " + file1.lastModified());
		}
		if (files == null || len <= CACHE_MAX_FILES) {
			return;
		}
		Arrays.sort(files, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f2.lastModified()).compareTo(
						f1.lastModified());
			}
		});
		int kept = 0;
		File file;
		for (int i = 0; i < len; i++) {
			file = files[i];
			if (file.isFile()) {
				if (kept < CACHE_MAX_FILES) {
					kept++;
					continue;
				}
				file.delete();
			}
		}
		Log.d(TAG, this.getClass().getSimpleName() + ": trimCache: Kept "
				+ kept + "/" + len + " file(s)");
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
		 * Returns a CalendarDay representing the first strip, which was on on
		 * April 16, 1989.
		 * 
		 * @return
		 */
		public static CalendarDay first() {
			return new CalendarDay(1989, 3, 16);
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

	/**
	 * Gesture detector. Based on an example at<br>
	 * <br>
	 * http://www.codeshogun.com/blog/2009
	 * /04/16/how-to-implement-swipe-action-in-android/
	 */
	class MyGestureDetector extends SimpleOnGestureListener {
		private static final int SWIPE_MIN_DISTANCE = 120;
		private static final int SWIPE_MAX_OFF_PATH = 250;
		private static final int SWIPE_THRESHOLD_VELOCITY = 200;

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			try {
				// Check if it is a horizontal swipe
				if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
					return false;
				}
				// Branch on direction
				if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE
						&& Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					// To left
					getStrip(cDay.incrementDay(-1));
				} else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE
						&& Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					// To right
					getStrip(cDay.incrementDay(1));
				}
			} catch (Exception ex) {
				// Do nothing
			}
			return false;
		}
	}

}