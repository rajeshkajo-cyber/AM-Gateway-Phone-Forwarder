# AM SMS Forwarder (Android)
Forwards **payment-received SMS** (BharatPe / bank) to your AM Gateway. Only SMS that (a) come from the sender IDs you typed and (b) look like an incoming credit (not OTP / debit) ever leave the phone. Every request is signed with HMAC-SHA256; SMS are queued offline and retried.

## Get the APK without Android Studio (GitHub Actions — works from a phone browser)
1. Create a free GitHub repository (private is fine) and upload the **contents of this `android-app` folder** (so `gradlew`, `app/`, `.github/` are at the repo root).
2. Open the **Actions** tab -> *Build APK* -> **Run workflow**. After ~4 minutes download the artifact `am-sms-forwarder-apk` -> `am-sms-forwarder.apk`.
3. Install on the phone (allow "install unknown apps" for your browser/file manager).
4. Optional: upload the file to your gateway as `downloads/am-sms-forwarder.apk` so merchants get a download button.

## Build with Android Studio
Open this folder, let Gradle sync, *Build -> Build APK(s)*. Needs JDK 17, Android SDK 34.

## Setup on the phone (once)
1. Dashboard -> Payment providers -> **SMS Forwarder** -> enter UPI ID + sender IDs -> Save -> **Create pairing code**.
2. App: paste the code, type the same sender IDs -> **Save & pair**.
3. Tap **Allow SMS**, then **Battery: allow background** (on Xiaomi/Oppo/Vivo/Realme also enable *Autostart* in App settings and lock the app in Recents).
4. **Send test** -> "Connected to the gateway". Dashboard -> **Test connection** -> Switch on.

## Notes
* Gateway URL must be **https**. The pairing secret is stored inside the app's private storage (backups disabled).
* Heartbeat every ~15 min (and with every SMS). If the phone is silent for 45 min the dashboard stops offering SMS payments and notifies the merchant.
* The phone's date & time must be automatic (a clock more than 10 min off is rejected).
* The source was written without access to an Android SDK in the authoring environment: the protocol/signature part (`Protocol.kt`) is verified against the PHP side; if the first Gradle build shows an error, send it to the developer — it will be a one-line fix.
