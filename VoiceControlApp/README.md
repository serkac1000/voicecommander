# Voice Control ESP32 Android App

A minimal Android app for controlling ESP32 devices via voice commands and Bluetooth.

## Features

- **Voice Recognition**: Push-to-talk microphone button for voice commands
- **Configurable Commands**: Set up to 10 custom voice commands with corresponding data
- **Bluetooth Communication**: Automatic connection and data transmission to paired ESP32 devices
- **Minimal Delay**: Optimized for fast voice recognition and Bluetooth transmission

## Setup Instructions

### 1. Import to Android Studio
1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to the `VoiceControlApp` folder and select it
4. Wait for Gradle sync to complete

### 2. Build and Install
1. Connect your Android device via USB
2. Enable Developer Options and USB Debugging on your device
3. In Android Studio, click "Run" or press Shift+F10
4. Select your device and click OK

### 3. Permissions
The app will request the following permissions:
- **Microphone**: For voice recognition
- **Bluetooth**: For ESP32 communication
- **Location**: Required for Bluetooth device discovery on Android

### 4. ESP32 Setup
Make sure your ESP32 is:
- Paired with your Android device via Bluetooth
- Running a sketch that accepts serial commands
- Using the standard Bluetooth Serial UUID: `00001101-0000-1000-8000-00805F9B34FB`

## Usage

### Basic Operation
1. **Main Screen**: Tap the microphone button to start voice recognition
2. **Speak Command**: Say one of your configured voice commands
3. **Automatic Transmission**: The app will automatically send the corresponding data to your ESP32

### Configuration
1. **Settings**: Tap the gear icon on the main screen
2. **Command List**: Select any of the 10 vocal command slots
3. **Configure Command**: Set the voice phrase you want to say
4. **Configure Data**: Set the data string to send to your ESP32

### Default Commands
If no commands are configured, these defaults are available:
- "turn on" → "LED_ON"
- "turn off" → "LED_OFF"
- "forward" → "MOVE_FORWARD"
- "backward" → "MOVE_BACKWARD"
- "left" → "TURN_LEFT"
- "right" → "TURN_RIGHT"
- "stop" → "STOP"

## ESP32 Example Code

```cpp
#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32-Device"); // Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
}

void loop() {
  if (SerialBT.available()) {
    String command = SerialBT.readString();
    command.trim();
    
    Serial.println("Received: " + command);
    
    // Handle commands
    if (command == "LED_ON") {
      // Turn on LED
      digitalWrite(LED_BUILTIN, HIGH);
    } else if (command == "LED_OFF") {
      // Turn off LED
      digitalWrite(LED_BUILTIN, LOW);
    } else if (command == "MOVE_FORWARD") {
      // Move robot forward
    }
    // Add more command handling as needed
  }
}
```

## Troubleshooting

### Voice Recognition Issues
- Ensure microphone permissions are granted
- Speak clearly and avoid background noise
- Check that Google Speech Services are installed and updated

### Bluetooth Connection Issues
- Verify ESP32 is paired in Android Bluetooth settings
- Ensure ESP32 is not connected to another device
- Try unpairing and re-pairing the ESP32

### Build Issues
- Make sure you have Android SDK 32 or higher installed
- Update Android Studio to the latest version
- Clean and rebuild the project (Build → Clean Project)

## Technical Details

- **Minimum Android Version**: API 21 (Android 5.0)
- **Target Android Version**: API 32 (Android 12)
- **Bluetooth Protocol**: Classic Bluetooth with SPP (Serial Port Profile)
- **Voice Recognition**: Android's built-in SpeechRecognizer
- **Data Storage**: SharedPreferences for command configuration

## License

This project is provided as-is for educational and development purposes.