package com.yourpackage.voicecontrol;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomSpeechRecognizer {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private SpeechRecognitionListener listener;
    private Context context;
    private Handler mainHandler;
    
    // Simple pattern matching for voice commands
    private Map<String, List<String>> commandPatterns;
    
    public interface SpeechRecognitionListener {
        void onSpeechRecognized(String command);
        void onError(String error);
        void onStartListening();
        void onStopListening();
    }
    
    public CustomSpeechRecognizer(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeCommandPatterns();
    }
    
    private void initializeCommandPatterns() {
        commandPatterns = new HashMap<>();
        
        // Define simple patterns for each command
        commandPatterns.put("turn on", createPatterns("turn on", "on", "start", "enable"));
        commandPatterns.put("turn off", createPatterns("turn off", "off", "stop", "disable"));
        commandPatterns.put("forward", createPatterns("forward", "ahead", "go", "move"));
        commandPatterns.put("backward", createPatterns("backward", "back", "reverse"));
        commandPatterns.put("left", createPatterns("left", "turn left"));
        commandPatterns.put("right", createPatterns("right", "turn right"));
        commandPatterns.put("stop", createPatterns("stop", "halt", "pause"));
    }
    
    private List<String> createPatterns(String... patterns) {
        List<String> list = new ArrayList<>();
        for (String pattern : patterns) {
            list.add(pattern.toLowerCase());
        }
        return list;
    }
    
    public void setListener(SpeechRecognitionListener listener) {
        this.listener = listener;
    }
    
    public void startListening() {
        if (isRecording) {
            return;
        }
        
        try {
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 2
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                notifyError("AudioRecord initialization failed");
                return;
            }
            
            isRecording = true;
            audioRecord.startRecording();
            
            if (listener != null) {
                mainHandler.post(() -> listener.onStartListening());
            }
            
            recordingThread = new Thread(this::recordAudio);
            recordingThread.start();
            
        } catch (SecurityException e) {
            notifyError("Microphone permission denied");
        } catch (Exception e) {
            notifyError("Failed to start recording: " + e.getMessage());
        }
    }
    
    public void stopListening() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            audioRecord = null;
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (listener != null) {
            mainHandler.post(() -> listener.onStopListening());
        }
    }
    
    private void recordAudio() {
        short[] buffer = new short[BUFFER_SIZE];
        List<Short> audioData = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long silenceStart = 0;
        boolean speechDetected = false;
        
        while (isRecording) {
            int bytesRead = audioRecord.read(buffer, 0, buffer.length);
            
            if (bytesRead > 0) {
                // Simple voice activity detection
                double energy = calculateEnergy(buffer, bytesRead);
                
                if (energy > 1000) { // Threshold for speech detection
                    speechDetected = true;
                    silenceStart = 0;
                    
                    // Collect audio data
                    for (int i = 0; i < bytesRead; i++) {
                        audioData.add(buffer[i]);
                    }
                } else if (speechDetected) {
                    if (silenceStart == 0) {
                        silenceStart = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - silenceStart > 1000) {
                        // 1 second of silence after speech - process command
                        processAudioData(audioData);
                        break;
                    }
                }
                
                // Timeout after 5 seconds
                if (System.currentTimeMillis() - startTime > 5000) {
                    if (speechDetected && !audioData.isEmpty()) {
                        processAudioData(audioData);
                    } else {
                        notifyError("No speech detected");
                    }
                    break;
                }
            }
        }
        
        stopListening();
    }
    
    private double calculateEnergy(short[] buffer, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        return sum / length;
    }
    
    private void processAudioData(List<Short> audioData) {
        // Simple pattern matching based on audio characteristics
        // This is a basic implementation - in a real app you'd use more sophisticated algorithms
        
        String recognizedCommand = performSimplePatternMatching(audioData);
        
        if (recognizedCommand != null) {
            if (listener != null) {
                mainHandler.post(() -> listener.onSpeechRecognized(recognizedCommand));
            }
        } else {
            notifyError("Command not recognized");
        }
    }
    
    private String performSimplePatternMatching(List<Short> audioData) {
        // This is a very basic implementation
        // In practice, you'd use more sophisticated audio processing
        
        if (audioData.size() < 1000) {
            return null; // Too short
        }
        
        // Calculate basic audio characteristics
        double avgAmplitude = calculateAverageAmplitude(audioData);
        double duration = audioData.size() / (double) SAMPLE_RATE;
        
        // Simple heuristic matching based on duration and amplitude
        if (duration < 0.5) {
            // Short commands
            if (avgAmplitude > 2000) {
                return "stop";
            } else {
                return "on";
            }
        } else if (duration < 1.0) {
            // Medium commands
            if (avgAmplitude > 2500) {
                return "forward";
            } else if (avgAmplitude > 2000) {
                return "left";
            } else {
                return "right";
            }
        } else {
            // Longer commands
            if (avgAmplitude > 2000) {
                return "turn on";
            } else {
                return "turn off";
            }
        }
    }
    
    private double calculateAverageAmplitude(List<Short> audioData) {
        double sum = 0;
        for (Short sample : audioData) {
            sum += Math.abs(sample);
        }
        return sum / audioData.size();
    }
    
    private void notifyError(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onError(error));
        }
    }
    
    public boolean isListening() {
        return isRecording;
    }
    
    public void release() {
        stopListening();
    }
}