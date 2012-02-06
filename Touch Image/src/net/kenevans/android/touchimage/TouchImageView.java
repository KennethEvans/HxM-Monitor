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

package net.kenevans.android.touchimage;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * Based on an example at:
 * 
 * @link 
 *       http://stackoverflow.com/questions/2537238/how-can-i-get-zoom-functionality
 *       -for-images
 * 
 */
public class TouchImageView extends ImageView implements IConstants {

	// These matrices will be used to move and zoom image
	Matrix matrix = new Matrix();
	Matrix savedMatrix = new Matrix();

	/**
	 * Determines if the image is to be fit to the view when onMeasure is next
	 * called. Will be set to false after the fit isdone.
	 */
	boolean fitImage = false;

	// We can be in one of these 3 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	int mode = NONE;

	// Remember some things for zooming
	private PointF start = new PointF();
	private PointF mid = new PointF();
	private float oldDist = 1f;

	Context context;

	/**
	 * Use this constructor when calling from code.
	 * 
	 * @param context
	 */
	public TouchImageView(Context context) {
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
	public TouchImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		super.setClickable(true);
		this.context = context;
		init();
	}

	/**
	 * Does the additional setup in the constructor.
	 */
	private void init() {
		matrix.setTranslate(1f, 1f);
		setImageMatrix(matrix);
		setScaleType(ScaleType.MATRIX);

		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// Handle touch events here...
				switch (event.getAction() & MotionEvent.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:
					savedMatrix.set(matrix);
					start.set(event.getX(), event.getY());
					Log.d(TAG, "mode=DRAG");
					mode = DRAG;
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					oldDist = spacing(event);
					Log.d(TAG, "oldDist=" + oldDist);
					if (oldDist > 10f) {
						savedMatrix.set(matrix);
						midPoint(mid, event);
						mode = ZOOM;
						Log.d(TAG, "mode=ZOOM");
					}
					break;
				case MotionEvent.ACTION_UP:
					int xDiff = (int) Math.abs(event.getX() - start.x);
					int yDiff = (int) Math.abs(event.getY() - start.y);
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
						matrix.postTranslate(event.getX() - start.x,
								event.getY() - start.y);
					} else if (mode == ZOOM) {
						float newDist = spacing(event);
						Log.d(TAG, "newDist=" + newDist);
						if (newDist > 10f) {
							matrix.set(savedMatrix);
							float scale = newDist / oldDist;
							matrix.postScale(scale, scale, mid.x, mid.y);
						}
					}
					break;
				}
				setImageMatrix(matrix);
				return true; // indicate event was handled
			}
		});
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onMeasure: fitImage="
				+ fitImage);
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		// Fit the image if specified only if the sizes are determined
		if (fitImage) {
			fitImage = false;
			fitImage();
		}
	}

	/**
	 * Sets the image to fit inside the view and centers it. super.onMeasure
	 * must have been called first.
	 */
	public void fitImage() {
		Drawable drawable = getDrawable();
		Log.d(TAG, this.getClass().getSimpleName() + ": fitImage:");
		if (drawable == null) {
			return;
		}
		int dWidth = drawable.getIntrinsicWidth();
		int dHeight = drawable.getIntrinsicHeight();
		// These should have been defined, but getWidth and getheight may not
		// have been
		int vWidth = getMeasuredWidth();
		int vHeight = getMeasuredHeight();
		Log.d(TAG, this.getClass().getSimpleName() + ": fitImage: drawable: "
				+ dWidth + "," + dHeight + " view: " + vWidth + "," + vHeight);
		if (vHeight == 0 || vWidth == 0) {
			return;
		}

		// Fit to view
		float scale;
		if ((vHeight / dHeight) >= (vWidth / dWidth)) {
			scale = (float) vWidth / (float) dWidth;
		} else {
			scale = (float) vHeight / (float) dHeight;
		}

		savedMatrix.set(matrix);
		matrix.set(savedMatrix);
		matrix.postScale(scale, scale, mid.x, mid.y);
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
	}

	// // TODO This doesn't seem to work as the width and height may not have
	// been
	// // determined when it is logical to call it
	// public void setImage(Bitmap bm, int displayWidth, int displayHeight) {
	// super.setImageBitmap(bm);
	//
	// // Fit to screen.
	// float scale;
	// if ((displayHeight / bm.getHeight()) >= (displayWidth / bm.getWidth())) {
	// scale = (float) displayWidth / (float) bm.getWidth();
	// } else {
	// scale = (float) displayHeight / (float) bm.getHeight();
	// }
	//
	// savedMatrix.set(matrix);
	// matrix.set(savedMatrix);
	// matrix.postScale(scale, scale, mid.x, mid.y);
	// setImageMatrix(matrix);
	//
	// // Center the image
	// float redundantYSpace = (float) displayHeight
	// - (scale * (float) bm.getHeight());
	// float redundantXSpace = (float) displayWidth
	// - (scale * (float) bm.getWidth());
	//
	// redundantYSpace /= (float) 2;
	// redundantXSpace /= (float) 2;
	//
	// savedMatrix.set(matrix);
	// matrix.set(savedMatrix);
	// matrix.postTranslate(redundantXSpace, redundantYSpace);
	// setImageMatrix(matrix);
	// }

	/** Determines the space between the first two fingers */
	private float spacing(MotionEvent event) {
		// ...
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	/** Calculates the mid point of the first two fingers */
	private void midPoint(PointF point, MotionEvent event) {
		// ...
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}

	/**
	 * Shows an event in the LogCat view, for debugging.
	 * 
	 * @param event
	 */
	public static void dumpEvent(WrapMotionEvent event) {
		String names[] = { "DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE",
				"POINTER_DOWN", "POINTER_UP", "7?", "8?", "9?" };
		StringBuilder sb = new StringBuilder();
		int action = event.getAction();
		int actionCode = action & MotionEvent.ACTION_MASK;
		sb.append("event ACTION_").append(names[actionCode]);
		if (actionCode == MotionEvent.ACTION_POINTER_DOWN
				|| actionCode == MotionEvent.ACTION_POINTER_UP) {
			sb.append("(pid ").append(
					action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
			sb.append(")");
		}
		sb.append("[");
		for (int i = 0; i < event.getPointerCount(); i++) {
			sb.append("#").append(i);
			// May be a problem with API 1.6 or less (Donut, Cupcake)
			sb.append("(pid ").append(event.getPointerId(i));
			sb.append(")=").append((int) event.getX(i));
			sb.append(",").append((int) event.getY(i));
			if (i + 1 < event.getPointerCount())
				sb.append(";");
		}
		sb.append("]");
		Log.d(TAG, sb.toString());
	}

	/**
	 * Gets the value of fitImage.
	 * 
	 * @see #fitImage
	 * @return The value of fitImage
	 */
	public boolean getFitImage() {
		return fitImage;
	}

	/**
	 * Sets the value of fitImage. The value will be reset to false after
	 * onMeasure is next called.
	 * 
	 * @param fitImage
	 * @see #fitImage
	 */
	public void setFitImage(boolean fitImage) {
		Log.d(TAG, this.getClass().getSimpleName() + ": setFitImage: fitImage="
				+ fitImage);
		this.fitImage = fitImage;
	}

	/**
	 * Wrapper class to get around different Android versions (Donut/Cupcake).
	 * 
	 * <ul>
	 * <li>4.0.x Ice Cream Sandwich 14-16</li>
	 * <li>3.x.x Honeycomb 11-13</li>
	 * <li>2.3.x Gingerbread 9-10</li>
	 * <li>2.2 Froyo 8</li>
	 * <li>2.0, 2.1 Eclair 7</li>
	 * <li>1.6 Donut 4</li>
	 * <li>1.5 Cupcake</li>
	 * </ul>
	 */
	public static class WrapMotionEvent {
		protected MotionEvent event;

		protected WrapMotionEvent(MotionEvent event) {
			this.event = event;
		}

		static public WrapMotionEvent wrap(MotionEvent event) {
			try {
				return new EclairMotionEvent(event);
			} catch (VerifyError e) {
				return new WrapMotionEvent(event);
			}
		}

		public int getAction() {
			return event.getAction();
		}

		public float getX() {
			return event.getX();
		}

		public float getX(int pointerIndex) {
			verifyPointerIndex(pointerIndex);
			return getX();
		}

		public float getY() {
			return event.getY();
		}

		public float getY(int pointerIndex) {
			verifyPointerIndex(pointerIndex);
			return getY();
		}

		public int getPointerCount() {
			return 1;
		}

		public int getPointerId(int pointerIndex) {
			verifyPointerIndex(pointerIndex);
			return 0;
		}

		private void verifyPointerIndex(int pointerIndex) {
			if (pointerIndex > 0) {
				throw new IllegalArgumentException(
						"Invalid pointer index for Donut/Cupcake");
			}
		}

	}

	/**
	 * Wrapper class to get around different Android versions (Donut/Cupcake).
	 * 
	 * <ul>
	 * <li>4.0.x Ice Cream Sandwich 14-16</li>
	 * <li>3.x.x Honeycomb 11-13</li>
	 * <li>2.3.x Gingerbread 9-10</li>
	 * <li>2.2 Froyo 8</li>
	 * <li>2.0, 2.1 Eclair 7</li>
	 * <li>1.6 Donut 4</li>
	 * <li>1.5 Cupcake</li>
	 * </ul>
	 */
	public static class EclairMotionEvent extends WrapMotionEvent {

		protected EclairMotionEvent(MotionEvent event) {
			super(event);
		}

		public float getX(int pointerIndex) {
			return event.getX(pointerIndex);
		}

		public float getY(int pointerIndex) {
			return event.getY(pointerIndex);
		}

		public int getPointerCount() {
			return event.getPointerCount();
		}

		public int getPointerId(int pointerIndex) {
			return event.getPointerId(pointerIndex);
		}

	}

}
