
# ARmor – Ride with Augmented Safety 🛡️

**Smart Helmet Project** integrating an ESP32 microcontroller, Android companion app, and sensors to enhance rider safety via real-time alerts, GPS tracking, SOS support, and voice commands.

---

## 🚀 Table of Contents
1. [About](#about)  
2. [Features](#features)  
3. [Tech Stack](#tech-stack)  
4. [System Architecture](#system-architecture)  
5. [Hardware Components](#hardware-components)  
6. [Setup & Installation](#setup--installation)  
7. [Usage](#usage)  
8. [Screenshots / Demo](#screenshots--demo)  
9. [Contributing](#contributing)  
10. [License](#license)  

---

## About  
ARmor (“Augmented Rider-Monitoring system”) is a smart helmet safety system built during Feb–May 2025 at Sambhram Institute of Tech. It includes:  
- **ESP32 microcontroller** with BLE, accelerometer, GPS, and SOS button  
- **Android app** to relay notifications and alerts  
- **Voice and data alerts** to notify sudden impacts, helmet removal, and SOS triggers  
- **Location tracking** and buzzer feedback for emergencies

---

## Features  
- Real-time **helmet status** detection (worn/removed)  
- **Accident detection** via MPU-6050 accelerometer thresholds  
- SOS alerts via hardware button press  
- **Voice notification support** on Android  
- **GPS location broadcast** via BLE for emergency tracking  
- **Buzzer and LED indicators** for immediate alert

---

## Tech Stack  
| Component           | Details |
|--------------------|---------|
| **Firmware**        | ESP32 (Arduino/C++) with MPU‑6050, GPS, BLE, OLED |
| **Mobile App**      | Android Studio (Java) with BLE connectivity |
| **Middleware**      | Firebase for notification relay |
| **Sensors & Outputs** | MPU‑6050, GPS, SOS button, buzzer, LEDs |
| **Communication**   | Bluetooth Low Energy, serial over USB |

---

## System Architecture  
```
[Helmet: ESP32 + MPU-6050 + GPS + buttons/LEDs] 
   ↕ BLE ↔
[Android App: alerts, voice, SOS relay]
   ↔ Firebase (cloud notifications, logging)
```

---

## Hardware Components  
- **ESP32** development board  
- MPU-6050 accelerometer + gyroscope  
- GPS receiver via SoftwareSerial  
- SOS button + removal detector  
- Buzzer, LEDs, and optionally OLED display  
- Android phone for BLE app and voice alerts

---

## 🛠️ Setup & Installation  

### 1. Clone the Repo  
```bash
git clone https://github.com/Darshan-Rajanna/ARmor
cd ARmor
```

### 2. Install & Configure Dependencies  
- Install Arduino IDE with ESP32 board support  
- Add libraries: `TinyGPSPlus`, `TridentTD_LineNotify`, `Wire`, etc.

### 3. Flash ESP32 Firmware  
- Open `main.ino` in Arduino IDE  
- Set WiFi SSID/PASSWORD and LINE_TOKEN  
- Upload to ESP32

### 4. Build Android App  
- Open the Android Studio project  
- Run on Android device  
- Ensure Bluetooth & GPS are enabled

---

## 📱 Usage  
1. Wear the helmet → ESP32 detects helmet on via pin sensor → sends BLE message  
2. If helmet removed → Android app notifies user  
3. Detect sudden impact → SOS LED/beeper triggers → Android app shows alert and sends GPS (via Firebase)  
4. Press SOS button → Android relays emergency with location

---

## Screenshots / Demo  
*Add annotated screenshots or demo video here.*

---

## Contributing  
Contributions are welcome!  
- Fork the repo and create a feature branch (`git checkout -b feature/xyz`)  
- Submit a pull request explaining your changes

---

## License  
This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

## Contact  
**Darshan R** – *Project Lead*  
- 📧 darshanrajanna07@gmail.com  
- 🔗 [LinkedIn](https://www.linkedin.com/in/darshan-rajanna-07-/)  
- 🔗 [GitHub](https://github.com/Darshan-Rajanna) 


**M Kusuma** – *Project Team*  
 - 📧 kusumamurugesh2003@gmail.com
 - 🔗 [LinkedIn](https://www.linkedin.com/in/kusuma-techdev/)  
 - 🔗 [GitHub](https://github.com/Kuusuma)


**Deeksha M** – *Project Team*  
 - 📧 deekshamanjunath24@gmail.com
 - 🔗 [LinkedIn](https://www.linkedin.com/in/deeksha-manjunath-805348230/)  
 - 🔗 [GitHub](https://github.com/deeksha-manjunath2)
