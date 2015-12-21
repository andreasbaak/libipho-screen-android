package andreasbaak.libiphoscreen;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Receive commands and image data using a TCP connection
 * and forward the received commands using the ImageReceivedListener interface.
 */
public class ImageReceiver extends AsyncTask<Void, Void, Void> {
    public static final String CLASS_NAME = "ImageReceiver";

    enum ImageCommand {
        INVALID,
        TAKEN,
        DATA
    }

    private final ImageReceivedListener mListener;
    private final String mServerIp;
    private final int mServerPort;

    public ImageReceiver(String serverIp, int serverPort, ImageReceivedListener listener) {
        mServerIp = serverIp;
        mServerPort = serverPort;
        mListener = listener;
    }

    @Override
    protected Void doInBackground(Void... params) {
        while (!isCancelled()) {
            try {
                InetAddress serverAddr = InetAddress.getByName(mServerIp);
                Socket socket = connectToServer(serverAddr);
                try {
                    DataInputStream is = new DataInputStream(socket.getInputStream());
                    while (!isCancelled()) {
                        ImageCommand command = receiveCommand(is);
                        if (command == null) {
                            break; // socket closed
                        } else if (command == ImageCommand.TAKEN) {
                            mListener.onImageTaken();
                        } else if (command == ImageCommand.DATA) {
                            byte[] imageBuf = receiveImage(is);
                            if (imageBuf == null) {
                                break;
                            }
                            mListener.onImageReceived(imageBuf);
                        } else {
                            Log.e(CLASS_NAME, "Received invalid command.");
                        }
                    }
                } catch (Exception e) {
                    Log.e(CLASS_NAME, "Receiver eError ", e);
                } finally {
                    socket.close();
                }
            } catch (Exception e) {
                Log.e(CLASS_NAME, "Connection error", e);
            }
        }
        Log.i(CLASS_NAME, "Finished operation on receiver " + this);
        return null;
    }

    @NonNull
    private Socket connectToServer(InetAddress serverAddr) throws IOException, InterruptedException {
        Log.d(CLASS_NAME, String.format("Connecting to %s", serverAddr.toString()));
        Socket socket = null;
        while (socket == null) {
            try {
                //create a socket to make the connection with the server
                socket = new Socket(serverAddr, mServerPort);
            } catch (ConnectException e) {
                Log.e(CLASS_NAME, String.format("Connection to server %s failed. Retrying...",
                        serverAddr.toString()));
                Thread.sleep(500);
            }
        }
        return socket;
    }

    private byte[] receiveImage(DataInputStream is) throws IOException {
        // Obtain the size of the next image in bytes
        Log.d(CLASS_NAME, String.format("Waiting for next image."));
        try {

            byte[] intBuf = new byte[4];
            // Avoid blocking I/O in order to listen to the isCancelled() flag
            while (is.available() < 4) {
                if (isCancelled()) {
                    return null;
                }
                try {
                    Thread.sleep(1l);
                } catch (InterruptedException e) {
                }
            }
            is.readFully(intBuf);
            int imageSize = 0;
            for (int i = 0; i < 4; ++i) {
                int interpretedByte = (((int) intBuf[intBuf.length - 1 - i]) & 0xff);
                imageSize *= 0xff;
                imageSize += interpretedByte;
            }

            Log.i(CLASS_NAME, String.format("Trying to receive an image buffer of size %d", imageSize));
            byte[] imageBuf = new byte[imageSize];
            while (is.available() == 0) {
                if (isCancelled()) {
                    return null;
                }
                try {
                    Thread.sleep(1l);
                } catch (InterruptedException e) {
                }
            }
            is.readFully(imageBuf);
            Log.i(CLASS_NAME, String.format("Received image buffer of size %d", imageBuf.length));
            return imageBuf;
        } catch (EOFException e) {
            Log.e(CLASS_NAME, "Server closed the socket." );
            return null;
        }
    }

    private ImageCommand receiveCommand(DataInputStream is) throws IOException {
        Log.d(CLASS_NAME, String.format("Waiting for next command."));
        try {
            while (is.available() < 1) {
                if (isCancelled()) {
                    return null;
                }
                try {
                    Thread.sleep(1l);
                } catch (InterruptedException e) {
                }
            }
            byte command = is.readByte();
            switch (command) {
                case 1:
                    Log.d(CLASS_NAME, String.format("An image has been taken!"));
                    return ImageCommand.TAKEN;
                case 2:
                    Log.d(CLASS_NAME, String.format("Image data will be transferred!"));
                    return ImageCommand.DATA;
                default:
                    return ImageCommand.INVALID;
            }
        } catch (EOFException e) {
            Log.e(CLASS_NAME, "Server closed the socket." );
            return null;
        }

    }
}
