package net.kenevans.android.hxmmonitor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.afree.chart.AFreeChart;
import org.afree.chart.ChartFactory;
import org.afree.chart.axis.DateAxis;
import org.afree.chart.axis.NumberAxis;
import org.afree.chart.axis.NumberTickUnit;
import org.afree.chart.plot.XYPlot;
import org.afree.chart.renderer.xy.StandardXYItemRenderer;
import org.afree.chart.renderer.xy.XYItemRenderer;
import org.afree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.afree.chart.title.TextTitle;
import org.afree.data.time.FixedMillisecond;
import org.afree.data.time.TimeSeries;
import org.afree.data.time.TimeSeriesCollection;
import org.afree.data.xy.XYDataset;
import org.afree.graphics.SolidColor;
import org.afree.graphics.geom.Dimension;
import org.afree.graphics.geom.Font;
import org.afree.graphics.geom.RectShape;
import org.afree.ui.RectangleInsets;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

/**
 * @author evans
 *
 */
public class PlotActivity extends Activity implements IConstants {
	private static final String TAG = "HxM Plot";
	private AFreeChartView mView;
	private AFreeChart mChart;
	private XYDataset mHrDataset;
	private XYDataset mRrDataset;
	private TimeSeries mHrSeries;
	private TimeSeries mRrSeries;
	private HxMMonitorDbAdapter mDbAdapter;
	private File mDataDir;
	private long mPlotStartTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
		super.onCreate(savedInstanceState);
		mView = new AFreeChartView(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(mView);

		// Get the database name from the default preferences
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String prefString = prefs.getString(PREF_DATA_DIRECTORY, null);
		if (prefString == null) {
			return;
		}

		// Get the plot start time from the default preferences
		long prefLong = prefs.getLong(PREF_PLOT_START_TIME, Long.MIN_VALUE);
		if (prefLong == Long.MIN_VALUE) {
			// Set it to now
			mPlotStartTime = new Date().getTime();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putLong(PREF_PLOT_START_TIME, mPlotStartTime);
			editor.commit();
		} else {
			mPlotStartTime = prefLong;
		}

		// Open the database
		mDataDir = new File(prefString);
		if (mDataDir == null) {
			Utils.errMsg(this, "Database directory is null");
			return;
		}
		if (!mDataDir.exists()) {
			Utils.errMsg(this, "Cannot find database directory: " + mDataDir);
			mDataDir = null;
			return;
		}
		mDbAdapter = new HxMMonitorDbAdapter(this, mDataDir);
		mDbAdapter.open();
	}

