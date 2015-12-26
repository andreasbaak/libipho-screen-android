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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;

/**
 * This activity shows the most recently taken image of the
 * libipho (Linux-based individual photobooth).
 * It immediately goes full-screen and does not have a non-full-screen mode.
 * If the app is not connected to the libipho server, it shows a corresponding
 * symbol on the center of the screen.
 */
public class Screen extends AppCompatActivity {
    private static final String CLASS_NAME = "Screen";

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private final Runnable mGoFullscreen = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mCameraImageView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private SizeAwareImageView mCameraImageView;
    private ImageView mCameraImageMask;
    private ImageReceiver mImageReceiver;
    private HeartbeatReceiver mHeartbeatReceiver;
    private ImageView mPleaseWaitView;
    private ImageView mNetworkConnectionStatusView;

    /** Stores the current image on order to be able to restore it when the activity is
     * recreated.
     */
    private Bitmap currentImage;

    private static final String SERVER_IP = "photobooth";
    private static final int SERVER_PORT = 1338;
    private static final int HEARTBEAT_PORT = 1339;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);

        mCameraImageView = (SizeAwareImageView)findViewById(R.id.camera_image_view);
        mCameraImageMask = (ImageView)findViewById(R.id.camera_image_mask);
        mPleaseWaitView = (ImageView)findViewById(R.id.camera_please_wait);
        mNetworkConnectionStatusView = (ImageView)findViewById(R.id.network_connection_status);

        restoreCurrentImage(savedInstanceState);
        alignSizeOfImageMask();
        scalePleaseWaitPicture();
    }

    private void scalePleaseWaitPicture() {
        // Scale the "please wait" picture down using padding of the corresponding view
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int w = metrics.heightPixels;
        int h = metrics.widthPixels;
        mPleaseWaitView.setPadding(w / 5, h / 5, h / 5, w / 5);
    }

    private void alignSizeOfImageMask() {
        mCameraImageView.setResizeListener(new ResizeListener() {
            @Override
            public void size(int w, int h) {
                mCameraImageMask.getLayoutParams().height = h;
                mCameraImageMask.getLayoutParams().width = w;
                mCameraImageMask.requestLayout();
            }
        });
    }

    private void restoreCurrentImage(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Parcelable img = savedInstanceState.getParcelable("currentImage");
            if (img != null) {
                Log.d(CLASS_NAME, "Restoring image from the previous life cycle.");
                mCameraImageView.setImageBitmap((Bitmap)img);
            }
        }
    }

    private void hide() {
        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.postDelayed(mGoFullscreen, UI_ANIMATION_DELAY);
    }

    private void hideWaitScreen() {
        mPleaseWaitView.setAlpha(0.0f);
        mCameraImageView.setAlpha(1.0f);
    }

    private void showWaitScreen() {
        mCameraImageView.setAlpha(0.5f);
        mPleaseWaitView.setAlpha(1.0f);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        // Store the currently shown image in order to restore the last view.
        Log.d(CLASS_NAME, "Saving current image: " + currentImage);
        savedInstanceState.putParcelable("currentImage", currentImage);
    }

    /**
     * Display the "please wait" screen as soon as an image has been taken.
     * Display the image and remove the "please wait" screen as soon as the image
     * data has arrived.
     */
    class ImageHandler implements ImageReceivedListener {
        @Override
        public void onImageTaken() {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mCameraImageView.setAlpha(0.5f);
                    showWaitScreen();
                }
            });
        }

        @Override
        public void onImageReceived(byte[] imageBuffer) {
            final Bitmap bitmap = decodeImageBuffer(imageBuffer);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideWaitScreen();
                    mCameraImageView.setImageBitmap(bitmap);
                }
            });
            currentImage = bitmap;
        }

        private Bitmap decodeImageBuffer(byte[] imageBuffer) {
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBuffer);
            return BitmapFactory.decodeStream(inputStream);
        }
    }

    /**
     * Display the connection status.
     * If we are not yet connected, show a bold symbol on the center of the screen.
     * As soon as we are connected, remove the symbol so that the screen can be used
     * for the image in full screen.
     */
    class ConnectionHandler implements NetworkConnectionStatusListener {
        private void fadeOut(final View view)
        {
            Animation fadeOut = new AlphaAnimation(1, 0);
            fadeOut.setInterpolator(new AccelerateInterpolator());
            fadeOut.setDuration(2000);

            fadeOut.setAnimationListener(new Animation.AnimationListener()
            {
                public void onAnimationEnd(Animation animation)
                {
                    view.setVisibility(View.GONE);
                }
                public void onAnimationRepeat(Animation animation) {}
                public void onAnimationStart(Animation animation) {}
            });

            view.startAnimation(fadeOut);
        }

        @Override
        public void onConnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mNetworkConnectionStatusView.setImageResource(R.drawable.connected);
                    fadeOut(mNetworkConnectionStatusView);
                }
            });

        }

        @Override
        public void onDisconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mNetworkConnectionStatusView.setImageResource(R.drawable.disconnected);
                    mNetworkConnectionStatusView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        hide();

        mHeartbeatReceiver = new HeartbeatReceiver(SERVER_IP, HEARTBEAT_PORT);
        mHeartbeatReceiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        mImageReceiver = new ImageReceiver(SERVER_IP, SERVER_PORT,
                new ImageHandler(),
                new ConnectionHandler());
        mImageReceiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        hideWaitScreen();
    }

    @Override
    public void onStop() {
        super.onStop();
        mHeartbeatReceiver.cancel(true);
        mImageReceiver.cancel(true);
    }
}
