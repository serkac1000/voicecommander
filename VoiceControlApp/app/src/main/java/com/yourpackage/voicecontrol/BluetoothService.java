package com.yourpackage.voicecontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private Context context;
    private Handler mainHandler;
    
    public BluetoothService(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void connect(String macAddress) {
        new Thread(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                
                mainHandler.post(() -> 
                    Toast.makeText(context, "Connected to " + device.getName(), 
                        Toast.LENGTH_SHORT).show());
                        
            } catch (IOException e) {
                mainHandler.post(() -> 
                    Toast.makeText(context, "Connection failed: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            }
        }).start();
    }
    
    public void sendData(String data) {
        if (outputStream != null) {
            new Thread(() -> {
                try {
                    outputStream.write((data + "\n").getBytes());
                    outputStream.flush();
                    
                    mainHandler.post(() -> 
                        Toast.makeText(context, "Sent: " + data, 
                            Toast.LENGTH_SHORT).show());
                            
                } catch (IOException e) {
                    mainHandler.post(() -> 
                        Toast.makeText(context, "Send failed: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show());
                    e.printStackTrace();
                }
            }).start();
        } else {
            Toast.makeText(context, "Not connected to device", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void connectAndSend(String macAddress, String data) {
        new Thread(() -> {
            try {
                if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    
                    bluetoothAdapter.cancelDiscovery();
                    bluetoothSocket.connect();
                    outputStream = bluetoothSocket.getOutputStream();
                }
                
                // Send data immediately after connection
                outputStream.write((data + "\n").getBytes());
                outputStream.flush();
                
                mainHandler.post(() -> 
                    Toast.makeText(context, "Command sent: " + data, 
                        Toast.LENGTH_SHORT).show());
                        
            } catch (IOException e) {
                mainHandler.post(() -> 
                    Toast.makeText(context, "Failed: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            }
        }).start();
    }
    
    public void disconnect() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public boolean isConnected() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }
}