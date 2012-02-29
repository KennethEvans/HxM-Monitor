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

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * ImageView to handle the DailyDilbert image and input. Uses a Matrix to
 * determine the image size and placement.
 */
public class DilbertImageView extends ImageView implements IConstants {
	// These matrices will be used to move and zoom image
	Matrix matrix = new Matrix();
	Matrix savedMatrix = new Matrix();

	/**
	 * Used to keep the displayed width from getting smaller than this fraction
	 * of the bitmap width. A value of .333 should show one frame of the
	 * cartoon.
	 */
	private static final float SHOW_WIDTH_FRACT = .333f;

	// We can be in one of these 3 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	int mode = NONE;

	// We can be in one of 3 levels 0-2
	public static final int UNDEFINED_LEVEL = -Integer.MAX_VALUE;
	int level = UNDEFINED_LEVEL;

	// Remember some things for zooming
	private PointF start = new PointF();
	private PointF mid = new PointF();
	private float oldDist = 1f;

	Context context;

	private DailyDilbertActivity activity;
	private GestureDetector gestureDetector;

	/**
	 * Use this constructor when calling from code.
	 * 
	 * @param context
	 */
	public DilbertImageView(Context context) {
		super(context);
		super.setClickable(true);
		this.context = context;
		init();
	}

