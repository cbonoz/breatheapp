
Breathe Application
===================================
UCLA Research - Wireless Health Institute


Pre-requisites
--------------

- Android SDK v23
- Android Build Tools v23.0.2
- Android Support Repository
- Android Wearable Device
- Android Phone
- Android API level 19+ (20+ recommended)

Screenshots
-------------

TBD

Getting Started
---------------

This app uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Notes
---------------

Registration process: Initial launch of app will prompt clinician to enter passcode and subject id (already provisioned by their accessing of the breatheplatform website). This subject ID will be saved on the phone

Each launch, watch will retrieve subject id from the paired phone. API calls are routed through the mobile device.


Wearable should be paired to the desired phone, spirometer, and dust sensor before use.

