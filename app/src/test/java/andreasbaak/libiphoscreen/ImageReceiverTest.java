package andreasbaak.libiphoscreen;

import android.os.AsyncTask;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ImageReceiverTest extends TestCase {

    private MockServer server;

    class MockServer {

        public static final int IMAGE_TAKEN = 1;
        public static final int IMAGE_DATA = 2;

        public static final int SERVER_PORT = 1338;
        private ServerSocket mServerSocket;
        private Socket mClientSocket;

        Socket acceptClient() throws IOException {
            System.out.println("Accepting client connection.");
            mServerSocket = new ServerSocket(SERVER_PORT);
            return mServerSocket.accept();
        }

        void sendImageData() throws IOException {
            mClientSocket = acceptClient();
            System.out.println("Sending image data.");
            DataOutputStream outToClient = new DataOutputStream(mClientSocket.getOutputStream());
            outToClient.writeByte(IMAGE_DATA); // constant that indicates that image data will follow
            // fill byte array with some data.
            byte[] data = new byte[200];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) ((i % 0xff) & 0xff);
            }

            // Unpack in integer into byes and repack it on the receiver side.
            // We do this since we send the integer from C++ to Java in production code.
            int size = data.length;
            byte[] intAsByteArray = new byte[4];
            for (int i = 0; i < 4; ++i) {
                intAsByteArray[i] = (byte)((size % 0xff) & 0xff);
                size /= 0xff;
            }
            outToClient.write(intAsByteArray);
            outToClient.write(data);
        }

        void sendImageTaken() throws IOException {
            Socket clientSocket = acceptClient();
            System.out.println("Sending image taken.");
            DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
            outToClient.writeByte(IMAGE_TAKEN); // constant that indicates that an image has been taken
        }

        void cleanup() {
            try {
            if (mClientSocket != null) {
                mClientSocket.close();
            }
            if (mServerSocket != null) {
                    mServerSocket.close();
            }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    CountDownLatch imageTakenLatch = new CountDownLatch(1);
    CountDownLatch imageReceivedLatch = new CountDownLatch(1);
    ImageReceivedListener received = new ImageReceivedListener() {

        @Override
        public void onImageTaken() {
            imageTakenLatch.countDown();
        }

        @Override
        public void onImageReceived(byte[] imageBuffer) {
            System.out.println("Received a buffer of size " + Integer.toString(imageBuffer.length));
            imageReceivedLatch.countDown();
        }
    };

    ImageReceiver receiver = new ImageReceiver("127.0.0.1", MockServer.SERVER_PORT, received);

    @Before
    public void startServer() {
        server = new MockServer();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.cleanup();
            server = null;
        }
    }

    @Test
    public void testOnImageTakenCallback() throws InterruptedException {

        new Thread(new Runnable() {
            public void run() {
                try {
                    server.sendImageTaken();
                } catch (IOException e) {
                    e.printStackTrace();
                    fail();
                }
                return;
            }
        }).start();
        receiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        Robolectric.flushBackgroundThreadScheduler();
        Assert.assertTrue(imageTakenLatch.await(10, TimeUnit.SECONDS));
        receiver.cancel(true);
    }

    @Test
    public void testOnImageReceivedCallback() throws InterruptedException {
        new Thread(new Runnable() {
            public void run() {
                try {
                    server.sendImageData();
                } catch (IOException e) {
                    e.printStackTrace();
                    fail();
                }
                return;
            }
        }).start();

        receiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        Robolectric.flushBackgroundThreadScheduler();
        Assert.assertTrue(imageReceivedLatch.await(10, TimeUnit.SECONDS));
        receiver.cancel(true);
    }
}