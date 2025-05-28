#include <OLED_I2C.h>
#include <Wire.h>
#include <BluetoothSerial.h>
#include <Ticker.h>
#include <MPU6050.h>

#define SOS_BUTTON_PIN 27
#define NAVIGATE_BUTTON_PIN 25
#define BUZZER_PIN 26

#define I2C_SDA_PIN 21
#define I2C_SCL_PIN 22
#define I2C_FREQ 100000
#define MPU6050_RETRIES 3

#define ACCIDENT_THRESHOLD 0.2
#define ACCELEROMETER_SCALE_FACTOR 16384.0

#define RED_PIN    14
#define GREEN_PIN  12
#define BLUE_PIN   13


OLED myOLED(I2C_SDA_PIN, I2C_SCL_PIN);
extern uint8_t SmallFont[], MediumNumbers[];

BluetoothSerial SerialBT;
Ticker secondTicker;

MPU6050 mpu;

int hours = 0, minutes = 0, seconds = 0;
int day = 4, month = 5, year = 2025;

bool timeRequested = false;
bool weatherRequested = false;
bool wasConnected = false;
unsigned long connectionMillis = 0;
unsigned long messageDisplayStart = 0;
unsigned long buzzerStartTime = 0;

String lastWeather = "";
String currentTimeStr = "";
String currentDateStr = "";
String incomingDisplayMessage = "";

const unsigned long MESSAGE_DISPLAY_DURATION = 15000;
bool sosTriggered = false;
bool buzzerActive = false;
bool navigationMode = false;
bool navigationCommandSent = false; // Flag to track if navigation command was sent

uint8_t bluetooth[] = {
  0x00, 0x00, 0x00, 0x00, 0x30, 0x60, 0xC0, 0xFE, 0x86, 0xCC, 0x78, 0x30, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x18, 0x0C, 0x06, 0xFF, 0xC3, 0x66, 0x3C, 0x18, 0x00, 0x00, 0x00, 0x00
};

// ========== Lights Setup ==========

void setRGBColor(int red, int green, int blue) {
  analogWrite(RED_PIN, red);     // Values 0-255
  analogWrite(GREEN_PIN, green);
  analogWrite(BLUE_PIN, blue);
}

// ========== MPU6050 Setup ==========
void scanI2CDevices() {
  Serial.println("Scanning I2C devices...");
  byte error, address;
  int nDevices = 0;

  for (address = 1; address < 127; address++) {
    Wire.beginTransmission(address);
    error = Wire.endTransmission();
    if (error == 0) {
      Serial.printf("Device found at 0x%02X\n", address);
      nDevices++;
    }
  }
  if (nDevices == 0) Serial.println("No I2C devices found");
}

void initializeMPU6050() {
  Serial.println("Initializing I2C bus...");
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  Wire.setClock(I2C_FREQ);
  delay(100);

  scanI2CDevices();
  Serial.println("Initializing MPU6050...");

  int retryCount = 0;
  bool mpuConnected = false;

  while (retryCount < MPU6050_RETRIES && !mpuConnected) {
    mpu.initialize();
    mpuConnected = mpu.testConnection();

    if (!mpuConnected) {
      retryCount++;
      Serial.printf("MPU6050 connection attempt %d failed\n", retryCount);
      delay(100);
      Wire.end();
      delay(50);
      Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
      Wire.setClock(I2C_FREQ);
    }
  }

  if (mpuConnected) {
    Serial.println("MPU6050 connected successfully");
    mpu.setDLPFMode(MPU6050_DLPF_BW_5);
    mpu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);
    mpu.setSleepEnabled(false);
  } else {
    Serial.println("MPU6050 connection failed after retries");
  }
}

void readMPU6050Data(int16_t *ax, int16_t *ay, int16_t *az) {
  static int errorCount = 0;
  const int maxErrors = 5;

  if (errorCount >= maxErrors) {
    initializeMPU6050();
    errorCount = 0;
    return;
  }

  if (mpu.testConnection()) {
    mpu.getAcceleration(ax, ay, az);
    if (*ax == 0 && *ay == 0 && *az == 0) {
      errorCount++;
      Serial.println("MPU6050 returned zero values");
    } else {
      errorCount = 0;
    }
  } else {
    *ax = *ay = *az = 0;
    errorCount++;
    Serial.println("MPU6050 not connected");
  }
}

void detectAccident(float az_g) {
  if (az_g < ACCIDENT_THRESHOLD && !buzzerActive) {
    Serial.println("Accident Detected!");
    showSOSAlert();
    SerialBT.println("sos");
    digitalWrite(BUZZER_PIN, HIGH);
    setRGBColor(255, 0, 0); // Bright red
    buzzerStartTime = millis();
    buzzerActive = true;
  }
}

