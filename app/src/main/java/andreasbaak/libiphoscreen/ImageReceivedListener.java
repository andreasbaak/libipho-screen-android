package andreasbaak.libiphoscreen;

public interface ImageReceivedListener {
    /**
     * This function is called as soon as an image has been taken with the camera.
     * The function indicates that an image will be transferred shortly after.
     */
    void onImageTaken();

    /**
     * This function is called as soon as the image data has been received.
     */
    void onImageReceived(byte[] imageBuffer);
}
