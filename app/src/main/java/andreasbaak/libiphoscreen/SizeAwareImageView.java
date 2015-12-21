package andreasbaak.libiphoscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * An ImageView that notifies a listener as soon as the
 * ImageView is resized to a different size.
 */
public class SizeAwareImageView extends ImageView {
    private ResizeListener rl;
    Point lastSize;

    public SizeAwareImageView(Context context) {
        super(context);
    }

    public SizeAwareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SizeAwareImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setResizeListener(ResizeListener rl) {
        this.rl = rl;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        computeActualImageSize();
        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        computeActualImageSize();
    }

    private void computeActualImageSize() {
        float[] f = new float[9];
        getImageMatrix().getValues(f);
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = getDrawable();
        if (d == null) {
            return;
        }
        final int originalImageWidth = d.getIntrinsicWidth();
        final int origImageHeight = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        // By using ceil, we actually make sure that the mask
        // is at least as big as the actual image.
        final int actualImageWidth = (int)Math.ceil(originalImageWidth * scaleX);
        final int actualImageHeight = (int)Math.ceil(origImageHeight * scaleY);

        Point size = new Point(actualImageWidth, actualImageHeight);
        // Call listener only if the size changed.
        if (lastSize == null || !size.equals(lastSize)) {
            rl.size(actualImageWidth, actualImageHeight);
        }
        lastSize = size;
    }
}