	/**
	 * Use this constructor when inflating from resources.
	 * 
	 * @see android.view.View#View(android.content.Context,
	 *      android.util.AttributeSet)
	 */
	public DilbertImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		super.setClickable(true);
		this.context = context;
		init();
	}

	/**
	 * Does the additional setup in the constructor.
	 */
	private void init() {
		setImageMatrix(matrix);
		setScaleType(ScaleType.MATRIX);

		gestureDetector = new GestureDetector(new DilbertGestureDetector());

		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent ev) {
				// Handle touch events here...
				switch (ev.getAction() & MotionEvent.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:
					savedMatrix.set(matrix);
					start.set(ev.getX(), ev.getY());
					Log.d(TAG, "mode=DRAG");
					mode = DRAG;
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					oldDist = spacing(ev);
					Log.d(TAG, "oldDist=" + oldDist);
					if (oldDist > 10f) {
						savedMatrix.set(matrix);
						midPoint(mid, ev);
						mode = ZOOM;
						Log.d(TAG, "mode=ZOOM");
					}
					break;
				case MotionEvent.ACTION_UP:
					int xDiff = (int) Math.abs(ev.getX() - start.x);
					int yDiff = (int) Math.abs(ev.getY() - start.y);
					if (xDiff < 8 && yDiff < 8) {
						performClick();
					}
				case MotionEvent.ACTION_POINTER_UP:
					mode = NONE;
					Log.d(TAG, "mode=NONE");
					break;
				case MotionEvent.ACTION_MOVE:
					if (mode == DRAG) {
						matrix.set(savedMatrix);
						if (level == 1) {
							// Allow drag in x only
							matrix.postTranslate(ev.getX() - start.x, 0);
						} else if (level == 2) {
							// Allow drag in both directions
							matrix.postTranslate(ev.getX() - start.x, ev.getY()
									- start.y);
						}
					} else if (mode == ZOOM) {
						// Allow scale only in level 2
						if (level == 2) {
							float newDist = spacing(ev);
							Log.d(TAG, "newDist=" + newDist);
							if (newDist > 10f) {
								matrix.set(savedMatrix);
								float scale = newDist / oldDist;
								matrix.postScale(scale, scale, mid.x, mid.y);
							}
						}
					}
					break;
				}
				setImageMatrix(matrix);

				// Next send it to the GestureDetector
				return gestureDetector.onTouchEvent(ev);

				// Indicate event was handled
				// return true;
			}
		});
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onLayout:");
		super.onLayout(changed, left, top, right, bottom);
		// Layout should be done
		resetLevel(level);
	}

	/**
	 * Set the values corresponding to a new level
	 */
	void resetLevel(int level) {
		Log.d(TAG, this.getClass().getSimpleName() + ": resetLevel: old level="
				+ this.level + " request level=" + level);
		// If the level has not been set, default to 0
		if (level == UNDEFINED_LEVEL) {
			level = 0;
		} else {
			// Wrap the level
			if (level > 2) {
				level = 0;
			} else if (level < 0) {
				level = 2;
			}
		}
		// // Do nothing if the level has not changed
		// if (level != -1 && level == this.level) {
		// return;
		// }
		this.level = level;
		switch (level) {
		case 0:
			initializeLevel0();
			break;
		case 1:
			initializeLevel1();
			break;
		case 2:
			initializeLevel2();
			break;
		}
		Log.d(TAG, this.getClass().getSimpleName() + ": resetLevel: new level="
				+ level);
	}

	/**
	 * Do the initialization necessary for level 0.
	 */
	private void initializeLevel0() {
		Log.d(TAG, this.getClass().getSimpleName() + ": initializeLevel0:");
		Drawable drawable = getDrawable();
		if (drawable == null) {
			return;
		}
		matrix.reset();
		int dWidth = drawable.getIntrinsicWidth();
		int dHeight = drawable.getIntrinsicHeight();
		// getWidth and getHeight should have been set
		int vWidth = getWidth();
		int vHeight = getHeight();
		Log.d(TAG, this.getClass().getSimpleName()
				+ ": initializeLevel0: drawable: " + dWidth + "," + dHeight
				+ " view: " + vWidth + "," + vHeight);
		if (vHeight == 0 || vWidth == 0) {
			return;
		}

		// Fit to the view
		float scale = 1;
		if ((vHeight / dHeight) >= (vWidth / dWidth)) {
			scale = (float) vWidth / (float) dWidth;
		} else {
			scale = (float) vHeight / (float) dHeight;
		}
		savedMatrix.set(matrix);
		matrix.set(savedMatrix);
		matrix.postScale(scale, scale, 0, 0);
		setImageMatrix(matrix);

		// Center the image
		float redundantYSpace = (float) vHeight - (scale * (float) dHeight);
		float redundantXSpace = (float) vWidth - (scale * (float) dWidth);

		redundantYSpace /= (float) 2;
		redundantXSpace /= (float) 2;

		savedMatrix.set(matrix);
		matrix.set(savedMatrix);
		matrix.postTranslate(redundantXSpace, redundantYSpace);
		setImageMatrix(matrix);

//		Toast.makeText(context, "No Pan or Zoom", Toast.LENGTH_SHORT).show();
	}

	/**
	 * Do the initialization necessary for level 1.
	 */
	private void initializeLevel1() {
		Log.d(TAG, this.getClass().getSimpleName() + ": initializeLevel1:");
		Drawable drawable = getDrawable();
		if (drawable == null) {
			return;
		}
		matrix.reset();
		int dWidth = drawable.getIntrinsicWidth();
		int dHeight = drawable.getIntrinsicHeight();
		// getWidth and getHeight should have been set
		int vWidth = getWidth();
		int vHeight = getHeight();
		Log.d(TAG, this.getClass().getSimpleName()
				+ ": initializeLevel1: drawable: " + dWidth + "," + dHeight
				+ " view: " + vWidth + "," + vHeight);
		if (vHeight == 0 || vWidth == 0) {
			return;
		}

		// Fit to the height of the view
		float scale = (float) vHeight / (float) dHeight;

		// Don't let it get too big
		float newWidth = scale * vWidth;
		float minWidth = newWidth * SHOW_WIDTH_FRACT;
		if (minWidth > vWidth) {
			scale *= vWidth / minWidth;
		}

		savedMatrix.set(matrix);
		matrix.set(savedMatrix);
		matrix.postScale(scale, scale, 0, 0);
		setImageMatrix(matrix);

		// Center the image vertically
		float redundantYSpace = (float) vHeight - (scale * (float) dHeight);

		redundantYSpace /= (float) 2;

		savedMatrix.set(matrix);
		matrix.set(savedMatrix);
		matrix.postTranslate(0, redundantYSpace);
		setImageMatrix(matrix);

//		Toast.makeText(context, "Horizontal Pan", Toast.LENGTH_SHORT).show();
	}

	/**
	 * Do the initialization necessary for level 2.
	 */
	private void initializeLevel2() {
		Log.d(TAG, this.getClass().getSimpleName() + ": initializeLevel2:");
		
		// If it is the identity matrix, then center it
		Drawable drawable = getDrawable();
		if (matrix.isIdentity() && drawable != null) {
			// Center the image vertically
			if (drawable != null) {
				int dWidth = drawable.getIntrinsicWidth();
				int dHeight = drawable.getIntrinsicHeight();
				int vWidth = getWidth();
				int vHeight = getHeight();
				float scale = 1.f;
				float redundantYSpace = (float) vHeight - (scale * (float) dHeight);
				float redundantXSpace = (float) vWidth - (scale * (float) dWidth);

				redundantYSpace /= (float) 2;
				redundantXSpace /= (float) 2;

				savedMatrix.set(matrix);
				matrix.set(savedMatrix);
				matrix.postTranslate(redundantXSpace, redundantYSpace);
				setImageMatrix(matrix);
			}
		}

		Toast.makeText(context, "Pan and Zoom", Toast.LENGTH_SHORT).show();
	}

	/** Determines the space between the first two fingers */
	private float spacing(MotionEvent ev) {
		// ...
		float x = ev.getX(0) - ev.getX(1);
		float y = ev.getY(0) - ev.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	/** Calculates the mid point of the first two fingers */
	private void midPoint(PointF point, MotionEvent ev) {
		// ...
		float x = ev.getX(0) + ev.getX(1);
		float y = ev.getY(0) + ev.getY(1);
		point.set(x / 2, y / 2);
	}

	/**
	 * Shows an event in the LogCat view, for debugging.
	 * 
	 * @param ev
	 */
	public static void dumpEvent(MotionEvent ev) {
		String names[] = { "DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE",
				"POINTER_DOWN", "POINTER_UP", "7?", "8?", "9?" };
		StringBuilder sb = new StringBuilder();
		int action = ev.getAction();
		int actionCode = action & MotionEvent.ACTION_MASK;
		sb.append("event ACTION_").append(names[actionCode]);
		if (actionCode == MotionEvent.ACTION_POINTER_DOWN
				|| actionCode == MotionEvent.ACTION_POINTER_UP) {
			sb.append("(pid ").append(
					action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
			sb.append(")");
		}
		sb.append("[");
		for (int i = 0; i < ev.getPointerCount(); i++) {
			sb.append("#").append(i);
			// May be a problem with API 1.6 or less (Donut, Cupcake)
			sb.append("(pid ").append(ev.getPointerId(i));
			sb.append(")=").append((int) ev.getX(i));
			sb.append(",").append((int) ev.getY(i));
			if (i + 1 < ev.getPointerCount())
				sb.append(";");
		}
		sb.append("]");
		Log.d(TAG, sb.toString());
	}

	/**
	 * Gets the current level.
	 * 
	 * @return
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Sets the current level.
	 * 
	 * @param level
	 */
	public void setLevel(int level) {
		resetLevel(level);
	}

	/**
	 * Gets the associated DailyDilbertActivity.
	 * 
	 * @return
	 */
	public DailyDilbertActivity getActivity() {
		return activity;
	}

	/**
	 * Sets the associated DailyDilbertActivity.
	 * 
	 * @param activity
	 */
	public void setActivity(DailyDilbertActivity activity) {
		this.activity = activity;
	}

	/**
	 * Gesture detector. Based on an example at<br>
	 * <br>
	 * http://www.codeshogun.com/blog/2009
	 * /04/16/how-to-implement-swipe-action-in-android/
	 */
	class DilbertGestureDetector extends SimpleOnGestureListener {
		private static final int SWIPE_MIN_DISTANCE = 120;
		private static final int SWIPE_MAX_OFF_PATH = 250;
		private static final int SWIPE_THRESHOLD_VELOCITY = 200;

		@Override
		public boolean onFling(MotionEvent ev1, MotionEvent ev2,
				float velocityX, float velocityY) {
			Log.d(TAG, this.getClass().getSimpleName() + ": onFling:");
			// Only do this in level 0
			if (level != 0) {
				return false;
			}
			if (activity == null) {
				Log.d(TAG, this.getClass().getSimpleName()
						+ ": onFling: Activity is null");
			}
			CalendarDay cDay = activity.getCDay();
			if (cDay == null) {
				Log.d(TAG, this.getClass().getSimpleName()
						+ ": onFling: cDay is null");
			}
			try {
				// Check if it is a horizontal swipe
				if (Math.abs(ev1.getY() - ev2.getY()) > SWIPE_MAX_OFF_PATH) {
					Log.d(TAG, this.getClass().getSimpleName()
							+ ": onFling: Not horizontal " + cDay);
					return false;
				}
				// Branch on direction
				if (ev1.getX() - ev2.getX() > SWIPE_MIN_DISTANCE
						&& Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					Log.d(TAG, this.getClass().getSimpleName()
							+ ": onFling: To left (increment) " + cDay);
					// To left
					activity.getStrip(cDay.incrementDay(+1));
					return true;
				} else if (ev2.getX() - ev1.getX() > SWIPE_MIN_DISTANCE
						&& Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					// To right
					Log.d(TAG, this.getClass().getSimpleName()
							+ ": onFling: To right (decrement) " + cDay);
					activity.getStrip(cDay.incrementDay(-1));
					return true;
				}
			} catch (Exception ex) {
				// Do nothing
				Log.d(TAG, this.getClass().getSimpleName() + ": onFling: "
						+ cDay + " Exception: " + ex.getMessage());
			}
			Log.d(TAG, this.getClass().getSimpleName() + ": onFling: Nothing "
					+ cDay);
			return false;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent ev) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": onSingleTapConfirmed:");
			if (activity == null) {
				Log.d(TAG, this.getClass().getSimpleName()
						+ ": onSingleTapConfirmed: Activity is null");
			}
			CalendarDay cDay = activity.getCDay();
			if (cDay == null) {
				Log.d(TAG, this.getClass().getSimpleName()
						+ ": onSingleTapConfirmed: cDay is null");
			}
			// DEBUG Time
			activity.startTimer();
			int vWidth = getWidth();
			if (ev.getX() < vWidth / 3) {
				activity.getStrip(cDay.incrementDay(-1));
			} else if (ev.getX() > 2 * vWidth / 3) {
				activity.getStrip(cDay.incrementDay(1));
			} else {
				resetLevel(level + 1);
			}
			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent ev) {
			// We only want ACTION_UP actions
			int action = ev.getAction() & MotionEvent.ACTION_MASK;
			if (action != MotionEvent.ACTION_UP) {
				return false;
			}
			Log.d(TAG, this.getClass().getSimpleName() + ": onDoubleTapEvent:");
			if (activity == null) {
				Log.d(TAG, this.getClass().getSimpleName()
						+ ": onDoubleTapEvent: Activity is null");
			}
			CalendarDay cDay = activity.getCDay();
			if (cDay == null) {
				Log.d(TAG, this.getClass().getSimpleName()
						+ ": onDoubleTapEvent: cDay is null");
			}
			// DEBUG TIME
			activity.startTimer();
			int vWidth = getWidth();
			if (ev.getX() < vWidth / 3) {
				activity.getStrip(CalendarDay.first());
			} else if (ev.getX() > 2 * vWidth / 3) {
				activity.getStrip(CalendarDay.now());
			} else {
				resetLevel(level - 1);
			}
			return true;
		}
	}

}
