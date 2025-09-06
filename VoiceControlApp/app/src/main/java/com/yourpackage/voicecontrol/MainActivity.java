package com.yourpackage.voicecontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
// Removed Google Speech Services imports
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements EnhancedSpeechRecognizer.SpeechRecognitionListener, BluetoothScanner.ScanResultListener {
    private static final int REQUEST_PERMISSIONS = 1;
    
    private Button micButton;
    private Button settingsButton;
    private Button scanButton;
    private Button connectButton;
    private Spinner deviceSpinner;
    private HashMap<String, String> commands = new HashMap<>();
    private BluetoothService bluetoothService;
    private SharedPreferences prefs;
    private EnhancedSpeechRecognizer speechRecognizer;
    private BluetoothScanner bluetoothScanner;
    private boolean isListening = false;
    private ArrayList<String> deviceList;
    private ArrayAdapter<String> deviceAdapter;
    private ArrayList<BluetoothDevice> availableDevices;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("voice_commands", MODE_PRIVATE);
        
        initViews();
        requestPermissions();
        loadConfiguredCommands();
        bluetoothService = new BluetoothService(this);
        speechRecognizer = new EnhancedSpeechRecognizer(this);
        speechRecognizer.setListener(this);
        bluetoothScanner = new BluetoothScanner(this);
        bluetoothScanner.setListener(this);
        
        deviceList = new ArrayList<>();
        availableDevices = new ArrayList<>();
        
        setupBluetoothDevices();
        setupMicButton();
        setupSettingsButton();
        setupBluetoothButtons();
    }
    
    private void initViews() {
        try {
            micButton = findViewById(R.id.mic_button);
            settingsButton = findViewById(R.id.settings_button);
            deviceSpinner = findViewById(R.id.device_spinner);
            scanButton = findViewById(R.id.scan_button);
            connectButton = findViewById(R.id.connect_button);
            
            // Debug logging
            if (micButton == null) {
                Toast.makeText(this, "Mic button not found!", Toast.LENGTH_SHORT).show();
            }
            if (settingsButton == null) {
                Toast.makeText(this, "Settings button not found!", Toast.LENGTH_SHORT).show();
            }
            if (deviceSpinner == null) {
                Toast.makeText(this, "Device spinner not found!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing views: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void requestPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        // Add new Bluetooth permissions for Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }
    
    private void loadConfiguredCommands() {
        commands.clear();
        
        // Load configured commands from SharedPreferences
        for (int i = 1; i <= 10; i++) {
            String command = prefs.getString("command_" + i, "");
            String data = prefs.getString("data_" + i, "");
            
            if (!command.isEmpty() && !data.isEmpty()) {
                commands.put(command.toLowerCase().trim(), data);
            }
        }
        
        // Add default commands if none configured
        if (commands.isEmpty()) {
            setupDefaultCommands();
        }
    }
    
    private void setupDefaultCommands() {
        commands.put("turn on", "LED_ON");
        commands.put("turn off", "LED_OFF");
        commands.put("forward", "MOVE_FORWARD");
        commands.put("backward", "MOVE_BACKWARD");
        commands.put("left", "TURN_LEFT");
        commands.put("right", "TURN_RIGHT");
        commands.put("stop", "STOP");
    }
    
    private void setupBluetoothDevices() {
        deviceList.clear();
        availableDevices.clear();
        
        deviceList.add("No devices found - Tap 'Scan ESP32'");
        
        deviceAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, deviceList);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        if (deviceSpinner != null) {
            deviceSpinner.setAdapter(deviceAdapter);
        }
        
        // Load paired ESP32 devices
        loadPairedESP32Devices();
    }
    
    private void loadPairedESP32Devices() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                return;
            }
            
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            boolean foundESP32 = false;
            
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String macAddress = device.getAddress();
                
                if (isESP32Device(deviceName, macAddress)) {
                    if (deviceName == null) deviceName = "ESP32 Device";
                    
                    if (deviceList.get(0).contains("No devices found")) {
                        deviceList.clear();
                        availableDevices.clear();
                    }
                    
                    deviceList.add(deviceName + " - " + macAddress + " (Paired)");
                    availableDevices.add(device);
                    foundESP32 = true;
                }
            }
            
            if (foundESP32) {
                deviceAdapter.notifyDataSetChanged();
            }
            
        } catch (SecurityException e) {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading paired devices: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean isESP32Device(String deviceName, String macAddress) {
        if (deviceName != null) {
            String nameLower = deviceName.toLowerCase();
            if (nameLower.contains("esp32") || nameLower.contains("esp-32") || 
                nameLower.contains("arduino") || nameLower.startsWith("esp")) {
                return true;
            }
        }
        
        if (macAddress != null) {
            String macUpper = macAddress.toUpperCase();
            String[] espressifPrefixes = {
                "24:0A:C4", "30:AE:A4", "84:CC:A8", "A4:CF:12",
                "B4:E6:2D", "C8:C9:A3", "DC:A6:32", "E8:31:CD",
                "EC:62:60", "F0:08:D1", "24:6F:28", "3C:61:05"
            };
            
            for (String prefix : espressifPrefixes) {
                if (macUpper.startsWith(prefix)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void setupMicButton() {
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isListening) {
                    startVoiceRecognition();
                } else {
                    stopVoiceRecognition();
                }
            }
        });
    }
    
    private void setupSettingsButton() {
        if (settingsButton != null) {
            settingsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(MainActivity.this, VocalCommandsActivity.class);
                        startActivity(intent);
                        Toast.makeText(MainActivity.this, "Opening settings...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Error opening settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                }
            });
        } else {
            Toast.makeText(this, "Settings button not found!", Toast.LENGTH_LONG).show();
        }
    }
    
    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.startListening();
        } else {
            Toast.makeText(this, "Speech recognizer not initialized", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopVoiceRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }
    
    // Speech Recognition Listener Implementation
    @Override
    public void onSpeechRecognized(String command) {
        String dataToSend = commands.getOrDefault(command.toLowerCase().trim(), null);
        
        if (dataToSend != null) {
            sendCommandToDevice(dataToSend);
            Toast.makeText(this, "Command: " + command + " -> " + dataToSend, 
                Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Command not recognized: " + command, 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onError(String error) {
        Toast.makeText(this, "Speech error: " + error, Toast.LENGTH_SHORT).show();
        isListening = false;
        updateMicButtonState();
    }
    
    @Override
    public void onStartListening() {
        isListening = true;
        updateMicButtonState();
        Toast.makeText(this, "Listening... Speak now", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onStopListening() {
        isListening = false;
        updateMicButtonState();
    }
    
    private void updateMicButtonState() {
        if (micButton != null) {
            micButton.setText(isListening ? "🔴" : "🎤");
            micButton.setBackgroundColor(isListening ? 0xFFFF0000 : 0xFF4CAF50);
        }
    }
    
    private void sendCommandToDevice(String dataToSend) {
        String selectedDevice = (String) deviceSpinner.getSelectedItem();
        
        if (selectedDevice == null || selectedDevice.contains("No devices found")) {
            Toast.makeText(this, "No device selected. Please scan and connect to ESP32 first.", Toast.LENGTH_LONG).show();
            return;
        }
        
        if (selectedDevice.contains(" - ")) {
            String[] parts = selectedDevice.split(" - ");
            if (parts.length >= 2) {
                String macAddress = parts[1].replace(" (Paired)", "").replace(" (Found)", "");
                bluetoothService.connectAndSend(macAddress, dataToSend);
            }
        } else {
            // Fallback to first available ESP32 device
            if (!availableDevices.isEmpty()) {
                BluetoothDevice device = availableDevices.get(0);
                bluetoothService.connectAndSend(device.getAddress(), dataToSend);
            } else {
                Toast.makeText(this, "No ESP32 devices available", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload commands when returning from configuration
        loadConfiguredCommands();
    }
    
    private void setupBluetoothButtons() {
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> startBluetoothScan());
        }
        
        if (connectButton != null) {
            connectButton.setOnClickListener(v -> connectToSelectedDevice());
        }
    }
    
    private void startBluetoothScan() {
        if (bluetoothScanner.isScanning()) {
            bluetoothScanner.stopScan();
            scanButton.setText("Scan ESP32");
        } else {
            scanButton.setText("Scanning...");
            bluetoothScanner.startScan();
        }
    }
    
    private void connectToSelectedDevice() {
        String selectedDevice = (String) deviceSpinner.getSelectedItem();
        if (selectedDevice == null || selectedDevice.contains("No devices found")) {
            Toast.makeText(this, "Please select a device first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedDevice.contains(" - ")) {
            String[] parts = selectedDevice.split(" - ");
            if (parts.length >= 2) {
                String macAddress = parts[1].replace(" (Paired)", "").replace(" (Found)", "");
                bluetoothService.connect(macAddress);
                Toast.makeText(this, "Connecting to " + parts[0] + "...", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // BluetoothScanner.ScanResultListener Implementation
    @Override
    public void onDeviceFound(BluetoothDevice device, String deviceName, String macAddress) {
        runOnUiThread(() -> {
            if (deviceName == null) deviceName = "ESP32 Device";
            
            // Remove "No devices found" message if it exists
            if (deviceList.size() > 0 && deviceList.get(0).contains("No devices found")) {
                deviceList.clear();
                availableDevices.clear();
            }
            
            String deviceInfo = deviceName + " - " + macAddress + " (Found)";
            
            // Check if device already exists
            boolean exists = false;
            for (String existing : deviceList) {
                if (existing.contains(macAddress)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                deviceList.add(deviceInfo);
                availableDevices.add(device);
                deviceAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Found: " + deviceName, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onScanStarted() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Scanning for ESP32 devices...", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onScanFinished() {
        runOnUiThread(() -> {
            scanButton.setText("Scan ESP32");
            Toast.makeText(this, "Scan completed", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            scanButton.setText("Scan ESP32");
            Toast.makeText(this, "Scan error: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    public void connectDevice(View view) {
        connectToSelectedDevice();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                setupBluetoothDevices();
            } else {
                Toast.makeText(this, "Some permissions denied. App may not work properly.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
        if (speechRecognizer != null) {
            speechRecognizer.release();
        }
        if (bluetoothScanner != null) {
            bluetoothScanner.release();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
        }
    }
}