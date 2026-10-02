# Meridian — Pothole Detection & Road Reporting

**[▶ Watch the app demo](https://drive.google.com/file/d/1x2Y0gwZcN761sr3JPvkWLX5YCDDKosAw/view?usp=drive_link)**

Meridian is an Android application that combines sensor-based pothole detection with an interactive map of road conditions. Users can report potholes, view their location and severity, follow reports, and receive updates. Administrator accounts can manage pothole statuses and road closures directly from the map.

## Features

- **Automatic pothole reporting:** Process acceleration readings from a Bluetooth sensor device and report potential potholes when a detection threshold is exceeded.
- **Manual reporting:** Submit pothole reports directly through the app.
- **Interactive map:** Explore pothole locations and a heatmap weighted by severity using Google Maps.
- **Severity classification:** Classify detected potholes as Minor, Moderate, or Severe based on acceleration readings.
- **Report tracking:** Follow potholes and monitor their status.
- **Notifications:** Receive updates about followed potholes and road closures near a saved home location.
- **Administrator controls:** Update pothole statuses and create, edit, or delete road closures.
- **User accounts:** Register, sign in, manage account settings, and review submitted reports.
- **GPS selection:** Use either the connected hardware’s coordinates or the phone’s location for automatic reports.

## Technology Stack

| Component | Technologies |
| --- | --- |
| Android development | Java, Android SDK, AndroidX |
| User interface | XML layouts, Material Components, View Binding |
| Authentication | Firebase Authentication |
| Database | Cloud Firestore |
| Maps and location | Google Maps SDK, Maps Utils, Fused Location Provider |
| Road information | Google Roads and Places APIs |
| Hardware communication | Bluetooth Classic, RFCOMM |
| HTTP requests | OkHttp |
| Build system | Gradle with Kotlin DSL |

## How It Works

1. A Bluetooth sensor device streams acceleration and GPS readings to the Android app.
2. The app processes the readings to identify potential potholes and assign severity levels.
3. Reports are associated with GPS coordinates and stored in Cloud Firestore.
4. Users explore reports through map markers and a severity-weighted heatmap.
5. Users follow reports and receive updates as administrators change their status.

## Project Structure

The application code is located in `app/src/main/java/com/example/meridian/`.

| Package | Responsibility |
| --- | --- |
| `login/` and `registration/` | Authentication and account creation |
| `navigation/` | Home, tracked reports, notifications, and account screens |
| `map/` | Map visualization, report details, and road closure management |
| `realtime/` | Bluetooth communication, sensor processing, and automatic reporting |
| `firebase/` | Firestore data helpers |
| `notifications/` | Notification listeners and delivery |
| `items/` | Pothole data model |
| `settings/` | Account settings |
