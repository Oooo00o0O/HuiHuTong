# Huihutong Gate

Native Android/Kotlin standalone gate QR app for the Huihutong flow.

## Build

This project intentionally avoids Flutter, Compose, AndroidX, OkHttp, and other app runtime dependencies. It only needs the Android Gradle Plugin during build. AGP 9.0 provides Kotlin Android support directly, so `org.jetbrains.kotlin.android` should not be applied separately.

Local SDK path is stored in `local.properties` and is intentionally not committed:

```properties
sdk.dir=D\:\\Programs\\Andriod\\Sdk
```

Use Android Studio's bundled JBR 21 as the Gradle JDK. In Android Studio this is under `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK`; choose `D:\Program Files\Android\Android Studio\jbr` or the bundled/embedded JDK option.

If Gradle has not cached the build plugins yet, open `HuihutongGate` in Android Studio and run Gradle Sync, or run:

```powershell
$env:JAVA_HOME='D:\Program Files\Android\Android Studio\jbr'; gradle assembleDebug
```

Expected build-time plugin:

- `com.android.application:9.0.0`

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