// ======================= Display Time & Weather =========================
void updateTime() {
  seconds++;
  if (seconds >= 60) {
    seconds = 0; minutes++;
    if (minutes >= 60) {
      minutes = 0; hours++;
      if (hours >= 24) hours = 0;
    }
  }

  char timeStr[9], dateStr[11];
  sprintf(timeStr, "%02d:%02d:%02d", hours, minutes, seconds);
  sprintf(dateStr, "%02d/%02d/%04d", day, month, year);

  if (!buzzerActive && incomingDisplayMessage == "") {
    myOLED.clrScr();
    myOLED.setFont(MediumNumbers);
    myOLED.print(timeStr, CENTER, 0);
    myOLED.setFont(SmallFont);
    myOLED.print(dateStr, CENTER, 20);

    if (lastWeather != "") {
      int lineBreakIndex = lastWeather.indexOf('\n');
      if (lineBreakIndex != -1) {
        myOLED.print(lastWeather.substring(0, lineBreakIndex).c_str(), 1, 38);
        myOLED.print(lastWeather.substring(lineBreakIndex + 1).c_str(), 1, 48);
      }
    }

    if (SerialBT.hasClient())
      myOLED.drawBitmap(112, 48, bluetooth, 16, 16);

    myOLED.update();
  }
}

// =================== Parsers (Same) ====================
void parseBluetoothTime(String msg) {
  msg.trim();
  if (msg.startsWith("Time:") && msg.indexOf("Date:") != -1) {
    int tIndex = msg.indexOf("Time:") + 6;
    int dIndex = msg.indexOf("Date:") + 6;
    String timePart = msg.substring(tIndex, msg.indexOf(",", tIndex));
    String datePart = msg.substring(dIndex);

    if (timePart.length() != 8 || datePart.length() != 10) return;

    int h = timePart.substring(0, 2).toInt();
    int m = timePart.substring(3, 5).toInt();
    int s = timePart.substring(6, 8).toInt();
    int y = datePart.substring(0, 4).toInt();
    int mo = datePart.substring(5, 7).toInt();
    int d = datePart.substring(8, 10).toInt();

    if (h >= 0 && h < 24 && m >= 0 && m < 60 && s >= 0 && s < 60 &&
        d > 0 && d <= 31 && mo > 0 && mo <= 12 && y >= 2020) {
      hours = h; minutes = m; seconds = s;
      day = d; month = mo; year = y;
      currentTimeStr = timePart;
      currentDateStr = datePart;
      Serial.println("Time Received: " + timePart);
      Serial.println("Date Received: " + datePart);
    }
  }
}

void parseWeatherResponse(String msg) {
  msg.trim();
  if (msg.startsWith("Weather:") && msg.indexOf("°C") != -1 && msg.indexOf(",") != -1) {
    String data = msg.substring(msg.indexOf(":") + 2);
    int commaIndex = data.indexOf(",");
    if (commaIndex != -1) {
      String temp = data.substring(0, commaIndex);
      String condition = data.substring(commaIndex + 2);
      lastWeather = temp + "\n" + condition;
      Serial.println("Weather Received: " + lastWeather);
    }
  }
}

void showSOSAlert() {
  myOLED.clrScr();
  myOLED.setFont(MediumNumbers);
  myOLED.print("505", CENTER, 10);
  myOLED.setFont(SmallFont);
  myOLED.print("ACCIDENT DETECTED", CENTER, 35);
  myOLED.update();

  Serial.println("SOS\nACCIDENT DETECTED");
}

void showIncomingMessage(String message) {
  myOLED.clrScr();
  myOLED.setFont(SmallFont);
  myOLED.print("Message Received", CENTER, 0);
  int y = 20;
  while (message.length() > 0 && y < 64) {
    String line = message.substring(0, 20);
    message = message.length() > 20 ? message.substring(20) : "";
    myOLED.print(line.c_str(), 0, y);
    y += 10;
  }
  myOLED.update();
}

// ============ Setup ============
void timeSetup() {
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  myOLED.begin();
  myOLED.flipMode(true);
  myOLED.clrScr();
  myOLED.update();

  SerialBT.begin("ARmor");
  secondTicker.attach(1.0, updateTime);

  initializeMPU6050();
}

