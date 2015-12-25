package andreasbaak.libiphoscreen;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

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

    private final ImageReceivedListener mImageListener;
    private final NetworkConnectionStatusListener mNetworkListener;
    private final String mServerIp;
    private final int mServerPort;

    public ImageReceiver(String serverIp, int serverPort,
                         ImageReceivedListener imageListener,
                         NetworkConnectionStatusListener networkListener) {
        mServerIp = serverIp;
        mServerPort = serverPort;
        mImageListener = imageListener;
        mNetworkListener = networkListener;

        mNetworkListener.onDisconnected();
    }

    @Override
    protected Void doInBackground(Void... params) {
        while (!isCancelled()) {
            SocketChannel channel = null;
            try {
                InetAddress serverAddr = InetAddress.getByName(mServerIp);
                channel = connectToServer(serverAddr);
                // connectToServer can return an unconnected server
                // if the thread is interrupted. Check for this here:
                if (isCancelled()) {
                    break;
                }
                mNetworkListener.onConnected();
                while (!isCancelled()) {
                    ImageCommand command = receiveCommand(channel);
                    if (command == null) {
                        throw new Exception("Socket was closed on receiving a command.");
                    } else if (command == ImageCommand.TAKEN) {
                        mImageListener.onImageTaken();
                    } else if (command == ImageCommand.DATA) {
                        byte[] imageBuf = receiveImage(channel);
                        if (imageBuf == null) {
                            throw new Exception("Socket was closed on receiving an image.");
                        }
                        mImageListener.onImageReceived(imageBuf);
                    } else {
                        Log.e(CLASS_NAME, "Received invalid command.");
                    }
                }
            } catch (Exception e) {
                Log.e(CLASS_NAME, "Connection error", e);
            } finally {
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                    }
                }
            }
            mNetworkListener.onDisconnected();
        }
        Log.i(CLASS_NAME, "Finished operation on receiver " + this);
        return null;
    }

    @NonNull
    private SocketChannel connectToServer(InetAddress serverAddr) throws InterruptedException {
        Log.d(CLASS_NAME, String.format("Connecting to %s", serverAddr.toString()));
        SocketChannel socketChannel = null;

        // This will block until a connection has been established or an IOException occurred.
        boolean connected = false;
        while (!connected && !isCancelled()) {
            try {
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(true);
                socketChannel.connect(new InetSocketAddress(serverAddr, mServerPort));
                connected = true;
            } catch (Exception e) {
                Log.e(CLASS_NAME, "Exception while trying to connect to server: " + e.getClass());
                Thread.sleep(500);
                if (socketChannel != null) {
                    try {
                        socketChannel.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }

        return socketChannel;
    }

    private byte[] receiveImage(SocketChannel channel) throws IOException {
        // Obtain the size of the next image in bytes
        Log.d(CLASS_NAME, String.format("Waiting for next image."));
        try {
            ByteBuffer imageSizeBuffer = ByteBuffer.allocate(4);
            int numBytesRead = channel.read(imageSizeBuffer);
            if (numBytesRead == -1) {
                Log.e(CLASS_NAME, "Socket was closed while reading the size of the next image." );
                return null;
            } else if (numBytesRead < 4) {
                Log.e(CLASS_NAME, "Could not read the size of the next image completely." );
                return null;
            }
            // numBytesRead == 4
            imageSizeBuffer.flip();
            byte intBuf[] = new byte[4];
            imageSizeBuffer.get(intBuf);

            int imageSize = 0;
            for (int i = 0; i < 4; ++i) {
                int interpretedByte = (((int) intBuf[intBuf.length - 1 - i]) & 0xff);
                imageSize *= 0xff;
                imageSize += interpretedByte;
            }

            ByteBuffer imageBuffer = ByteBuffer.allocate(imageSize);
            Log.i(CLASS_NAME, String.format("Trying to receive an image buffer of size %d", imageSize));
            numBytesRead = 0;
            while (numBytesRead < imageSize) {
                int nbytes = channel.read(imageBuffer);
                if (nbytes == -1) {
                    Log.e(CLASS_NAME, "Received EOF while reading image data.");
                    return null;
                }
                numBytesRead += nbytes;
            }
            imageBuffer.flip();
            byte[] imageBuf = new byte[imageSize];
            imageBuffer.get(imageBuf);
            Log.i(CLASS_NAME, String.format("Received image buffer of size %d", imageBuf.length));
            return imageBuf;
        } catch (EOFException e) {
            Log.e(CLASS_NAME, "Server closed the socket." );
            return null;
        }
    }

    private ImageCommand receiveCommand(SocketChannel channel) throws IOException {
        Log.d(CLASS_NAME, String.format("Waiting for next command."));
        ByteBuffer buffer = ByteBuffer.allocate(1);
        int numBytesRead = channel.read(buffer);
        if (numBytesRead == -1) {
            Log.e(CLASS_NAME, "Socket was closed while reading next command.");
            return null;
        } else if (numBytesRead == 0) {
            Log.e(CLASS_NAME, "Could not read next command.");
            return null;
        }
        // numBytesRead == 1

        buffer.flip();
        byte command = buffer.get();
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
    }
}
