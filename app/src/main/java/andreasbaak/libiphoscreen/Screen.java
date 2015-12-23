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
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class Screen extends AppCompatActivity {
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
    private ImageView mPleaseWaitView;
    private ImageView mNetworkConnectionStatusView;

    /** Stores the current image on order to be able to restore it when the activity is
     * recreated.
     */
    private Bitmap currentImage;

    private static final String SERVER_IP = "photobooth";
    private static final int SERVER_PORT = 1338;

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
        Log.i("X", "Restoring image" );

        if (savedInstanceState != null) {
            Log.i("X", "SavedState" );

            Parcelable img = savedInstanceState.getParcelable("currentImage");
            if (img != null) {
                Log.i("X", "Image" );
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
        // Store the currently shown image in order to restore the last view.
        Log.i("X", "Saving current image: " + currentImage);

        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable("currentImage", currentImage);
    }

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

        mImageReceiver = new ImageReceiver(SERVER_IP, SERVER_PORT,
                new ImageHandler(),
                new ConnectionHandler());
        mImageReceiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        hideWaitScreen();
    }

    @Override
    public void onStop() {
        super.onStop();
        mImageReceiver.cancel(true);
    }
}