	@Override
	protected void onResume() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume: " + "mView="
				+ mView + " mHrDataset=" + mHrDataset + " mHrSeries="
				+ mHrSeries);
		super.onResume();
		// SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		// lastTimeOffset = prefs.getInt("timeOffset", lastTimeOffset);
		// dryRun = prefs.getBoolean("dryrun", dryRun);
		if (mView != null && mChart == null) {
			mChart = createChart();
			mView.setChart(mChart);
		} else {
			Log.d(TAG, getClass().getSimpleName() + ".onResume: mView null");
			returnResult(RESULT_ERROR, "mView is null");
		}
		super.onResume();

		Log.d(TAG, "Starting registerReceiver");
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + " :onPause");
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mDbAdapter != null) {
			mDbAdapter.close();
			mDbAdapter = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_plot, menu);
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
			return true;
		case R.id.start_now:
			startNow();
			return true;
		case R.id.get_view_info:
			Utils.infoMsg(this, getViewInfo());
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Sets the result code to send back to the calling Activity.
	 * 
	 * @param resultCode
	 *            The result code to send.
	 */
	private void returnResult(int resultCode, String msg) {
		Intent data = new Intent();
		if (msg != null) {
			data.putExtra(MSG_CODE, msg);
		}
		setResult(resultCode, data);
		finish();
	}

	/**
	 * Handles various events fired by the Service.
	 * 
	 * <br>
	 * <br>
	 * ACTION_GATT_CONNECTED: connected to a GATT server.<br>
	 * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.<br>
	 * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.<br>
	 * ACTION_DATA_AVAILABLE: received data from the device. This can be a
	 * result of read or notification operations.<br>
	 */
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				// Log.d(TAG, "onReceive: " + action);
				updateChart(intent);
			} else if (BluetoothLeService.ACTION_ERROR.equals(action)) {
				Log.d(TAG, "onReceive: " + action);
				displayError(intent);
			}
		}
	};

	/**
	 * Make an IntentFilter for the actions in which we are interested.
	 * 
	 * @return
	 */
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		// intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		// intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		// intentFilter
		// .addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	/**
	 * Displays the error from an ACTION_ERROR callback.
	 * 
	 * @param intent
	 */
	private void displayError(Intent intent) {
		String msg = null;
		try {
			msg = intent.getStringExtra(EXTRA_MSG);
			if (msg == null) {
				Utils.errMsg(this, "Received null error message");
				return;
			}
			Utils.errMsg(this, msg);
		} catch (Exception ex) {
			Log.d(TAG, "Error displaying error", ex);
			Utils.excMsg(this, msg, ex);
		}
	}

	/**
	 * Resets the plot start time to now.
	 */
	private void startNow() {
		mPlotStartTime = new Date().getTime();
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(PREF_PLOT_START_TIME, mPlotStartTime);
		editor.commit();
		mHrDataset = createHrDataset();
		mRrDataset = createRrDataset();
		((XYPlot) mChart.getPlot()).setDataset(0, mHrDataset);
		((XYPlot) mChart.getPlot()).setDataset(1, mRrDataset);
	}

	/**
	 * Gets info about the view.
	 */
	private String getViewInfo() {
		String info = "";
		if (mView == null) {
			info += "View is null";
			return info;
		}
		Dimension size = mView.getSize();
		RectangleInsets insets = mView.getInsets();
		RectShape available = new RectShape(insets.getLeft(), insets.getTop(),
				size.getWidth() - insets.getLeft() - insets.getRight(),
				size.getHeight() - insets.getTop() - insets.getBottom());

		info += "Size=(" + size.getWidth() + "," + size.getHeight() + ")\n";
		info += "Available=(" + available.getWidth() + ","
				+ available.getHeight() + ") @ (" + available.getCenterX()
				+ "," + available.getCenterY() + ")\n";
		int minimumDrawWidth = mView.getMinimumDrawWidth();
		int maximumDrawWidth = mView.getMaximumDrawWidth();
		int minimumDrawHeight = mView.getMinimumDrawHeight();
		int maximumDrawHeight = mView.getMaximumDrawHeight();
		info += "minimumDrawWidth=" + minimumDrawWidth + " maximumDrawWidth="
				+ maximumDrawWidth + "\n";
		info += "minimumDrawHeight=" + minimumDrawHeight
				+ " maximumDrawHeight=" + maximumDrawHeight + "\n";
		double chartScaleX = mView.getChartScaleX();
		double chartScaleY = mView.getChartScaleY();
		info += "chartScaleX=" + chartScaleX + " chartScaleY=" + chartScaleY
				+ "\n";
		Display display = getWindowManager().getDefaultDisplay();
		Point displaySize = new Point();
		display.getSize(displaySize);
		info += "displayWidth=" + displaySize.x + " displayHeight="
				+ displaySize.y + "\n";

		return info;
	}

	/**
	 * Converts the String of RR values from the database to a sing double
	 * value.
	 * 
	 * @param strValues
	 * @return The value or NaN if there is an error.
	 */
	private double convertRrStringsToDouble(String strValue) {
		String[] tokens;
		double value;
		double max;
		if (strValue == null) {
			return Double.NaN;
		}
		tokens = strValue.trim().split("\\s+");
		if (tokens.length == 1) {
			try {
				value = Double.parseDouble(tokens[0]);
			} catch (NumberFormatException ex) {
				value = Double.NaN;
			}
		} else {
			// TODO Consider other alternatives
			max = -Double.MAX_VALUE;
			for (String string : tokens) {
				max = -Double.NaN;
				try {
					value = Double.parseDouble(string);
				} catch (NumberFormatException ex) {
					continue;
				}
				if (value > max) {
					max = value;
				}
			}
			if (max == -Double.MAX_VALUE) {
				value = Double.NaN;
			} else {
				value = max;
			}
		}
		return value;
	}

	/**
	 * Creates a chart.
	 *
	 * @param dataset
	 *            The dataset to use.
	 *
	 * @return The chart.
	 */
	private AFreeChart createChart() {
		Log.d(TAG, "createChart");
		if (mHrDataset == null) {
			mHrDataset = createHrDataset();
		}
		AFreeChart chart = ChartFactory.createTimeSeriesChart(null, // title
				"Time", // x-axis label
				"HR", // y-axis label
				mHrDataset, // data
				false, // create legend?
				true, // generate tooltips?
				false // generate URLs?
				);

		SolidColor white = new SolidColor(Color.WHITE);
		SolidColor black = new SolidColor(Color.BLACK);
		SolidColor red = new SolidColor(Color.RED);
		SolidColor blue = new SolidColor(Color.BLUE);
		SolidColor gray = new SolidColor(Color.GRAY);
		SolidColor ltgray = new SolidColor(Color.LTGRAY);

		chart.setBackgroundPaintType(black);
		// chart.setBorderPaintType(white);
		chart.setBorderVisible(false);
		// chart.setPadding(new RectangleInsets(10.0, 10.0, 10.0, 10.0));

		Font font = new Font("SansSerif", Typeface.NORMAL, 24);
		Font titleFont = new Font("SansSerif", Typeface.BOLD, 30);

		chart.setTitle("HxM Monitor");
		TextTitle title = chart.getTitle();
		title.setFont(titleFont);
		title.setPaintType(white);

		// LegendTitle legend = chart.getLegend();
		// legend.setItemFont(font);

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaintType(black);
		plot.setDomainGridlinePaintType(gray);
		plot.setRangeGridlinePaintType(gray);
		plot.setOutlineVisible(true);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		// TODO Find out what these mean
		// plot.setDomainCrosshairVisible(true);
		// plot.setRangeCrosshairVisible(true);

		DateAxis xAxis = (DateAxis) plot.getDomainAxis();
		xAxis.setDateFormatOverride(new SimpleDateFormat("hh:mm", Locale.US));
		xAxis.setLabelFont(font);
		xAxis.setLabelPaintType(white);
		xAxis.setAxisLinePaintType(white);
		xAxis.setTickLabelFont(font);
		xAxis.setTickLabelPaintType(ltgray);

		float strokeSize = 5f;

		NumberAxis yAxis0 = (NumberAxis) plot.getRangeAxis();
		yAxis0.setAutoRangeIncludesZero(true);
		yAxis0.setAutoRangeMinimumSize(10);
		yAxis0.setLabelFont(font);
		yAxis0.setLabelPaintType(white);
		yAxis0.setAxisLinePaintType(white);
		yAxis0.setTickLabelFont(font);
		yAxis0.setTickLabelPaintType(ltgray);
		XYItemRenderer renderer0 = new StandardXYItemRenderer();
		renderer0.setSeriesPaintType(0, red);
		renderer0.setBaseStroke(strokeSize);
		renderer0.setSeriesStroke(0, strokeSize);
		plot.setRenderer(0, renderer0);

		if (mRrDataset == null) {
			mRrDataset = createRrDataset();
		}

		NumberAxis yAxis1 = new NumberAxis("RR");
		plot.setRangeAxis(1, yAxis1);
		plot.setDataset(1, mRrDataset);
		plot.mapDatasetToRangeAxis(1, 1);
		yAxis1.setAutoRangeIncludesZero(true);
		yAxis1.setAutoRangeMinimumSize(.3);
//		yAxis1.setTickUnit(new NumberTickUnit(.1));
		yAxis1.setLabelFont(font);
		yAxis1.setLabelPaintType(white);
		yAxis1.setLabelPaintType(white);
		yAxis1.setAxisLinePaintType(white);
		yAxis1.setTickLabelFont(font);
		yAxis1.setTickLabelPaintType(ltgray);
		XYItemRenderer renderer1 = new StandardXYItemRenderer();
		renderer1.setSeriesPaintType(0, blue);
		renderer1.setBaseStroke(strokeSize);
		renderer1.setSeriesStroke(0, strokeSize);
		plot.setRenderer(1, renderer1);

		XYItemRenderer r = plot.getRenderer();
		if (r instanceof XYLineAndShapeRenderer) {
			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
			renderer.setBaseShapesVisible(true);
			renderer.setBaseShapesFilled(true);
			renderer.setDrawSeriesLineAsPath(true);
		}

		return chart;
	}

	/**
	 * Updates the chart when data is received.
	 * 
	 * @param intent
	 */
	private void updateChart(Intent intent) {
		String strValue;
		String[] tokens;
		long date = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE);
		if (date == Long.MIN_VALUE) {
			return;
		}
		double value = Double.NaN;
		double max;
		if (mHrSeries != null) {
			strValue = intent.getStringExtra(EXTRA_HR);
			if (strValue != null) {
				try {
					value = Double.parseDouble(strValue);
				} catch (NumberFormatException ex) {
					value = Double.NaN;
				}
			}
			mHrSeries.addOrUpdate(new FixedMillisecond(date), value);
		}
		if (mRrSeries != null) {
			value = Double.NaN;
			strValue = intent.getStringExtra(EXTRA_RR);
			value = convertRrStringsToDouble(strValue);
			mRrSeries.addOrUpdate(new FixedMillisecond(date), value / 1024.);
		}
	}

	/**
	 * Creates a sample dataset.
	 *
	 * @return The dataset.
	 */
	private XYDataset createHrDataset() {
		Log.d(TAG, "Creating HR dataset");
		mHrSeries = new TimeSeries("HR");
		Cursor cursor = null;
		int nItems = 0;
		try {
			if (mDbAdapter != null) {
				cursor = mDbAdapter.fetchAllDataStartingAtTime(mPlotStartTime);
				int indexDate = cursor.getColumnIndex(COL_DATE);
				int indexHr = cursor.getColumnIndex(COL_HR);

				// Loop over items
				cursor.moveToFirst();
				long date;
				double hr;
				while (cursor.isAfterLast() == false) {
					nItems++;
					date = cursor.getLong(indexDate);
					hr = cursor.getInt(indexHr);
					mHrSeries.addOrUpdate(new FixedMillisecond(date), hr);
					cursor.moveToNext();
				}
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Error creating HR dataset", ex);
		} finally {
			try {
				cursor.close();
			} catch (Exception ex) {
				// Do nothing
			}
		}
		Log.d(TAG, "Dataset 1 created with " + nItems + " items");

		// long date = new Date().getTime();
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 160);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 161);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 165);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 170);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 180);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 200);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 201);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 205);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 210);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 215);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 217);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 214);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 212);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 210);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 208);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 205);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 203);
		// mHrSeries.add(new FixedMillisecond(date -= 1000), 200);

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(mHrSeries);
		return dataset;
	}

	/**
	 * Creates a sample dataset.
	 *
	 * @return The dataset.
	 */
	private XYDataset createRrDataset() {
		Log.d(TAG, "Creating RR datase");
		mRrSeries = new TimeSeries("RR");
		Cursor cursor = null;
		int nItems = 0;
		try {
			if (mDbAdapter != null) {
				cursor = mDbAdapter.fetchAllDataStartingAtTime(mPlotStartTime);
				int indexDate = cursor.getColumnIndex(COL_DATE);
				int indexRr = cursor.getColumnIndex(COL_RR);

				// Loop over items
				cursor.moveToFirst();
				long date;
				double rr;
				String rrString;
				while (cursor.isAfterLast() == false) {
					date = cursor.getLong(indexDate);
					rrString = cursor.getString(indexRr);
					rr = convertRrStringsToDouble(rrString);
					mRrSeries.addOrUpdate(new FixedMillisecond(date), rr);
					cursor.moveToNext();
				}
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Error creating RR dataset", ex);
		} finally {
			try {
				cursor.close();
			} catch (Exception ex) {
				// Do nothing
			}
		}
		Log.d(TAG, "Dataset 2 created with " + nItems + " items");

		// long date = new Date().getTime();
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 260);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 261);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 265);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 270);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 280);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 200);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 201);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 205);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 210);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 215);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 217);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 214);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 212);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 210);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 208);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 205);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 203);
		// mRrSeries.add(new FixedMillisecond(date -= 1000), 1 / 200);

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(mRrSeries);
		return dataset;
	}

}
