/*
libipho-screen-android is the Android front-end of the libipho photobooth.

Copyright (C) 2015 Andreas Baak (andreas.baak@gmail.com)

This file is part of libipho-screen-android.

libipho-screen-server is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

libipho-screen-server is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with libipho-screen-android. If not, see <http://www.gnu.org/licenses/>.
*/

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

    /**
     * The provided listener is notified as soon as the size of the drawn image
     * changes.
     * @param rl Listener.
     */
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

        final Drawable d = getDrawable();
        if (d == null) {
            return;
        }
        final int originalImageWidth = d.getIntrinsicWidth();
        final int origImageHeight = d.getIntrinsicHeight();

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
