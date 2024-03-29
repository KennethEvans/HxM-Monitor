package net.kenevans.android.hxmmonitor;

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
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;

import org.afree.chart.AFreeChart;
import org.afree.chart.ChartFactory;
import org.afree.chart.axis.AxisLocation;
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
import org.afree.graphics.SolidColor;
import org.afree.graphics.geom.Dimension;
import org.afree.graphics.geom.Font;
import org.afree.graphics.geom.RectShape;
import org.afree.ui.RectangleInsets;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author evans
 */
public class PlotActivity extends AppCompatActivity implements IConstants {
    private static final String TAG = "HxMPlot";
    private AFreeChartView mView;
    private AFreeChart mChart;
    private TimeSeriesCollection mHrDataset;
    private TimeSeriesCollection mRrDataset;
    private TimeSeriesCollection mActDataset;
    private TimeSeriesCollection mPaDataset;
    private TimeSeries mHrSeries;
    private TimeSeries mRrSeries;
    private TimeSeries mActSeries;
    private TimeSeries mPaSeries;
    private boolean mPlotHr = true;
    private boolean mPlotRr = true;
    private boolean mPlotAct = true;
    private boolean mPlotPa = true;
    private int mPlotInterval = PLOT_MAXIMUM_AGE;
    private HxMMonitorDbAdapter mDbAdapter;
    private long mPlotStartTime = INVALID_DATE;
    private long mPlotSessionStart = INVALID_DATE;
    private boolean mIsSession = false;
    private long mLastRrUpdateTime = INVALID_DATE;
    private long mLastRrTime = INVALID_DATE;

