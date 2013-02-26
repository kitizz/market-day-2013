// Tutorial at http://kitizz.blogger.com
//
// Extended from http://www.jayway.com/?p=9435
// Pulled from https://github.com/joekickass/Arduino-Uno-Android-Accessory


#include <Usb.h>
#include <adk.h>

// MOTORS
#define ENA 3
#define INA1 2
#define INA2 4

#define ENB 6
#define INB1 5
#define INB2 7

// USB Stuff
#define LED_PIN 13
#define LONG_BLINK 2000
#define SHORT_BLINK 500

#define HORIZONTAL 10
#define VERTICAL 12

void setupMotors() {
  pinMode(ENA, OUTPUT);
  pinMode(INA1, OUTPUT);
  pinMode(INA2, OUTPUT);
  
  pinMode(ENB, OUTPUT);
  pinMode(INB1, OUTPUT);
  pinMode(INB2, OUTPUT);
}

void setRightMotor(int power) {
  if (power < 0) {
    power = -power;
    digitalWrite(INA1, HIGH);
    digitalWrite(INA2, LOW);
  } else {
    digitalWrite(INA1, LOW);
    digitalWrite(INA2, HIGH);
  }
  
  if (power > 255) power = 255;
  analogWrite(ENA, power);
}

void setLeftMotor(int power) {
  if (power < 0) {
    power = -power;
    digitalWrite(INB1, HIGH);
    digitalWrite(INB2, LOW);
  } else {
    digitalWrite(INB1, LOW);
    digitalWrite(INB2, HIGH);
  }
  
  if (power > 255) power = 255;
  analogWrite(ENB, power);
}

int ledStatus = HIGH;

USB Usb;
ADK adk(&Usb,"Kitizz",
            "MarketDay2013",
            "Face Tracker Robot",
            "1.0",
            "http://kitizz.blogger.com",
            "0000000012345678");

void toggleLed() {
    digitalWrite(LED_PIN, ledStatus);
    ledStatus = (ledStatus == LOW) ? HIGH : LOW;
}

void blinkLed(int delayFactor) {
    toggleLed();
    delay(delayFactor);
    toggleLed();
    delay(delayFactor);
}

void errorBlink() {
    blinkLed(SHORT_BLINK);
}

#define DEADZONE 25
void readMessage() {
    uint8_t msg[2] = { 0x00 };
    uint16_t len = sizeof(msg);
    adk.RcvData(&len, msg);
    if(len > 0) {
        Serial.print("Read Message... ");
        Serial.println(msg[0]);
        if (msg[0] == 0xf) {
            toggleLed();
        } else if (msg[0] == HORIZONTAL) {
            // The horizontal center of faces is sent
            Serial.print("Hor: ");
            Serial.println(msg[1]-DEADZONE);
            Serial.println(msg[1]+DEADZONE);
            if (msg[1] - DEADZONE > 128) {
              setRightMotor(-100);
              setLeftMotor(100);
            } else if (msg[1] + DEADZONE < 128) {
              setRightMotor(100);
              setLeftMotor(-100);
            } else {
              setRightMotor(0);
              setLeftMotor(0);
            }
        }
    }
}

void setup() {
    Serial.begin(57600);
    Serial.println("Serial Started...");
    pinMode(LED_PIN, OUTPUT);
    if (Usb.Init() == -1) {
        while(1) {
            errorBlink();
        }
    }
    Serial.println("UsbInit Success...");
    
    setupMotors();
    Serial.println("Motors Setup...");
}

void loop() {
    Usb.Task();

    if (!adk.isReady()) {
        return;
    }
    
    readMessage();
}