// ============ Main Loop ============
void timeLoop() {
  if (SerialBT.hasClient()) {
    if (!wasConnected) {
      wasConnected = true;
      timeRequested = weatherRequested = false;
      connectionMillis = millis();
      setRGBColor(0, 255, 0); // Green on connect
    }

    if (!timeRequested && millis() - connectionMillis >= 2000) {
      SerialBT.println("time");
      timeRequested = true;
    }
    if (!weatherRequested && millis() - connectionMillis >= 8000) {
      SerialBT.println("weather");
      weatherRequested = true;
    }
  } else {
    wasConnected = timeRequested = weatherRequested = false;
    setRGBColor(0, 0, 255); // Blue on disconnect
    navigationCommandSent = false; // Reset navigation command flag when disconnected
    navigationMode = false; // Reset navigation mode when disconnected
  }

  if (SerialBT.available()) {
    String btInput = SerialBT.readStringUntil('\n');
    btInput.trim();
    setRGBColor(255, 165, 0); // Orange for incoming message

    if (btInput.startsWith("Time:")) {
      parseBluetoothTime(btInput);
    } else if (btInput.startsWith("Weather:")) {
      parseWeatherResponse(btInput);
    } else {
      // Handle display messages based on navigation mode
      if (!navigationMode || (navigationMode && btInput.startsWith("NAV:"))) {
        incomingDisplayMessage = btInput;
        messageDisplayStart = millis();
        Serial.println("Incoming Message: " + incomingDisplayMessage);
        showIncomingMessage(incomingDisplayMessage);
      } else {
        Serial.println("Ignored non-NAV message in Navigation Mode: " + btInput);
      }
    }
  }

  if (incomingDisplayMessage != "" && millis() - messageDisplayStart > MESSAGE_DISPLAY_DURATION) {
    incomingDisplayMessage = "";
    updateTime();
    setRGBColor(0, 255, 0); // Back to green if still connected
  }

  // MPU6050 reading and accident check
  int16_t ax, ay, az;
  readMPU6050Data(&ax, &ay, &az);
  float az_g = az / ACCELEROMETER_SCALE_FACTOR;
  detectAccident(az_g);

  // Buzzer timeout
  if (buzzerActive && millis() - buzzerStartTime >= 2000) {
    digitalWrite(BUZZER_PIN, LOW);
    buzzerActive = false;
    setRGBColor(0, 0, 0); // Turn off RGB
  }
}
// ============ Arduino Entry ============
void setup() {
  Serial.begin(115200);
  pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
  pinMode(NAVIGATE_BUTTON_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RED_PIN, OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  pinMode(BLUE_PIN, OUTPUT);
  analogWrite(RED_PIN, 0);
  analogWrite(GREEN_PIN, 0);
  analogWrite(BLUE_PIN, 0);

  digitalWrite(BUZZER_PIN, LOW);
  timeSetup();
}


void loop() {
  timeLoop();

  // SOS Button Handling (Toggle Mode)
  static bool sosButtonPressed = false;

  if (digitalRead(SOS_BUTTON_PIN) == LOW) {
    if (!sosButtonPressed) {
      sosButtonPressed = true;

      // Toggle SOS mode
      if (sosTriggered) {
        // Disable SOS mode
        sosTriggered = false;
        Serial.println("SOS Cleared");
        setRGBColor(0, 255, 0); // Back to green (connected)
        digitalWrite(BUZZER_PIN, LOW); // Turn off buzzer
      } else {
        // Enable SOS mode
        sosTriggered = true;
        setRGBColor(128, 0, 128); // Purple for SOS manually triggered
        SerialBT.println("sos");
        Serial.println("SOS Triggered");
        showSOSAlert();
        digitalWrite(BUZZER_PIN, HIGH); // Turn on buzzer
      }
    }
  } else {
    if (sosButtonPressed) {
      // Reset SOS button press after handling
      sosButtonPressed = false;
    }
  }

  // Navigation Button Handling (Toggle Mode)
  static bool navigateButtonPressed = false;

  if (digitalRead(NAVIGATE_BUTTON_PIN) == LOW) {
    if (!navigateButtonPressed) {
      navigateButtonPressed = true;

      // Toggle navigation mode
      if (navigationMode) {
        // Disable navigation mode
        navigationMode = false;
        navigationCommandSent = false; // Allow sending navigate command again
        Serial.println("Navigation Mode OFF - Button Press");
        setRGBColor(0, 255, 0); // Back to green (connected)
      } else {
        // Enable navigation mode
        navigationMode = true;
        SerialBT.println("navigate");
        Serial.println("Navigate Command Sent - Navigation Mode ON");
        setRGBColor(0, 255, 255); // Cyan for navigate
      }
    }
  } else {
    if (navigateButtonPressed) {
      // Reset navigate button press after handling
      navigateButtonPressed = false;
    }
  }
}



