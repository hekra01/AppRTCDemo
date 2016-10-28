# AppRTCDemo

Android Studio project for AppRTCDemo of WebRTC project. The revision number of this build is 13722.
* official: https://chromium.googlesource.com/external/webrtc/+/bd59c71ff8d7014aee8fb64fa965b01e3b59671b
* or https://github.com/hekra01/webrtc/tree/rev13722

For Facets:
1-See/reuse the launch.sh script to configure and launch the docker instance that will host the AppRTCDemo app for Facets
2-run the roomserver at https://github.com/hekra01/apprtc. 
3-modify android settings to grant the app permissions:
  * "android.permission.MODIFY_AUDIO_SETTINGS",
  * "android.permission.RECORD_AUDIO",
  * "android.permission.INTERNET"
4-When the app is launched, adjust its settings if needed (room server url, default codec, etc...)

