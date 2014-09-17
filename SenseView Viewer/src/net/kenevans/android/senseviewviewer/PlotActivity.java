package net.kenevans.android.senseviewviewer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.afree.chart.AFreeChart;
import org.afree.chart.ChartFactory;
import org.afree.chart.axis.DateAxis;
import org.afree.chart.axis.NumberAxis;
import org.afree.chart.axis.NumberTickUnit;
import org.afree.chart.plot.XYPlot;
import org.afree.chart.renderer.xy.XYItemRenderer;
import org.afree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.afree.chart.title.LegendTitle;
import org.afree.chart.title.TextTitle;
import org.afree.data.time.Month;
import org.afree.data.time.Second;
import org.afree.data.time.TimeSeries;
import org.afree.data.time.TimeSeriesCollection;
import org.afree.data.xy.XYDataset;
import org.afree.graphics.SolidColor;
import org.afree.graphics.geom.Font;
import org.afree.ui.RectangleInsets;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

/**
 * @author evans
 *
 */
public class PlotActivity extends Activity implements IConstants {
	private String mFilePath;
	private AFreeChartView mView;
	/** Translation from file names to What is stored */
	private static HashMap<String, String> mDataTypes = new HashMap<String, String>();

	static {
		mDataTypes.put("LEHHRMHR", "Heart Rate");
		mDataTypes.put("LEHHRMCD", "Contact Detected");
		mDataTypes.put("LEHHRMEE", "Energy Expended");
		mDataTypes.put("LEHHRMRR", "R-R");
		mDataTypes.put("LEHBAT", "Battery Level");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mView = new AFreeChartView(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(mView);

		Bundle extras = getIntent().getExtras();
		mFilePath = extras.getString(OPEN_FILE_PATH_CODE, null);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		// lastTimeOffset = prefs.getInt("timeOffset", lastTimeOffset);
		// dryRun = prefs.getBoolean("dryrun", dryRun);
		if (mView != null) {
			final AFreeChart chart = createChart(createDataset(getName(mFilePath)));
			mView.setChart(chart);
		} else {
			Log.d(TAG, getClass().getSimpleName() + ".onResume: mView null");
			returnResult(RESULT_ERROR, "mView is null");
		}
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
		if (id == R.id.action_settings) {
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
		Intent intent = new Intent();
		if (msg != null) {
			intent.putExtra(MSG_CODE, msg);
		}
		setResult(resultCode, intent);
		finish();
	}

	/**
	 * Gets the name of the quantity in the given file.
	 * 
	 * @param filePath
	 * @return
	 */
	private String getName(String filePath) {
		String name = "Value";
		if (filePath == null) {
			return name;
		}
		Iterator<Entry<String, String>> it = mDataTypes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> pairs = it.next();
			if (filePath.contains((String) pairs.getKey())) {
				name = (String) pairs.getValue();
				break;
			}
		}
		return name;
	}

	/**
	 * Creates a chart.
	 *
	 * @param dataset
	 *            The dataset to use.
	 *
	 * @return The chart.
	 */
	private AFreeChart createChart(XYDataset dataset) {
		String name = getName(mFilePath);
		AFreeChart chart = ChartFactory.createTimeSeriesChart(
				"SenseView Monitor Results", // title
				"Date", // x-axis label
				name, // y-axis label
				dataset, // data
				true, // create legend?
				true, // generate tooltips?
				false // generate URLs?
				);
		chart.setBackgroundPaintType(new SolidColor(Color.WHITE));

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaintType(new SolidColor(Color.LTGRAY));
		plot.setDomainGridlinePaintType(new SolidColor(Color.WHITE));
		plot.setRangeGridlinePaintType(new SolidColor(Color.WHITE));
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);

		Font font = new Font("SansSerif", Typeface.NORMAL, 24);
		Font titleFont = new Font("SansSerif", Typeface.BOLD, 30);

		TextTitle title = chart.getTitle();
		title.setFont(titleFont);

		LegendTitle legend = chart.getLegend();
		legend.setItemFont(font);

		DateAxis xAxis = (DateAxis) plot.getDomainAxis();
		xAxis.setDateFormatOverride(new SimpleDateFormat("hh:mm", Locale.US));
		xAxis.setLabelFont(font);
		xAxis.setTickLabelFont(font);

		NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
		if (name == "R-R") {
			yAxis.setTickUnit(new NumberTickUnit(100));
		} else {
			yAxis.setTickUnit(new NumberTickUnit(10));
		}
		yAxis.setLabelFont(font);
		yAxis.setTickLabelFont(font);

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
	 * Creates a demo dataset, consisting of two series of monthly data.
	 *
	 * @return The dataset.
	 */
	private static XYDataset createDemoDataset() {
		TimeSeries s1 = new TimeSeries("L&G European Index Trust");
		s1.add(new Month(2, 2001), 181.8);
		s1.add(new Month(3, 2001), 167.3);
		s1.add(new Month(4, 2001), 153.8);
		s1.add(new Month(5, 2001), 167.6);
		s1.add(new Month(6, 2001), 158.8);
		s1.add(new Month(7, 2001), 148.3);
		s1.add(new Month(8, 2001), 153.9);
		s1.add(new Month(9, 2001), 142.7);
		s1.add(new Month(10, 2001), 123.2);
		s1.add(new Month(11, 2001), 131.8);
		s1.add(new Month(12, 2001), 139.6);
		s1.add(new Month(1, 2002), 142.9);
		s1.add(new Month(2, 2002), 138.7);
		s1.add(new Month(3, 2002), 137.3);
		s1.add(new Month(4, 2002), 143.9);
		s1.add(new Month(5, 2002), 139.8);
		s1.add(new Month(6, 2002), 137.0);
		s1.add(new Month(7, 2002), 132.8);

		TimeSeries s2 = new TimeSeries("L&G UK Index Trust");
		s2.add(new Month(2, 2001), 129.6);
		s2.add(new Month(3, 2001), 123.2);
		s2.add(new Month(4, 2001), 117.2);
		s2.add(new Month(5, 2001), 124.1);
		s2.add(new Month(6, 2001), 122.6);
		s2.add(new Month(7, 2001), 119.2);
		s2.add(new Month(8, 2001), 116.5);
		s2.add(new Month(9, 2001), 112.7);
		s2.add(new Month(10, 2001), 101.5);
		s2.add(new Month(11, 2001), 106.1);
		s2.add(new Month(12, 2001), 110.3);
		s2.add(new Month(1, 2002), 111.7);
		s2.add(new Month(2, 2002), 111.0);
		s2.add(new Month(3, 2002), 109.6);
		s2.add(new Month(4, 2002), 113.2);
		s2.add(new Month(5, 2002), 111.6);
		s2.add(new Month(6, 2002), 108.8);
		s2.add(new Month(7, 2002), 101.6);

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(s1);
		dataset.addSeries(s2);

		return dataset;
	}

	/**
	 * Creates the dataset, consisting of two series of monthly data.
	 *
	 * @return The dataset.
	 */
	private XYDataset createDataset(String name) {
		if (mFilePath == null) {
			String msg = getClass().getSimpleName()
					+ ".createDataset: file path is null";
			Log.d(TAG, msg);
			returnResult(RESULT_BAD_FILE, msg);
		}
		File file = new File(mFilePath);
		if (!file.exists()) {
			String msg = getClass().getSimpleName()
					+ ".createDataset: file does not exist";
			Log.d(TAG, msg);
			returnResult(RESULT_BAD_FILE, msg);
		}
		TimeSeries s1 = new TimeSeries(name);
		BufferedReader in = null;
		String[] tokens;
		String[] dateTokens;
		int lineNum = 0;
		String dateFull;
		double value;
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss",
				Locale.US);
		Date date;
		long millis;
		double max = -Double.MAX_VALUE;
		double min = Double.MAX_VALUE;
		try {
			in = new BufferedReader(new FileReader(file));
			String line;
			while ((line = in.readLine()) != null) {
				lineNum++;
				// Log.d(TAG, line);
				tokens = line.trim().split(",");
				dateFull = tokens[0] + " " + tokens[1];
				dateTokens = dateFull.split("\\.");
				date = (Date) formatter.parse(dateTokens[0]);
				String test1 = date.toString();
				millis = Math.round(Double.parseDouble(dateTokens[1]));
				// TODO check the length of dateTokens[1] to be sure it is 3
				date = new Date(date.getTime() + millis);
				value = Double.parseDouble(tokens[2]);
				if (value > max) {
					max = value;
				}
				if (value < min) {
					min = value;
				}
				String test2 = date.toString();
				s1.addOrUpdate(new Second(date), value);
				// Log.d(TAG, test2);
			}
		} catch (Exception ex) {
			String msg = getClass().getSimpleName() + ".createDataset: "
					+ " lineNum=" + lineNum + ": " + ex + ": "
					+ ex.getMessage();
			Log.d(TAG, msg);
			returnResult(RESULT_BAD_FILE, msg);
		} finally {
			try {
				if (in != null)
					in.close();
			} catch (IOException ex) {
				String msg = getClass().getSimpleName() + ".createDataset: "
						+ " Error closing file: " + ex + ": " + ex.getMessage();
				Log.d(TAG, msg);
				returnResult(RESULT_BAD_FILE, msg);
			}
		}
		Log.d(TAG, "nSeries=" + s1.getItemCount() + " lineNum=" + lineNum);
		Log.d(TAG, "max=" + max + " min=" + min);

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(s1);

		return dataset;
	}
}
