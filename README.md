### Intro

This app is an demo of [CMU PocketSphinx](https://cmusphinx.github.io/wiki/tutorial/) for Android. It uses Liepa-1 [acoustic model](https://github.com/mondhs/poliepa-android/tree/master/models/src/main/assets/sync/lt-lt-ptm), simplified [grammar](https://github.com/mondhs/poliepa-android/blob/master/models/src/main/assets/sync/liepa_commands.gram), [dictionary](https://github.com/mondhs/poliepa-android/blob/master/models/src/main/assets/sync/liepa-lt-lt.dict).
pocketsphinx is included as arr file and precompiled as separate activity
Read more [PocketSphinx on Android](https://cmusphinx.github.io/wiki/tutorialandroid/)

How to build CMU PocketShpix library you should check:
* https://github.com/cmusphinx/sphinxbase
* https://github.com/cmusphinx/pocketsphinx 

### Demo app

You cand find this app in google play store:
https://play.google.com/store/apps/details?id=io.github.mondhs.poliepa

### Build instructions
Clean up code

```
gradle clean
```

Build dev version

```
gradle assembleDebug
```



Build Release

```
gradle assembleRelease
```

Find file ```app/build/outputs/apk/release/app-release.apk```



Extract raw files:
~/Android/Sdk/platform-tools/adb shell ls -1 /sdcard/Android/data/io.github.mondhs.poliepa/files/sync/audio
~/Android/Sdk/platform-tools/adb pull /sdcard/Android/data/io.github.mondhs.poliepa/files/sync/audio/*.raw /tmp/audio
aplay -f S16_LE -r 16000 000000003.raw