    /**
     * Handles various events fired by the Service.
     * <p/>
     * <br>
     * <br>
     * ACTION_GATT_CONNECTED: connected to a GATT server.<br>
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.<br>
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.<br>
     * ACTION_DATA_AVAILABLE: received data from the device. This can be a
     * result of read or notification operations.<br>
     */
    private final BroadcastReceiver mGattUpdateReceiver = new
            BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (HxMBleService.ACTION_DATA_AVAILABLE.equals(action)) {
                        // Log.d(TAG, "onReceive: " + action);
                        updateChart(intent);
                    } else if (HxMBleService.ACTION_ERROR.equals(action)) {
                        Log.d(TAG, "onReceive: " + action);
                        displayError(intent);
                    }
                }
            };

    /**
     * Make an IntentFilter for the actions in which we are interested.
     *
     * @return The intent filter.
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        // intentFilter.addAction(HxMBleService.ACTION_GATT_CONNECTED);
        // intentFilter.addAction(HxMBleService.ACTION_GATT_DISCONNECTED);
        // intentFilter
        // .addAction(HxMBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(HxMBleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_activity_plot);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        mView = new AFreeChartView(this);
        setContentView(mView);

        // Get whether to plot a session or current
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mIsSession = extras.getBoolean(PLOT_SESSION_CODE, false);
            if (mIsSession) {
                mPlotSessionStart = extras.getLong(
                        PLOT_SESSION_START_TIME_CODE, INVALID_DATE);
            }
        }
        if (mIsSession && (mPlotSessionStart == INVALID_DATE)) {
            Utils.errMsg(this, "Plotting a session but got invalid "
                    + "values for the start time");
            return;
        }

        // Get the database name from the default preferences
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String prefString = prefs.getString(PREF_TREE_URI, null);
        if (prefString == null) {
            Utils.errMsg(this, "Cannot find the name of the data directory");
            return;
        }

        // Open the database
        mDbAdapter = new HxMMonitorDbAdapter(this);
        mDbAdapter.open();

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume: " + "mView="
                + mView + " mHrDataset=" + mHrDataset + " mHrSeries="
                + mHrSeries);
        super.onResume();
        // Get the settings
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mPlotHr = prefs.getBoolean(PREF_PLOT_HR, true);
        mPlotRr = prefs.getBoolean(PREF_PLOT_RR, true);
        mPlotAct = prefs.getBoolean(PREF_PLOT_ACT, true);
        mPlotPa = prefs.getBoolean(PREF_PLOT_PA, true);
        String stringVal = prefs.getString(PREF_PLOT_INTERVAL, null);
        if (stringVal == null) {
            mPlotInterval = PLOT_MAXIMUM_AGE;
        } else {
            try {
                mPlotInterval = 60000 * Integer.parseInt(stringVal);
            } catch (Exception ex) {
                mPlotInterval = PLOT_MAXIMUM_AGE;
            }
        }
        if (mView != null) {
            // Create the chart
            if (mChart == null) {
                mChart = createChart();
                mView.setChart(mChart);
            }
        } else {
            Log.d(TAG, getClass().getSimpleName() + ".onResume: mView is null");
            returnResult(RESULT_ERROR, "mView is null");
        }
        // If mIsSession is true it uses mPlotSessionStart, set in onCreate
        // If mIsSession is false it uses mPlotStartTime, set here
        if (!mIsSession) {
            Log.d(TAG, "onResume: Starting registerReceiver");
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            mPlotStartTime = new Date().getTime() - mPlotInterval;
        }
        // Create the datasets and fill them
        refresh();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
        if (!mIsSession) {
            unregisterReceiver(mGattUpdateReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
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
        if (id == R.id.menu_refresh) {
            refresh();
            return true;
        } else if (id == R.id.get_view_info) {
            Utils.infoMsg(this, getViewInfo());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets the result code to send back to the calling Activity.
     *
     * @param resultCode The result code to send.
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
     * Displays the error from an ACTION_ERROR callback.
     *
     * @param intent The intent.
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
     * Refreshes the plot by getting new data.
     */
    private void refresh() {
        // The logic for whether it is a session or not is in these methods
        createDatasets();
        ((XYPlot) mChart.getPlot()).setDataset(1, mHrDataset);
        ((XYPlot) mChart.getPlot()).setDataset(2, mRrDataset);
        ((XYPlot) mChart.getPlot()).setDataset(3, mActDataset);
        ((XYPlot) mChart.getPlot()).setDataset(4, mPaDataset);
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
     * Parses the RR String and adds items to the series at the appropriate
     * times.
     *
     * @param series     The series to use.
     * @param updateTime The time of this update.
     * @param strValue   The RR String from the database.
     * @return If the operation was successful.
     */
    private boolean addRrValues(TimeSeries series, long updateTime,
                                String strValue) {
        if (series == null || strValue == null) {
            return false;
        }
        if (strValue.length() == 0) {
            // Do nothing
            return true;
        }
        if (strValue.equals(INVALID_STRING)) {
            mLastRrUpdateTime = updateTime;
            mLastRrTime = updateTime - INITIAL_RR_START_TIME;
            series.addOrUpdate(new FixedMillisecond(updateTime), Double.NaN);
            return true;
        }
        String[] tokens;
        tokens = strValue.trim().split("\\s+");
        int nTokens = tokens.length;
        if (nTokens == 0) {
            return false;
        }
        long[] times = new long[nTokens];
        double[] values = new double[nTokens];
        long lastRrTime = mLastRrTime;
        double val;
        for (int i = 0; i < nTokens; i++) {
            try {
                val = Double.parseDouble(tokens[i]);
            } catch (NumberFormatException ex) {
                return false;
            }
            lastRrTime += val;
            times[i] = lastRrTime;
            values[i] = val / 1.024;
        }
        // Make first rr time be >= mLastRrUpdateTime
        long deltaTime;
        long firstTime = times[0];
        if (firstTime < mLastRrUpdateTime) {
            deltaTime = mLastRrUpdateTime - firstTime;
            for (int i = 0; i < nTokens; i++) {
                times[i] += deltaTime;
            }
        }
        // Make all times be <= updateTime. Overrides previous if necessary.
        long lastTime = times[nTokens - 1];
        if (times[nTokens - 1] > updateTime) {
            deltaTime = lastTime - updateTime;
            for (int i = 0; i < nTokens; i++) {
                times[i] -= deltaTime;
            }
        }
        // Add to the series
        for (int i = 0; i < nTokens; i++) {
            series.addOrUpdate(new FixedMillisecond(times[i]), values[i]);
        }
        mLastRrUpdateTime = updateTime;
        mLastRrTime = times[nTokens - 1];
        return true;
    }

    /**
     * Creates a chart.
     *
     * @return The chart.
     */
    private AFreeChart createChart() {
        Log.d(TAG, "createChart");
        final boolean doLegend = true;
        AFreeChart chart = ChartFactory.createTimeSeriesChart(null, // title
                "Time", // x-axis label
                null, // y-axis label
                null, // data
                doLegend, // create legend?
                true, // generate tooltips?
                false // generate URLs?
        );

        SolidColor white = new SolidColor(Color.WHITE);
        SolidColor black = new SolidColor(Color.BLACK);
        SolidColor gray = new SolidColor(Color.GRAY);
        SolidColor ltgray = new SolidColor(Color.LTGRAY);
        SolidColor hrColor = new SolidColor(Color.argb(255, 255, 50, 50));
        SolidColor rrColor = new SolidColor(Color.argb(255, 0, 153, 255));
        SolidColor actColor = new SolidColor(Color.argb(255, 255, 225, 0));
        SolidColor paColor = new SolidColor(Color.argb(255, 255, 153, 51));

        Font font = new Font("SansSerif", Typeface.NORMAL, 30);
        Font titleFont = new Font("SansSerif", Typeface.BOLD, 36);

        float strokeSize = 5f;

        // Chart
        chart.setTitle("HxM Monitor");
        TextTitle title = chart.getTitle();
        title.setFont(titleFont);
        title.setPaintType(white);
        chart.setBackgroundPaintType(black);
        // chart.setBorderPaintType(white);
        chart.setBorderVisible(false);
        // chart.setPadding(new RectangleInsets(10.0, 10.0, 10.0, 10.0));

        // Legend
        if (doLegend) {
            LegendTitle legend = chart.getLegend();
            legend.setItemFont(font);
            legend.setBackgroundPaintType(black);
            legend.setItemPaintType(white);
        }

        // Plot
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaintType(black);
        plot.setDomainGridlinePaintType(gray);
        plot.setRangeGridlinePaintType(gray);
        plot.setOutlineVisible(true);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        // TODO Find out what these mean
        // plot.setDomainCrosshairVisible(true);
        // plot.setRangeCrosshairVisible(true);
        XYItemRenderer renderer = plot.getRenderer();
        if (renderer instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer lineShapeRenderer =
                    (XYLineAndShapeRenderer) renderer;
            lineShapeRenderer.setBaseShapesVisible(true);
            lineShapeRenderer.setBaseShapesFilled(true);
            lineShapeRenderer.setDrawSeriesLineAsPath(true);
        }

        // X axis
        DateAxis xAxis = (DateAxis) plot.getDomainAxis();
        xAxis.setDateFormatOverride(new SimpleDateFormat("hh:mm", Locale.US));
        xAxis.setLabelFont(font);
        xAxis.setLabelPaintType(white);
        xAxis.setAxisLinePaintType(white);
        xAxis.setTickLabelFont(font);
        xAxis.setTickLabelPaintType(ltgray);

        // HR
        if (mPlotHr) {
            final int axisNum = 1;
            NumberAxis axis = new NumberAxis(null);
            plot.setRangeAxis(axisNum, axis);
            plot.setDataset(axisNum, mHrDataset);
            plot.mapDatasetToRangeAxis(axisNum, axisNum);
            plot.setRangeAxisLocation(axisNum, AxisLocation.BOTTOM_OR_LEFT);
            XYItemRenderer itemRenderer = new StandardXYItemRenderer();
            itemRenderer.setSeriesPaintType(0, hrColor);
            itemRenderer.setBaseStroke(strokeSize);
            itemRenderer.setSeriesStroke(0, strokeSize);
            plot.setRenderer(axisNum, itemRenderer);

            axis.setAutoRangeIncludesZero(true);
            axis.setAutoRangeMinimumSize(10);
            axis.setTickUnit(new NumberTickUnit(5));
            // yAxis0.setLabelFont(font);
            // yAxis0.setLabelPaintType(color);
            axis.setAxisLinePaintType(hrColor);
            axis.setTickLabelFont(font);
            axis.setTickLabelPaintType(hrColor);
        }

        // RR
        if (mPlotRr) {
            final int axisNum = 2;
            NumberAxis axis = new NumberAxis(null);
            plot.setRangeAxis(axisNum, axis);
            plot.setDataset(axisNum, mRrDataset);
            plot.mapDatasetToRangeAxis(axisNum, axisNum);
            plot.setRangeAxisLocation(axisNum, AxisLocation.BOTTOM_OR_RIGHT);
            XYItemRenderer itemRenderer = new StandardXYItemRenderer();
            itemRenderer.setSeriesPaintType(0, rrColor);
            itemRenderer.setBaseStroke(strokeSize);
            itemRenderer.setSeriesStroke(0, strokeSize);
            plot.setRenderer(axisNum, itemRenderer);

            axis.setAutoRangeIncludesZero(true);
            axis.setAutoRangeMinimumSize(.25);
            // axis.setTickUnit(new NumberTickUnit(.1));
            // yAxis1.setLabelFont(font);
            // yAxis1.setLabelPaintType(color);
            axis.setLabelPaintType(rrColor);
            axis.setAxisLinePaintType(rrColor);
            axis.setTickLabelFont(font);
            axis.setTickLabelPaintType(rrColor);
        }

        // Activity
        if (mPlotAct) {
            final int axisNum = 3;
            NumberAxis axis = new NumberAxis(null);
            plot.setRangeAxis(axisNum, axis);
            plot.setDataset(axisNum, mActDataset);
            plot.mapDatasetToRangeAxis(axisNum, axisNum);
            plot.setRangeAxisLocation(axisNum, AxisLocation.BOTTOM_OR_LEFT);
            XYItemRenderer itemRenderer = new StandardXYItemRenderer();
            itemRenderer.setSeriesPaintType(0, actColor);
            itemRenderer.setBaseStroke(strokeSize);
            itemRenderer.setSeriesStroke(0, strokeSize);
            plot.setRenderer(axisNum, itemRenderer);

            axis.setAutoRangeIncludesZero(true);
            axis.setAutoRangeMinimumSize(.25);
            // axis.setTickUnit(new NumberTickUnit(.1));
            // yAxis1.setLabelFont(font);
            // yAxis1.setLabelPaintType(color);
            axis.setLabelPaintType(actColor);
            axis.setAxisLinePaintType(actColor);
            axis.setTickLabelFont(font);
            axis.setTickLabelPaintType(actColor);
        }

        // PA
        if (mPlotPa) {
            final int axisNum = 4;
            NumberAxis axis = new NumberAxis(null);
            plot.setRangeAxis(axisNum, axis);
            plot.setDataset(axisNum, mPaDataset);
            plot.mapDatasetToRangeAxis(axisNum, axisNum);
            plot.setRangeAxisLocation(axisNum, AxisLocation.BOTTOM_OR_RIGHT);
            XYItemRenderer itemRenderer = new StandardXYItemRenderer();
            itemRenderer.setSeriesPaintType(0, paColor);
            itemRenderer.setBaseStroke(strokeSize);
            itemRenderer.setSeriesStroke(0, strokeSize);
            plot.setRenderer(axisNum, itemRenderer);

            axis.setAutoRangeIncludesZero(true);
            axis.setAutoRangeMinimumSize(.25);
            // axis.setTickUnit(new NumberTickUnit(.1));
            // yAxis1.setLabelFont(font);
            // yAxis1.setLabelPaintType(color);
            axis.setLabelPaintType(paColor);
            axis.setAxisLinePaintType(paColor);
            axis.setTickLabelFont(font);
            axis.setTickLabelPaintType(paColor);
        }

        return chart;
    }

    /**
     * Updates the chart when data is received from the HxMBleService.
     *
     * @param intent The intent.
     */
    private void updateChart(Intent intent) {
        Log.d(TAG, "updateChart");
        String strValue;
        double value;

        long date = intent.getLongExtra(EXTRA_DATE, INVALID_DATE);
        if (date == INVALID_DATE) {
            return;
        }
        if (mPlotHr && mHrSeries != null) {
            strValue = intent.getStringExtra(EXTRA_HR);
            if (strValue != null && strValue.length() > 0) {
                try {
                    value = Double.parseDouble(strValue);
                } catch (NumberFormatException ex) {
                    value = Double.NaN;
                }
                if (value == INVALID_INT) {
                    value = Double.NaN;
                }
                mHrSeries.addOrUpdate(new FixedMillisecond(date), value);
            }
        }
        if (mPlotRr && mRrSeries != null) {

            if (mLastRrUpdateTime == INVALID_DATE) {
                mLastRrUpdateTime = date;
                mLastRrTime = date;
            }
            strValue = intent.getStringExtra(EXTRA_RR);
            if (strValue != null && strValue.length() > 0) {
                // boolean res = addRrValues(mRrSeries, date, strValue);
                // Don't check for errors here to avoid error storms
                addRrValues(mRrSeries, date, strValue);
            }
        }
        if (mPlotAct && mActSeries != null) {
            strValue = intent.getStringExtra(EXTRA_ACT);
            if (strValue != null && strValue.length() > 0) {
                try {
                    value = Double.parseDouble(strValue);
                } catch (NumberFormatException ex) {
                    value = Double.NaN;
                }
                if (value == INVALID_INT) {
                    value = Double.NaN;
                }
                mActSeries
                        .addOrUpdate(new FixedMillisecond(date), value / 100.);
            }
        }
        if (mPlotPa && mPaSeries != null) {
            strValue = intent.getStringExtra(EXTRA_PA);
            if (strValue != null && strValue.length() > 0) {
                try {
                    value = Double.parseDouble(strValue);
                } catch (NumberFormatException ex) {
                    value = Double.NaN;
                }
                if (value == INVALID_INT) {
                    value = Double.NaN;
                }
                mPaSeries.addOrUpdate(new FixedMillisecond(date), value / 100.);
            }
        }
    }

    /**
     * Creates the data sets and series.
     */
    private void createDatasets() {
        Log.d(TAG, "Creating datasets");
        if (mPlotHr) {
            mHrSeries = new TimeSeries("HR");
            if (!mIsSession) {
                mHrSeries.setMaximumItemAge(mPlotInterval);
            }
        } else {
            mHrSeries = null;
        }
        if (mPlotRr) {
            mRrSeries = new TimeSeries("RR");
            if (!mIsSession) {
                mRrSeries.setMaximumItemAge(mPlotInterval);
            }
        } else {
            mRrSeries = null;
        }
        if (mPlotAct) {
            mActSeries = new TimeSeries("ACT");
            if (!mIsSession) {
                mActSeries.setMaximumItemAge(mPlotInterval);
            }
        } else {
            mActSeries = null;
        }
        if (mPlotPa) {
            mPaSeries = new TimeSeries("PA");
            if (!mIsSession) {
                mPaSeries.setMaximumItemAge(mPlotInterval);
            }
        } else {
            mPaSeries = null;
        }
        if (!mIsSession) {
            try {
                mHrSeries.setMaximumItemAge(mPlotInterval);
                mRrSeries.setMaximumItemAge(mPlotInterval);
            } catch (Exception ex) {
                Utils.excMsg(this, "createDatasets: Error setting maximum age" +
                        ".", ex);
            }
        }
        mLastRrTime = INVALID_DATE;
        mLastRrUpdateTime = INVALID_DATE;
        Cursor cursor = null;
        int nHrItems = 0, nRrItems = 0, nActItems = 0, nPaItems = 0;
        int nErrors = 0;
        boolean res;
        try {
            if (mDbAdapter != null) {
                if (mIsSession) {
                    cursor = mDbAdapter
                            .fetchAllHrRrActPaDateDataForStartDate
                                    (mPlotSessionStart);
                } else {
                    cursor = mDbAdapter
                            .fetchAllHrRrActPaDateDataStartingAtDate
                                    (mPlotStartTime);
                }
                int indexDate = cursor.getColumnIndex(COL_DATE);
                int indexHr = mPlotHr ? cursor.getColumnIndex(COL_HR) : -1;
                int indexRr = mPlotRr ? cursor.getColumnIndex(COL_RR) : -1;
                int indexAct = mPlotAct ? cursor.getColumnIndex(COL_ACT) : -1;
                int indexPa = mPlotPa ? cursor.getColumnIndex(COL_PA) : -1;

                // Loop over items
                cursor.moveToFirst();
                long date;
                double hr, act, pa;
                String rrString;
                while (!cursor.isAfterLast()) {
                    date = cursor.getLong(indexDate);
                    if (indexHr > -1) {
                        hr = cursor.getInt(indexHr);
                        if (hr == INVALID_INT) {
                            hr = Double.NaN;
                        }
                        mHrSeries.addOrUpdate(new FixedMillisecond(date), hr);
                        nHrItems++;
                    }
                    if (indexRr > -1) {
                        rrString = cursor.getString(indexRr);
                        if (nRrItems == 0) {
                            mLastRrUpdateTime = date;
                            mLastRrTime = date - INITIAL_RR_START_TIME;
                        }
                        res = addRrValues(mRrSeries, date, rrString);
                        nRrItems++;
                        if (!res) {
                            nErrors++;
                        }
                    }
                    if (indexAct > -1) {
                        act = cursor.getInt(indexAct);
                        if (act == INVALID_INT) {
                            act = Double.NaN;
                        }
                        mActSeries.addOrUpdate(new FixedMillisecond(date),
                                act / 100.);
                        nActItems++;
                    }
                    if (indexPa > -1) {
                        pa = cursor.getInt(indexPa);
                        if (pa == INVALID_INT) {
                            pa = Double.NaN;
                        }
                        mPaSeries.addOrUpdate(new FixedMillisecond(date),
                                pa / 100.);
                        nPaItems++;
                    }
                    cursor.moveToNext();
                }
            }
        } catch (Exception ex) {
            Utils.excMsg(this, "Error creating datasets", ex);
        } finally {
            try {
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
        if (nErrors > 0) {
            Utils.errMsg(this, nErrors + " creating RR dataset");
        }
        if (mPlotHr) {
            Log.d(TAG, "HR dataset created with " + nHrItems + " items");
            mHrDataset = new TimeSeriesCollection();
            mHrDataset.addSeries(mHrSeries);
        }
        if (mPlotRr) {
            Log.d(TAG, "RR dataset created with " + nRrItems
                    + " items nErrors=" + nErrors);
            mRrDataset = new TimeSeriesCollection();
            mRrDataset.addSeries(mRrSeries);
        }
        if (mPlotAct) {
            Log.d(TAG, "ACT dataset created with " + nActItems + " items");
            mActDataset = new TimeSeriesCollection();
            mActDataset.addSeries(mActSeries);
        }
        if (mPlotPa) {
            Log.d(TAG, "PA dataset created with " + nPaItems + " items");
            mPaDataset = new TimeSeriesCollection();
            mPaDataset.addSeries(mPaSeries);
        }
    }

}
