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

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Connect to a server using a stream socket (TCP).
 * Read all packets that arrive on the socket and simply ignore them.
 *
 * The purpose of this class is to enable the server to detect when a client
 * disconnects (on purpose or accidentally). If the client is not available anymore,
 * the server will get an error as soon as she sends packets to the client.
 *
 * By contrast, the client will read EOF as soon as the server disconnects.
 *
 * This class really does not do anything else than ignoring all incoming TCP packets.
 *
 * The class is implemented as an AsyncTask in order to easily run in the background.
 * The HeartbeatReceiver is interruptible. As soon as the class is interrupted via the
 * cancel() method, the background thread ends.
 */
public class HeartbeatReceiver extends AsyncTask<Void, Void, Void> {
    private static final String CLASS_NAME = "HeartbeatReceiver";
    private final String mServerIp;
    private final int mServerPort;

    /**
     * Create a new receiver.
     * The receiver has to be started with receiver.execute(..) and its variants,
     * see AsyncTask for details.
     *
     * @param serverIp IP address or hostname of the server that we connect to
     * @param serverPort TCP port of the server that we connect to
     */
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
                Log.d(CLASS_NAME, "Starting to receive heartbeat.");
                while (!isCancelled()) {

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
