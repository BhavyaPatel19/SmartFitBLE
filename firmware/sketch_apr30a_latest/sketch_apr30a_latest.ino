#include <Wire.h>
#include "MAX30105.h"
#include "spo2_algorithm.h"

#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Adafruit_MLX90614.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "esp_bt.h"

// ---------- Sensors ----------
MAX30105 particleSensor;
Adafruit_MLX90614 mlx = Adafruit_MLX90614();

// ---------- OLED ----------
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// ---------- BLE ----------
#define SERVICE_UUID        "12345678-1234-1234-1234-1234567890ab"
#define CHARACTERISTIC_UUID "abcd1234-5678-1234-5678-1234567890ab"

BLECharacteristic *pCharacteristic;
bool deviceConnected = false;

// ---------- BLE Callbacks ----------
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("BLE Client Connected");
  }
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("BLE Client Disconnected");
    delay(500);
    BLEDevice::startAdvertising();
  }
};

// ---------- MAX30102 Buffers ----------
#define BUFFER_SIZE 100
uint32_t irBuffer[BUFFER_SIZE];
uint32_t redBuffer[BUFFER_SIZE];

int32_t bufferLength;
int32_t spo2;
int8_t  validSPO2;
int32_t heartRate;
int8_t  validHeartRate;

// ---------- Averaging / Accumulation ----------
#define READING_DURATION_SEC  20
#define IR_THRESHOLD          50000
#define HR_MIN                70
#define HR_MAX                150
#define HR_CORRECTION         0.75

float hrSum   = 0;
int   hrCount = 0;

float spo2Sum   = 0;
int   spo2Count = 0;

float tempAccum = 0;
int   tempCount = 0;

// Final stable results
int32_t finalHR   = 0;
int32_t finalSpO2 = 0;
float   finalTemp = 0.0;
bool    hasResult = false;

// ---------- Temperature Filter ----------
float tempBuffer_arr[5];
int   tempIdx = 0;
float tempSum_filter = 0;
const float TEMP_OFFSET = 7.5;

// ---------- State Machine ----------
enum State {
  STATE_IDLE,
  STATE_PROCESSING,
  STATE_DISPLAY
};

State currentState = STATE_IDLE;
unsigned long processingStartTime = 0;

// ------------------------------------------------
// Helper: Check if finger is on sensor
// ------------------------------------------------
bool isFingerOn() {
  uint32_t irVal = particleSensor.getIR();
  return (irVal > IR_THRESHOLD);
}

// ------------------------------------------------
// Helper: Draw centered text
// ------------------------------------------------
void drawCentered(const char* text, int y, int textSize) {
  display.setTextSize(textSize);
  int16_t x1, y1;
  uint16_t w, h;
  display.getTextBounds(text, 0, 0, &x1, &y1, &w, &h);
  display.setCursor((SCREEN_WIDTH - w) / 2, y);
  display.print(text);
}

// ------------------------------------------------
// Display: Idle screen
// ------------------------------------------------
void showIdleScreen() {
  display.clearDisplay();
  display.setTextColor(WHITE);

  if (hasResult) {
    display.setTextSize(2);
    display.setCursor(0, 0);
    display.print("HR:");
    display.print(finalHR);

    display.setCursor(0, 22);
    display.print("SpO2:");
    display.print(finalSpO2);

    display.setTextSize(1);
    display.setCursor(0, 50);
    display.print("Temp:");
    display.print(finalTemp, 1);
    display.print("F");
  } else {
    drawCentered("SmartFit", 8, 2);
    drawCentered("Place finger", 35, 1);
    drawCentered("on sensor", 48, 1);
  }

  display.display();
}

// ------------------------------------------------
// Display: Processing screen with countdown
// ------------------------------------------------
void showProcessingScreen(int secondsRemaining) {
  display.clearDisplay();
  display.setTextColor(WHITE);

  drawCentered("Reading...", 0, 2);

  int elapsed = READING_DURATION_SEC - secondsRemaining;
  int barWidth = map(elapsed, 0, READING_DURATION_SEC, 0, 108);
  display.drawRect(10, 25, 108, 12, WHITE);
  display.fillRect(10, 25, barWidth, 12, WHITE);

  char timerStr[16];
  snprintf(timerStr, sizeof(timerStr), "%d sec left", secondsRemaining);
  drawCentered(timerStr, 45, 1);

  drawCentered("Keep finger steady", 56, 1);

  display.display();
}

