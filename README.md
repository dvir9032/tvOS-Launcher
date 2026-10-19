> [!WARNING]
> **Work in progress — not ready for everyday use.**
> Shelf is still being built and tested. Things may break or change between versions. Please don't install it yet unless you're helping test it.

# Shelf — a tvOS-inspired launcher for Android TV

Bring the look of the Apple TV home screen to your Android TV: the focused app's artwork fills the screen, its name sits big in the corner, and your apps live in a frosted-glass dock along the bottom, just like tvOS.

![Home screen](docs/screenshots/home.png)

![Moving focus changes the backdrop](docs/screenshots/focus.png)

*Screenshots from an Android TV 10 emulator. On your TV the dock shows your own apps.*

## Features

- Full-screen backdrop that takes the colors of the focused app and fades as you move
- Glass dock with your installed apps, using each app's official TV banner
- Focused tile lifts and catches a glare
- Clock in the top corner
- Home button jumps back to the first app; Back scrolls to the top
- Long-press OK (or the Menu button) on an app to open, move or hide it
- HDMI inputs row under the dock
- Settings: apps per row, 12/24-hour clock, glass strength, backdrop style, hidden apps
- Updates itself: an "update available" pill appears when a new version is out
- Picks up apps you install or remove automatically
- Works on Android TV 8.0 and newer, no account or internet needed

## Install

### Fastest: Downloader code

1. On your TV, install **Downloader** from the Google Play Store.
2. Open Downloader and enter the code:

   ## `8100721`

3. Allow Downloader to install unknown apps when asked
   (or: Settings → Device Preferences → Security & restrictions → Unknown sources → Downloader).
4. Tap **Install**.

### Direct link

Open this link in Downloader, or in any browser on the TV:

```
https://github.com/dvir9032/tvOS-Launcher/releases/latest/download/ShelfLauncher.apk
```

It always points to the newest version. You can also grab the APK from the [Releases](../../releases/latest) page.

### With ADB from a computer

```
adb connect <TV-IP>:5555
adb install -r ShelfLauncher.apk
```

## Make it your home screen

Press **Home** on the remote. If your TV asks which app to use, choose **Shelf** → **Always**.

### TVs that don't ask (Sony Bravia and many others): no computer needed

1. Open **Shelf** from your TV's app list.
2. Select the orange **הגדר כפתור בית** ("set up Home button") pill at the top.
3. In the list that opens, choose **Shelf – כפתור הבית** and turn it on.

From now on the Home button brings you to Shelf. The built-in launcher may flash for a split second first. The service only checks which app is in front; it doesn't read anything on screen.

### Or: switch off the built-in launcher with ADB

If you have a computer, you can instead turn off the built-in launcher once:

1. On the TV: Settings → Device Preferences → About → click **Build** 7 times to enable developer options.
2. Settings → Device Preferences → Developer options → turn on **USB debugging** (and **Network debugging** if listed).
3. From a computer on the same network:

   ```
   adb connect <TV-IP>:5555
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```

   The last line shows the current launcher, usually `com.google.android.tvlauncher/...`. Disable it (this does not delete it):

   ```
   adb shell pm disable-user --user 0 com.google.android.tvlauncher
   ```

**To get the original launcher back at any time:**

```
adb shell pm enable com.google.android.tvlauncher
```

Save this command somewhere before you disable anything.

## Updating

Install from the same code or link again. New versions install over the old one; your settings stay.

## Is it safe?

Every version is built automatically from the code in this repository by GitHub Actions, then installed and tested on an Android TV emulator. A version is only published if the test passes. You can see every build under [Actions](../../actions).

The launcher asks for no special permissions. It only reads the list of installed apps so it can show them.

## Build it yourself

Open the project in [Android Studio](https://developer.android.com/studio) and run **Build → Build APK(s)**. Without the signing secrets the APK is signed with your debug key.

## Notes

- Live background blur needs Android 12. On older TVs the backdrop is the app's banner scaled down and stretched, which gives a soft blur at almost no cost.
- Shelf is an independent project inspired by the design of tvOS. It is not affiliated with, endorsed by, or connected to Apple or Google. tvOS and Apple TV are trademarks of Apple Inc.

[עברית](README.he.md)
