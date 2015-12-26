package andreasbaak.libiphoscreen;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/*
 * Listen to a connection and ignore all packets that are read
 * from the connection.
 */
public class HeartbeatReceiver extends AsyncTask<Void, Void, Void> {
    private static final String CLASS_NAME = "HeartbeatReceiver";
    private final String mServerIp;
    private final int mServerPort;

    HeartbeatReceiver(String serverIp, int serverPort) {
        mServerIp = serverIp;
        mServerPort = serverPort;
    }

    @Override
    protected Void doInBackground(Void... params) {
        SocketChannel socketChannel = null;
        while (!isCancelled()) {
            try {
                InetAddress serverAddr = InetAddress.getByName(mServerIp);
                Log.d(CLASS_NAME, String.format("Connecting heartbeat to %s", serverAddr.toString()));

                // This will block until a connection has been established or an IOException occurred.
                boolean connected = false;
                while (!connected && !isCancelled()) {
                    try {
                        Log.d(CLASS_NAME, "Opening Heartbeat channel.");
                        socketChannel = SocketChannel.open();
                        socketChannel.configureBlocking(true);
                        socketChannel.connect(new InetSocketAddress(serverAddr, mServerPort));
                        connected = true;
                    } catch (Exception e) {
                        Log.e(CLASS_NAME, "Exception while trying to connect heartbeat to server: " + e.getClass());
                        if (socketChannel != null) {
                            try {
                                socketChannel.close();
                            } catch (Exception ioe) {
                            }
                        }
                        Thread.sleep(500);
                    }
                }

                ByteBuffer rcvBuffer = ByteBuffer.allocate(1);
                while (!isCancelled()) {
                    Log.d(CLASS_NAME, "Receiving heartbeat.");

                    int numBytesRead = socketChannel.read(rcvBuffer);
                    if (numBytesRead == -1) {
                        Log.e(CLASS_NAME, "Received EOF on the heartbeat channel.");
                        break;
                    }
                    if (numBytesRead == 0) {
                        Log.e(CLASS_NAME, "Could not read next command.");
                    }
                    rcvBuffer.clear();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socketChannel != null) {
                    try {
                        socketChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        Log.e(CLASS_NAME, "HeartbeatReceiver finished.");
        return null;
    }
}