// ------------------------------------------------
// Display: Final result screen
// ------------------------------------------------
void showResultScreen() {
  display.clearDisplay();
  display.setTextColor(WHITE);

  display.setTextSize(2);
  display.setCursor(0, 0);
  display.print("HR:");
  display.print(finalHR);

  display.setCursor(0, 22);
  display.print("SpO2:");
  display.print(finalSpO2);

  display.setTextSize(1);
  display.setCursor(0, 50);
  display.print("Temp:");
  display.print(finalTemp, 1);
  display.print("F");

  display.display();
}

// ------------------------------------------------
// Collect one batch of samples
// ------------------------------------------------
void collectOneBatch() {
  bufferLength = BUFFER_SIZE;

  for (byte i = 0; i < bufferLength; i++) {
    while (!particleSensor.available())
      particleSensor.check();

    redBuffer[i] = particleSensor.getRed();
    irBuffer[i]  = particleSensor.getIR();
    particleSensor.nextSample();
  }

  maxim_heart_rate_and_oxygen_saturation(
    irBuffer, bufferLength, redBuffer,
    &spo2, &validSPO2,
    &heartRate, &validHeartRate
  );
}

// ------------------------------------------------
// Read and filter temperature (returns Fahrenheit)
// ------------------------------------------------
float readFilteredTemp() {
  float rawTemp = mlx.readObjectTempC();

  tempSum_filter -= tempBuffer_arr[tempIdx];
  tempBuffer_arr[tempIdx] = rawTemp;
  tempSum_filter += rawTemp;
  tempIdx = (tempIdx + 1) % 5;

  float avgTemp = tempSum_filter / 5.0;
  float bodyTemp = avgTemp + TEMP_OFFSET;

  // Clamp in Celsius (36°C = 96.8°F, 39°C = 102.2°F)
  if (bodyTemp < 36.0) bodyTemp = 36.0;
  if (bodyTemp > 39.0) bodyTemp = 39.0;

  // Convert to Fahrenheit
  bodyTemp = (bodyTemp * 9.0 / 5.0) + 32.0;

  // Hard cap at 102F
  if (bodyTemp > 102.0) bodyTemp = 102.0;

  return bodyTemp;
}

// ------------------------------------------------
// SETUP
// ------------------------------------------------
void setup() {
  Serial.begin(115200);
  Serial.println("Booting SmartFit...");

  Wire.begin(21, 22);

  // OLED
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    Serial.println("OLED failed");
    while (1);
  }

  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(WHITE);
  display.setCursor(0, 0);
  display.println("SmartFit Sensor");
  display.println("Initializing...");
  display.display();

  // MAX30102
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("MAX30102 not found");
    while (1);
  }
  particleSensor.setup();
  Serial.println("MAX30102 Ready");

  // MLX90614
  if (!mlx.begin()) {
    Serial.println("MLX90614 not detected");
  } else {
    Serial.println("MLX90614 Ready");
  }

  // Init temperature buffer
  for (int i = 0; i < 5; i++) tempBuffer_arr[i] = 0;

  // ---------- BLE Setup ----------
  BLEDevice::init("SmartFit");
  esp_bt_controller_mem_release(ESP_BT_MODE_CLASSIC_BT);
  BLEDevice::setMTU(185);

  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE_NR
                    );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.println("BLE Ready");

  currentState = STATE_IDLE;
  showIdleScreen();
}

