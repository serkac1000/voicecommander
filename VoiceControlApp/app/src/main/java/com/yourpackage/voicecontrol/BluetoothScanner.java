package com.yourpackage.voicecontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothScanner {
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private ScanResultListener listener;
    private boolean isScanning = false;
    private Handler timeoutHandler;
    private List<BluetoothDevice> discoveredDevices;
    
    public interface ScanResultListener {
        void onDeviceFound(BluetoothDevice device, String deviceName, String macAddress);
        void onScanStarted();
        void onScanFinished();
        void onError(String error);
    }
    
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    try {
                        String deviceName = device.getName();
                        String macAddress = device.getAddress();
                        
                        // Check for ESP32 devices or specific patterns
                        if (isTargetDevice(deviceName, macAddress)) {
                            if (!discoveredDevices.contains(device)) {
                                discoveredDevices.add(device);
                                if (listener != null) {
                                    listener.onDeviceFound(device, deviceName, macAddress);
                                }
                            }
                        }
                    } catch (SecurityException e) {
                        if (listener != null) {
                            listener.onError("Permission denied: " + e.getMessage());
                        }
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                stopScan();
            }
        }
    };
    
    public BluetoothScanner(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.discoveredDevices = new ArrayList<>();
        this.timeoutHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setListener(ScanResultListener listener) {
        this.listener = listener;
    }
    
    public void startScan() {
        if (isScanning) {
            return;
        }
        
        if (bluetoothAdapter == null) {
            if (listener != null) {
                listener.onError("Bluetooth not supported");
            }
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            if (listener != null) {
                listener.onError("Bluetooth is disabled");
            }
            return;
        }
        
        // Check permissions
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onError("Missing Bluetooth or Location permissions");
            }
            return;
        }
        
        discoveredDevices.clear();
        
        // First, check paired devices
        scanPairedDevices();
        
        // Register receiver for discovery
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);
        
        // Start discovery
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            
            boolean started = bluetoothAdapter.startDiscovery();
            if (started) {
                isScanning = true;
                if (listener != null) {
                    listener.onScanStarted();
                }
                
                // Set timeout for scan (30 seconds)
                timeoutHandler.postDelayed(() -> {
                    if (isScanning) {
                        stopScan();
                    }
                }, 30000);
            } else {
                if (listener != null) {
                    listener.onError("Failed to start Bluetooth discovery");
                }
            }
        } catch (SecurityException e) {
            if (listener != null) {
                listener.onError("Permission denied: " + e.getMessage());
            }
        }
    }
    
    private void scanPairedDevices() {
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String macAddress = device.getAddress();
                
                if (isTargetDevice(deviceName, macAddress)) {
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        if (listener != null) {
                            listener.onDeviceFound(device, deviceName, macAddress);
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            if (listener != null) {
                listener.onError("Permission denied accessing paired devices: " + e.getMessage());
            }
        }
    }
    
    private boolean isTargetDevice(String deviceName, String macAddress) {
        if (deviceName == null) {
            deviceName = "";
        }
        
        // Check for ESP32 patterns
        String nameLower = deviceName.toLowerCase();
        
        // Common ESP32 device names
        if (nameLower.contains("esp32") || 
            nameLower.contains("esp-32") ||
            nameLower.contains("arduino") ||
            nameLower.contains("bluetooth") ||
            nameLower.contains("bt") ||
            nameLower.startsWith("esp") ||
            nameLower.contains("dev") ||
            nameLower.contains("module")) {
            return true;
        }
        
        // Check MAC address patterns for ESP32 (Espressif Systems)
        if (macAddress != null) {
            String macUpper = macAddress.toUpperCase();
            // Espressif MAC prefixes
            if (macUpper.startsWith("24:0A:C4") ||  // ESP32
                macUpper.startsWith("30:AE:A4") ||  // ESP32
                macUpper.startsWith("84:CC:A8") ||  // ESP32
                macUpper.startsWith("A4:CF:12") ||  // ESP32
                macUpper.startsWith("B4:E6:2D") ||  // ESP32
                macUpper.startsWith("C8:C9:A3") ||  // ESP32
                macUpper.startsWith("DC:A6:32") ||  // ESP32
                macUpper.startsWith("E8:31:CD") ||  // ESP32
                macUpper.startsWith("EC:62:60") ||  // ESP32
                macUpper.startsWith("F0:08:D1")) {  // ESP32
                return true;
            }
        }
        
        // If device name is empty but MAC suggests ESP32
        if (deviceName.isEmpty() && macAddress != null) {
            return isEspressifMAC(macAddress);
        }
        
        return false;
    }
    
    private boolean isEspressifMAC(String macAddress) {
        if (macAddress == null) return false;
        
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
        
        return false;
    }
    
    public void stopScan() {
        if (!isScanning) {
            return;
        }
        
        isScanning = false;
        timeoutHandler.removeCallbacksAndMessages(null);
        
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            
            context.unregisterReceiver(discoveryReceiver);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        
        if (listener != null) {
            listener.onScanFinished();
        }
    }
    
    public List<BluetoothDevice> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
    
    public boolean isScanning() {
        return isScanning;
    }
    
    public BluetoothDevice findDeviceByName(String targetName) {
        for (BluetoothDevice device : discoveredDevices) {
            try {
                String deviceName = device.getName();
                if (deviceName != null && deviceName.equalsIgnoreCase(targetName)) {
                    return device;
                }
            } catch (SecurityException e) {
                // Skip this device
            }
        }
        return null;
    }
    
    public BluetoothDevice findDeviceByMAC(String targetMAC) {
        for (BluetoothDevice device : discoveredDevices) {
            if (device.getAddress().equalsIgnoreCase(targetMAC)) {
                return device;
            }
        }
        return null;
    }
    
    public void release() {
        stopScan();
    }
}