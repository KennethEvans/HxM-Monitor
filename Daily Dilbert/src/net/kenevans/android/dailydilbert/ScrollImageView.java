package net.kenevans.android.dailydilbert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * A custom View to display the strip resized and centered. Fits the View in y
 * and pans in x. Does not zoom.
 */
class ScrollImageView extends View implements IConstants {
	/**
	 * Used to keep the displayed width from getting smaller than this fraction
	 * of the bitmap width. A value of .333 should show one frame of the
	 * cartoon.
	 */
	private static final float SHOW_WIDTH_FRACT = .333f;
	/** The current bitmap. */
	private Bitmap bitmap;
	/** The width of the current bitmap. */
	int bWidth = 0;
	/** The height of the current bitmap. */
	int bHeight = 0;
	/** The start x value of the current drag. */
	private Float start = 0f;
	/** The current x offset of the image. */
	private Float delta = 0f;

	/**
	 * Use this constructor when calling from code.
	 * 
	 * @param context
	 */
	public ScrollImageView(Context context) {
		super(context);
		init();
	}

	/**
	 * Use this constructor when inflating from resources.
	 * 
	 * @see android.view.View#View(android.content.Context,
	 *      android.util.AttributeSet)
	 */
	public ScrollImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	/**
	 * Does the additional setup in the constructor.
	 */
	private void init() {
		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// Handle touch events
				switch (event.getAction() & MotionEvent.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:
					start = event.getX();
					break;
				case MotionEvent.ACTION_MOVE:
					// Log.d(TAG, this.getClass().getSimpleName()
					// + ": onTouch ACTION_MOVE: start=" + start
					// + start.x + "," + start.y + " current=" + current.x + ","
					// + current.y);
					delta += event.getX() - start;
					start = event.getX();
					invalidate();
					break;
				}
				// Indicate event was handled
				return true;
			}
		});
	}

	@Override
	public void onDraw(Canvas canvas) {
		if (bitmap == null) {
			return;
		}

		// Center it
		int vHeight = this.getHeight();
		int vWidth = this.getWidth();

		// Calculate the scale to make it fit
		// float scaleWidth = ((float) vWidth) / bWidth;
		float scaleHeight = ((float) vHeight) / bHeight;

		// Don't let it get too big
		float newWidth = scaleHeight * bWidth;
		float minWidth = newWidth * SHOW_WIDTH_FRACT;
		if (minWidth > vWidth) {
			scaleHeight *= vWidth / minWidth;
		}

		// // Keep the aspect ratio
		// if (scaleHeight > scaleWidth) {
		// scaleHeight = scaleWidth;
		// } else {
		// scaleWidth = scaleHeight;
		// }

		// Create a matrix for the scaling
		Matrix matrix = new Matrix();
		// Resize the bit map
		matrix.postScale(scaleHeight, scaleHeight);
		// rotate the Bitmap
		// matrix.postRotate(45);

		// Translate
		// matrix.postTranslate(0, current.y - start.y);

		// Recreate the bitmap
		Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bWidth, bHeight,
				matrix, true);
		int newHeight = newBitmap.getHeight();
		newWidth = newBitmap.getWidth();

		// Constrain delta
		float min = -(float) (newWidth - vWidth);
		float max = 0;
		if (delta < min) {
			delta = min;
		} else if (delta > max) {
			delta = max;
		}
//		Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: start=" + start
//				+ " delta=" + delta);

		// Center it
		int y0 = (vHeight - newHeight) / 2;
		int x0 = Math.round(delta);
		canvas.drawBitmap(newBitmap, x0, y0, null);

		// Debug
		// Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: bitmap=("
		// + bitmap.getWidth() + "," + bitmap.getHeight() + ")");
		// Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: newBitmap=("
		// + newWidth + "," + newHeight + ")");
		// Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: view=("
		// + vWidth + "," + vHeight + ")");

		// Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: scale=("
		// + scaleWidth + "," + scaleHeight + ")");
		// Log.d(TAG, this.getClass().getSimpleName()
		// + ": onDraw: bitmap offset: " + y0 + "," + x0);
		// Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: canvas: "
		// + canvas.getWidth() + "," + canvas.getHeight());
		// Log.d(TAG,
		// this.getClass().getSimpleName() + ": onDraw: this: "
		// + this.getLeft() + "," + this.getRight() + ","
		// + this.getTop() + "," + this.getBottom() + " : "
		// + this.getWidth() + "," + this.getHeight());
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public void setBitmap(Bitmap bitmap) {
		this.bitmap = bitmap;
		bHeight = bitmap.getHeight();
		bWidth = bitmap.getWidth();
	}

}
