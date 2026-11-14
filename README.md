# FlashLoop

Passive flashcards + spaced repetition. Kotlin, Jetpack Compose, Room, DataStore. Offline only, no accounts, no analytics.

## Run it
1. Unzip, then in Android Studio: File > Open > select the `FlashLoop` folder.
2. Wait for Gradle sync (first sync downloads dependencies; if it asks to install Android SDK 34, accept).
   If Android Studio offers an "Android Gradle Plugin upgrade", you can decline it.
3. Plug in a real phone (USB debugging on) or start an emulator (API 26+), press Run.

Package: `com.flashloop.app`  |  minSdk 26 (Android 8.0)  |  target/compile SDK 34

## How it fits together
- `data/`       Room entities/DAOs/database, `Srs.kt` (the learning algorithm, all numbers in `SrsConfig`), `SettingsRepo` (DataStore), `Repository` (single place that records reviews).
- `scheduling/` `Scheduler` (when + what), `AlarmReceiver`, `BootReceiver`, `Notifier`.
- `overlay/`    `OverlayManager` (the floating card, plain Android views).
- `ui/`         Compose screens: Home, Study, My Cards, Edit, Settings, Review (opened from the notification).

## What Android can and cannot guarantee
- Alarms use `setAndAllowWhileIdle`: no special permission, works in Doze, but timing is approximate (can slip by minutes).
- The overlay needs the "Display over other apps" permission and may be blocked by some manufacturers, hidden on the lock screen, or lost if the OS kills the app. Then a notification is used instead.
- Some brands (Xiaomi, Oppo, Vivo, Huawei, Samsung) kill background apps. Use Settings > Open battery settings and exclude FlashLoop.
- Force-stopping the app from system settings cancels its alarms until you open it once.
- Back / Home / power / recents are never intercepted. Back on the overlay opens the snooze options; the overlay stays pending until you answer or snooze.

## Verified vs not verified
- Verified: the spaced-repetition algorithm (compiled and run outside Android; see `app/src/test/.../SrsTest.kt`).
- NOT verified: a full Gradle build, and everything on a device (overlay, alarms, reboot, Doze). Use the checklist in the chat to test.
