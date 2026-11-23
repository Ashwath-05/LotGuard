# LotGuard for MetaTrader 5

An Android prototype that warns commodities and forex traders when the lot size visible in MT5 exceeds their saved preset for the active demo account and instrument.

## Problem

After switching MT5 accounts, the volume field can still show the previous account’s lot size. For example, a trader might set a 1.00-lot preset for one account and a 0.10-lot preset for another. If the field still reads 1.00 after the switch, the entry is ten times the second account’s preset.

## Proposed solution

The trader chooses a preset for each combination of login, broker server and symbol. During a user-started session, LotGuard reads the shared MT5 window, identifies the active account and compares the visible lot size with its preset. An oversized entry triggers an Android notification. The trader controls the order.

## How it works

| Stage | Implementation |
| --- | --- |
| Capture | Android MediaProjection supplies frames from the window the user shares. A foreground service receives them through ImageReader. |
| Read | The bundled Google ML Kit Latin text recognizer returns text and word positions on the device. The chart parser looks for the volume between the Sell and Buy controls. The detector also recognises supported order-ticket layouts. |
| Identify | The detector reads the expanded active card on the Accounts screen or uses the side menu as a fallback. Two consistent reads confirm the account. A visible list of saved logins alone is ambiguous. |
| Compare | Saved presets live in SharedPreferences. After two consistent volume reads, Java BigDecimal compares the entered volume with the preset for the verified login, server and symbol. |
| Notify | A session notification reports verification status. A separate high-importance channel handles oversized entries and displays the multiple of the preset. |

## Try the demo

1. Install the debug build on an Android 14+ device that offers single-app screen sharing. Use MT5 in English with volume displayed in lots.
2. Save the demo login, exact server name, symbol (including any broker suffix) and preferred lot size. You can also tap **Try sample OCR check** to run recognition on generated screens.
3. Tap **Start MT5-only check**, allow notifications, then select **A single app** and **MetaTrader 5** in the Android picker. Cancel if the picker only offers full-screen sharing.
4. Switch accounts in MT5 and briefly leave the expanded active card visible. If LotGuard requests verification, show the side menu. Check the session notification for the result.
5. On an account with a 0.10-lot preset, display 1.00 lot on the chart or order ticket. Look for the **10× preset** warning. You can test this without submitting an order.
6. To remove presets in v0.4, tick their checkboxes and tap **Clear selected**. Clearing presets stops an active session. Start another session to use the remaining presets.

## Build

Install JDK 17 and Android SDK Platform 35. Point ANDROID_HOME or the sdk.dir entry in a local.properties file at the SDK. From the repository root on macOS or Linux, run:

```sh
./gradlew assembleDebug
```

On Windows, run `gradlew.bat assembleDebug`. The Gradle wrapper downloads Gradle 8.9 on first use. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run the detector checks

These checks use JDK 17 and run independently of Android Studio or a phone.

```sh
classes=$(mktemp -d)
src=app/src/main/java/dev/lotguard/app
javac -d "$classes" "$src/Detector.java" "$src/ChartVolume.java" "$src/AccountCard.java" checks/*.java
for check in DetectorCheck CardCheck LayoutCheck; do
  java -cp "$classes" "dev.lotguard.app.$check" || exit 1
done
```

The checks cover account switching, ambiguous reads, chart volume selection, preset comparison and duplicate warning suppression. The account-switch and warning workflow was also demonstrated with two demo accounts in Samsung Remote Test Lab. The v0.4 preset deletion UI still needs an on-device check.

## Scope and privacy

LotGuard processes frames in memory and stores presets on the device. It does not request a trading password or submit orders. Android reports when the captured app becomes hidden, at which point LotGuard ends the session. The user can also stop it manually.

Checks depend on readable account and volume fields. Broker-specific layouts, protected screens and Android notification settings can delay or hide a warning. A preset represents the volume the trader chose. It does not calculate percentage risk or establish that a trade is safe.
