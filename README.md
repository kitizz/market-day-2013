market-day-2013
===============

This project contains the Android and Arduino code for the Face Tracking robot at UQ Market Day 2013.

The Android code makes use of the OpenCV libraries (http://opencv.org/platforms/android.html) and the USB Host Shield 2.0 library (https://github.com/felis/USB_Host_Shield_2.0). It is basically the OpenCV Face Detection sample mashed up with the USB Host Shield example at http://www.jayway.com/?p=9419/

The Arduino code extends the USB Host Shield example by adding a second part to the message sent from the Android to the Arduino.

To install on phone
================

Import the Android project into Eclipse workspace (or any other Android dev environment). Ensure the OpenCV and USB Host Shield 2.0 library is correctly linked and that the Android NDK (http://developer.android.com/tools/sdk/ndk/index.html) is installed and working.

Run the project on the phone like any other Android project. If the app requests to install the OpenCV Manager, accept it.

To install on arduino
================

Simply use the Arduino IDE to upload the sketch.
