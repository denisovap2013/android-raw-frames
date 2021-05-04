# Android Raw Frames extractor

This demo app allows capturing raw frames from the camera preview stream.

I made it mostly for myself for the task where I had to process the captured frames without JPEG or MPEG compression artifacts 
(and when it cannot be done on the mobile device, for instance testing your experimental neural network model).

The major issues of the existing solutions are:

* Where are they?
* Some code examples provide only basic understanding of how the necessary frames can be acquired.

So, there you have it!

## How it works

1. I use Camera API (not Camera2 API, because the camera initialization alone would require more code that the current entire demo).
2. For capturing the frames data I use camera preview 
  * I tried MediaRecorder class, but even the demo from the official website did not work.
  * Even if it worked, MediaRecorder did not provide lots of methods to actually acquire the frames data. Whatever. 
3. Camera preview has a handy [Camera.PreviewCallback()](https://developer.android.com/reference/android/hardware/Camera#setPreviewCallback(android.hardware.Camera.PreviewCallback)) 
   (you can dig into the code if you really want) which gives me the uncompressed frames data.
4. The frames binary data is stored in the private storage space of your app (read the description below) 
   (Well, I tried to save frames into a public shared directory, but it was too much of a hassle.)

## Installation

1. Install Android Studio.
  * I used Android Studio 4.1.3 (Build #AI-201.8743.12.41.7199119, built on March 11, 2021).
  * You can use any other methods you want, but the description is for the Android Studio only.
2. Clone this repo, and open it using the Android Studio.
3. Connect your device, setup debug mode, the press the "Run" button at the top of the IDE.
   You can read more [here](https://developer.android.com/training/basics/firstapp/running-app)
4. Grant the app the permissions that the app requests.
5. Enjoy!

## Permissions

* `Manifest.permission.CAMERA` - well, without it the app surely won't work.
* `Manifest.permission.WRITE_EXTERNAL_STORAGE` and `Manifest.permission.READ_EXTERNAL_STORAGE` 
  - maybe the app can properly work without it, but my device sometime crushed without it (I wonder why).
  You can reject it, but if something is not working, try to allow them.

## Camera settings

By default the camera will be prepared for video preview, with video stabilization enabled and maximum available resolution 
(depends on the device, so I encourage you to check the settings).

I added the following settings, related to the preview mode:

* **Preview size (width x height)**: The dimensions of the frame. If this parameter changes, camera preview will restart in order to allow the camera parameters update.
* **Preview FPS range**: The desirable frame rate. (The actual frame rate will be limited by the capabilities of your device).
* **Focus mode**: Camera focus behaviour.
* **Zoom**: Camera zoom.
* **Recording hint**: Flag for telling the camera, that you want to record a video (Also the video stabilization wont work without that).
* **Video stabilization**: If supported, the video stabilization will be applied (only works when the recording hint is enabled). 
  It will also result in cropped edges of the frame (you can read more about stabilization [here](https://en.wikipedia.org/wiki/Image_stabilization)).
* **Preview format**: Format of storing frames data. You can use "yuv420sp" which corresponds to NV21 format.
  Sometimes you can encounter some strange formats like "nv12-venus" (I honestly don't have a clue, what it is), so it is up to you to find the algorithm for decoding the frames byte data.

## Manage frames data

### Data structure

The main directory, where the frames are saved is `/data/data/com.rawframesrecorder/files/raw-frames`.

> Its actual location is `/data/user/0/com.rawframesrecorder/files/raw-frames`, so if you have a rooted device, you can access files from here.
> The device emulator usually provides access to **/data/data** folder and not to **/data/user/0**.

For each frames sequence, the separate folder is created. The folder name consist of the current data and time.

Inside each folder there is always a `camera_settings.txt` file which includes camera parameters and a timestamp of the beginning of frames recording.

Each recorded frame has the name including the timestamp (in milliseconds) and an extension ".yuv" (I know that it is not always YUV data, so you can change the extensions to match the selected frames format).

### Reading data

In order to do anything with frames data, you need the [Device File Explorer](https://developer.android.com/studio/debug/device-file-explorer) which is the part of the Android Studio.

> If you have a rooted device, than you explore the `/data/user/0/com.rawframesrecorder/files/raw-frames` 
> directory directly using just an ADB utility from the [Android Platform tools](https://developer.android.com/studio/releases/platform-tools).

> The first alternative is to use the emulator in order to get access to the files. 
> In this case you will have access to the `/data/data/com.rawframesrecorder/files/raw-frames` directory.

> The second alternative is to create a backup of the application (you will have to set additional permissions in you device settings).

### Remove data
Removing the frames data is up to you. **Never forget to do that!**

Each FullHD frame will occupy about 3 MB. So if you capture 150 frames at a time, the whole sequence will take about 0.5 GB of you storage.

## Decoding frames data

If your frames are stored using the NV21 format, you can use the following [NV21 tool](https://github.com/denisovap2013/nv21), 
that I wrote using Python 

> Actually it is just a wrapper around the cv2 conversion. It just rearranges the NV21 data into YUV format and then converts it to RGB.