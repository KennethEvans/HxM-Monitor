package net.kenevans.android.hxmmonitor;

import java.text.SimpleDateFormat;
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
import org.afree.chart.title.LegendTitle;
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
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
		super.onCreate(savedInstanceState);
		mView = new AFreeChartView(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(mView);
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
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.plot, menu);
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
			mHrDataset = createDataset1();
		}
		AFreeChart chart = ChartFactory.createTimeSeriesChart(
				"HxM Heart Monitor", // title
				"Time", // x-axis label
				"HR", // y-axis label
				mHrDataset, // data
				true, // create legend?
				true, // generate tooltips?
				false // generate URLs?
				);
		chart.setBackgroundPaintType(new SolidColor(Color.WHITE));

		Font font = new Font("SansSerif", Typeface.NORMAL, 24);
		Font titleFont = new Font("SansSerif", Typeface.BOLD, 30);

		TextTitle title = chart.getTitle();
		title.setFont(titleFont);

		LegendTitle legend = chart.getLegend();
		legend.setItemFont(font);

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaintType(new SolidColor(Color.LTGRAY));
		plot.setDomainGridlinePaintType(new SolidColor(Color.WHITE));
		plot.setRangeGridlinePaintType(new SolidColor(Color.WHITE));
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		// TODO Find out what these mean
		// plot.setDomainCrosshairVisible(true);
		// plot.setRangeCrosshairVisible(true);

		DateAxis xAxis = (DateAxis) plot.getDomainAxis();
		xAxis.setDateFormatOverride(new SimpleDateFormat("hh:mm", Locale.US));
		xAxis.setLabelFont(font);
		xAxis.setTickLabelFont(font);

		NumberAxis yAxis1 = (NumberAxis) plot.getRangeAxis();
		yAxis1.setAutoRangeIncludesZero(true);
		yAxis1.setLabelFont(font);
		yAxis1.setTickLabelFont(font);
		XYItemRenderer renderer1 = new StandardXYItemRenderer();
		renderer1.setSeriesPaintType(1, new SolidColor(Color.BLUE));
		plot.setRenderer(0, renderer1);

		if (mRrDataset == null) {
			mRrDataset = createDataset2();
		}

		NumberAxis yAxis2 = new NumberAxis("RR");
		yAxis2.setAutoRangeIncludesZero(false);
		plot.setRangeAxis(1, yAxis2);
		plot.setDataset(1, mRrDataset);
		plot.mapDatasetToRangeAxis(1, 1);
		yAxis2.setTickUnit(new NumberTickUnit(.2));
		yAxis2.setLabelFont(font);
		yAxis2.setTickLabelFont(font);
		XYItemRenderer renderer2 = new StandardXYItemRenderer();
		renderer2.setSeriesPaintType(1, new SolidColor(Color.BLUE));
		plot.setRenderer(1, renderer2);

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
			if (strValue != null) {
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
						try {
							value = Double.parseDouble(tokens[0]);
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
			}
			mRrSeries.addOrUpdate(new FixedMillisecond(date), value / 1024.);
		}
	}

	/**
	 * Creates a sample dataset.
	 *
	 * @return The dataset.
	 */
	private XYDataset createDataset1() {
		mHrSeries = new TimeSeries("HR");
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
	private XYDataset createDataset2() {
		mRrSeries = new TimeSeries("RR");
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
