# Farm Guardian V1

Farm Guardian is a two-phone Android system for remotely triggering deterrent sounds at a farm.

This repository starts the V1 implementation with:

- `backend`: Ktor WebSocket relay for controller and node devices.
- `android:shared`: shared Kotlin protocol models.
- `android:controller`: Android Controller app skeleton.
- `android:node`: Android Node app skeleton with foreground service entry point.

## Local Build

Open this folder in Android Studio, let Gradle sync, then build the desired module.

Backend entry point:

```powershell
./gradlew :backend:run
```

Android modules:

```powershell
./gradlew :android:controller:assembleDebug
./gradlew :android:node:assembleDebug
```

The default backend URL used by the Android clients is `ws://10.0.2.2:8080/ws`, which works from the Android emulator when the backend is running on the host machine.

For a deployed backend, build the apps with a secure WebSocket URL:

```powershell
./gradlew :android:controller:assembleRelease :android:node:assembleRelease -PbackendWsUrl=wss://your-domain.example/ws
```

The backend shared secret defaults to `secret` for local development. Override it in production:

```powershell
$env:FARM_GUARDIAN_SECRET = "replace-with-a-long-random-secret"
./gradlew :backend:run
```

## Render Hosting

Render-ready files are included:

- `Dockerfile`
- `render.yaml`
- `RENDER_DEPLOYMENT.md`

Follow [RENDER_DEPLOYMENT.md](RENDER_DEPLOYMENT.md) to create the Render service and rebuild Android apps with the final `wss://.../ws` URL.

Current Render backend:

```text
wss://farm-guardian-backend.onrender.com/ws
```

## V1 Coverage

- Controller connects over WebSocket, auto-reconnects, sends `PLAY` and `STOP`, controls volume percentage, and displays node health, playback, ACKs, and recent activity.
- Backend authenticates devices with a pre-shared secret, maintains node/controller WebSocket sessions, routes commands, remembers node status, answers heartbeats, and reports offline when the node disconnects.
- Node runs as a foreground service, starts on boot, auto-reconnects to the backend, sends 30-second heartbeats, reports battery/network/Bluetooth/playback health, logs local activity, and plays packaged local sounds with Media3.
- Playback follows V1 rules: stop any current sound, then play the requested sound; no queue.
- The Node packages V1 sound IDs under `android/node/src/main/res/raw/`. Replace those WAV files with real deterrent recordings using the same filenames when ready.

## Hardware Notes

Android does not provide a reliable public API to force-connect every Bluetooth speaker model. The Node detects Bluetooth connection state, reports it, and retries/report-checks every 20 seconds. Pair the farm phone with the speaker in Android system settings before leaving it at the farm.
