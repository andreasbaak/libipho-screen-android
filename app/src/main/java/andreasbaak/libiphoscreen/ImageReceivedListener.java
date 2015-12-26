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
