# Huihutong Gate

Native Android/Kotlin standalone gate QR app for the Huihutong flow.

## Build

This project intentionally avoids Flutter, Compose, AndroidX, OkHttp, and other app runtime dependencies. It only needs the Android Gradle Plugin and Kotlin Gradle Plugin during build.

Local SDK path is stored in `local.properties` and is intentionally not committed:

```properties
sdk.dir=D\:\\Programs\\Andriod\\Sdk
```

If Gradle has not cached the build plugins yet, open `HuihutongGate` in Android Studio and run Gradle Sync, or run:

```powershell
gradle assembleDebug
```

Expected build-time plugins:

- `com.android.application:9.0.0`
- `org.jetbrains.kotlin.android:2.2.20`

## Runtime Scope

Implemented endpoints:

- `/web-app/auth/certificateLogin`
- `/pms/welcome/make-code-info`
- `/pms/welcome/make-qrcode`
- `/pms/welcome/power-warning`

Not implemented:

- Real room balance query endpoints such as `/proxy/qy/sdcz/getRoomBalance`
- Preventing screen sleep
- Old school SSO account features

## Credential Input

The app accepts any of these in the settings dialog:

```text
openId=...&unionId=...
```

```json
{"openId":"...","unionId":"..."}
```

```text
openId-only-value
```

The current mini program traffic suggests `openId + unionId` is the safest input. OpenId-only is kept for compatibility with the older app behavior.
