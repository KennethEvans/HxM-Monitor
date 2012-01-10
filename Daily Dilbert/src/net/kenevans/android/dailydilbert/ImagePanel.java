package net.kenevans.android.dailydilbert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * A custom View to display the strip resized and centered.
 * 
 */
class ImagePanel extends View implements IConstants {
	Bitmap bitmap;

	/**
	 * Use this constructor when calling from code.
	 * 
	 * @param context
	 */
	public ImagePanel(Context context) {
		super(context);
	}

	/**
	 * Use this constructor when inflating from resources.
	 * 
	 * @see android.view.View#View(android.content.Context,
	 *      android.util.AttributeSet)
	 */
	public ImagePanel(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onDraw(Canvas canvas) {
		if (bitmap == null) {
			return;
		}

		// Center it
		int vHeight = this.getHeight();
		int vWidth = this.getWidth();

		int bHeight = bitmap.getHeight();
		int bWidth = bitmap.getWidth();

		// Calculate the scale to make it fit
		float scaleWidth = ((float) vWidth) / bWidth;
		float scaleHeight = ((float) vHeight) / bHeight;

		// Keep the aspect ratio
		if (scaleHeight > scaleWidth) {
			scaleHeight = scaleWidth;
		} else {
			scaleWidth = scaleHeight;
		}

		// Create a matrix for the scaling
		Matrix matrix = new Matrix();
		// resize the bit map
		matrix.postScale(scaleWidth, scaleHeight);
		// rotate the Bitmap
		// matrix.postRotate(45);

		// Recreate the bitmap
		Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bWidth, bHeight,
				matrix, true);
		bHeight = newBitmap.getHeight();
		bWidth = newBitmap.getWidth();

		// Center it
		int x0 = (vWidth - bWidth) / 2;
		int y0 = (vHeight - bHeight) / 2;
		canvas.drawBitmap(newBitmap, x0, y0, null);

		// Debug
		Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: bitmap=("
				+ bitmap.getWidth() + "," + bitmap.getHeight() + ")");
		Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: newBitmap=("
				+ bWidth + "," + bHeight + ")");
		Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: scale=("
				+ scaleWidth + "," + scaleHeight + ")");
		Log.d(TAG, this.getClass().getSimpleName()
				+ ": onDraw: bitmap offset: " + y0 + "," + x0);
		Log.d(TAG, this.getClass().getSimpleName() + ": onDraw: canvas: "
				+ canvas.getWidth() + "," + canvas.getHeight());
		Log.d(TAG,
				this.getClass().getSimpleName() + ": onDraw: this: "
						+ this.getLeft() + "," + this.getRight() + ","
						+ this.getTop() + "," + this.getBottom() + " : "
						+ this.getWidth() + "," + this.getHeight());
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public void setBitmap(Bitmap bitmap) {
		this.bitmap = bitmap;
	}

}
