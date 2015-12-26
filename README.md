# Libipho-screen-android: an Android frontend for the libipho project

This repository hosts an Android app that can be used as a front-end
for the photobooth project "libipho" (Linux-based individual photobooth).
It _can_ be used, since a front-ent based using web browser is possible as
well. The main advantage of the Android front-end is increased reponsiveness:
The browser-based solution has to continuously poll the server for updates
of the photobooth. By contrast, the Android app is notified from the
server of the photobooth about incoming pictures using push messages.

# Installation

The Android app has been written using the Android Studio IDE.
It can be compiled and deployed with the IDE, or, alternatively,
by using the gradle build system.

# License

The source code is released under the GPLv2.0 license.

