# Render Deployment

This repo is ready for Render as a Docker web service.

## 1. Push to GitHub

The target repo is:

```text
https://github.com/Vasant-JS/FarmGardian.git
```

## 2. Create the Render Service

1. Open Render Dashboard.
2. Click **New +**.
3. Choose **Blueprint** if Render detects `render.yaml`, or choose **Web Service** manually.
4. Connect GitHub and select `Vasant-JS/FarmGardian`.
5. Use these settings if creating manually:
   - Runtime: `Docker`
   - Dockerfile Path: `./Dockerfile`
   - Docker Context: `.`
   - Branch: `main`
   - Instance Type: Free is fine for testing.
6. Add environment variable:
   - `FARM_GUARDIAN_SECRET`: a long private value, not `secret`.
7. Deploy.

Render sets `PORT` automatically for web services. The backend reads that variable and binds on `0.0.0.0`.

## 3. Get the WebSocket URL

After deploy, Render gives a URL like:

```text
https://farm-guardian-backend.onrender.com
```

The Android backend URL must be:

```text
wss://farm-guardian-backend.onrender.com/ws
```

## 4. Build Android Apps for Render

Build both apps with the Render WebSocket URL:

```powershell
.\gradlew.bat :android:controller:assembleRelease :android:node:assembleRelease -PbackendWsUrl=wss://farm-guardian-backend.onrender.com/ws
```

For local emulator testing, omit `-PbackendWsUrl` and run:

```powershell
.\gradlew.bat :backend:run
```

## 5. Farm Setup

1. Install Node app on the farm phone.
2. Pair the farm phone with the Bluetooth speaker in Android settings.
3. Open Node app once and allow notification/Bluetooth permissions.
4. Install Controller app on the home phone.
5. Open Controller app and test `Dog Bark`, `Tiger`, `Siren`, and `Stop`.