// ------------------------------------------------
// LOOP
// ------------------------------------------------
void loop() {

  switch (currentState) {

    // =============================================
    // IDLE: Wait for finger, show last result
    // =============================================
    case STATE_IDLE: {
      particleSensor.check();

      if (isFingerOn()) {
        delay(500);
        particleSensor.check();
        if (!isFingerOn()) break;

        Serial.println("Finger detected — starting measurement");

        hrSum      = 0;   hrCount   = 0;
        spo2Sum    = 0;   spo2Count = 0;
        tempAccum  = 0;   tempCount = 0;

        processingStartTime = millis();
        currentState = STATE_PROCESSING;

        String bleMsg = "HR:0,SpO2:0,Temp:0.0";
        pCharacteristic->setValue(bleMsg.c_str());
        pCharacteristic->notify();
      } else {
        showIdleScreen();
        delay(1000);
      }
      break;
    }

    // =============================================
    // PROCESSING: Collect data for 20 seconds
    // =============================================
    case STATE_PROCESSING: {
      unsigned long elapsed = millis() - processingStartTime;
      int secondsElapsed    = elapsed / 1000;
      int secondsRemaining  = READING_DURATION_SEC - secondsElapsed;

      if (secondsRemaining < 0) secondsRemaining = 0;

      // Check finger still on
      particleSensor.check();
      if (!isFingerOn()) {
        Serial.println("Finger removed during processing — aborting");
        currentState = STATE_IDLE;
        showIdleScreen();
        break;
      }

      // Show countdown
      showProcessingScreen(secondsRemaining);

      // Collect one batch
      collectOneBatch();

      // Accumulate valid HR (70–150 only)
      if (validHeartRate && heartRate >= HR_MIN && heartRate <= HR_MAX) {
        hrSum += heartRate;
        hrCount++;
      }

      // Accumulate valid SpO2 (70–100 only)
      if (validSPO2 && spo2 >= 70 && spo2 <= 100) {
        spo2Sum += spo2;
        spo2Count++;
      }

      // Accumulate temperature (already in Fahrenheit)
      float currentTemp = readFilteredTemp();
      tempAccum += currentTemp;
      tempCount++;

      // Serial debug
      Serial.print("Batch HR:");
      Serial.print(heartRate);
      Serial.print(" SpO2:");
      Serial.print(spo2);
      Serial.print(" Temp:");
      Serial.print(currentTemp, 1);
      Serial.print("F");
      Serial.print(" | Elapsed:");
      Serial.print(secondsElapsed);
      Serial.println("s");

      // Send processing status via BLE
      String bleMsg = "HR:0,SpO2:0,Temp:0.0";
      pCharacteristic->setValue(bleMsg.c_str());
      pCharacteristic->notify();

      // Check if 20 seconds done
      if (elapsed >= (unsigned long)READING_DURATION_SEC * 1000) {

        if (hrCount > 0) {
          finalHR = (int32_t)((hrSum / hrCount) * HR_CORRECTION);
        } else {
          finalHR = 0;
        }

        if (spo2Count > 0) {
          finalSpO2 = (int32_t)(spo2Sum / spo2Count);
        } else {
          finalSpO2 = 0;
        }

        if (tempCount > 0) {
          finalTemp = tempAccum / tempCount;
        } else {
          finalTemp = 0.0;
        }

        // Clamp final values
        if (finalHR < HR_MIN && finalHR != 0) finalHR = HR_MIN;
        if (finalHR > HR_MAX) finalHR = HR_MAX;
        if (finalSpO2 < 70 && finalSpO2 != 0) finalSpO2 = 70;
        if (finalSpO2 > 100) finalSpO2 = 100;
        if (finalTemp > 102.0) finalTemp = 102.0;  // final hard cap

        hasResult = true;

        Serial.println("=== Measurement Complete ===");
        Serial.print("Final HR: ");   Serial.println(finalHR);
        Serial.print("Final SpO2: "); Serial.println(finalSpO2);
        Serial.print("Final Temp: "); Serial.print(finalTemp, 1); Serial.println("F");

        // Send final values via BLE
        String finalBLE =
          "HR:" + String(finalHR) +
          ",SpO2:" + String(finalSpO2) +
          ",Temp:" + String(finalTemp, 1);

        pCharacteristic->setValue(finalBLE.c_str());
        pCharacteristic->notify();

        showResultScreen();
        currentState = STATE_DISPLAY;
      }
      break;
    }

    // =============================================
    // DISPLAY: Show result until finger re-placed
    // =============================================
    case STATE_DISPLAY: {
      particleSensor.check();
      bool fingerNow = isFingerOn();

      static bool waitingForFingerOff = true;

      if (waitingForFingerOff) {
        if (!fingerNow) {
          waitingForFingerOff = false;
          Serial.println("Finger removed — ready for new reading");
        }
      } else {
        if (fingerNow) {
          delay(500);
          particleSensor.check();
          if (isFingerOn()) {
            Serial.println("Finger re-placed — starting new measurement");
            waitingForFingerOff = true;
            currentState = STATE_IDLE;
          }
        }
      }

      delay(200);
      break;
    }
  }
}