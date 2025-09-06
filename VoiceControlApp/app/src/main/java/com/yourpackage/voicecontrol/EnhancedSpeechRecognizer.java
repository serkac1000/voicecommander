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

public class EnhancedSpeechRecognizer {
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
    
    // Enhanced command patterns with multiple variations
    private Map<String, CommandPattern> commandPatterns;
    
    public interface SpeechRecognitionListener {
        void onSpeechRecognized(String command);
        void onError(String error);
        void onStartListening();
        void onStopListening();
    }
    
    private static class CommandPattern {
        String command;
        List<AudioSignature> signatures;
        
        CommandPattern(String command) {
            this.command = command;
            this.signatures = new ArrayList<>();
        }
        
        void addSignature(double minDuration, double maxDuration, double minAmplitude, double maxAmplitude, int syllables) {
            signatures.add(new AudioSignature(minDuration, maxDuration, minAmplitude, maxAmplitude, syllables));
        }
    }
    
    private static class AudioSignature {
        double minDuration, maxDuration;
        double minAmplitude, maxAmplitude;
        int syllables;
        
        AudioSignature(double minDuration, double maxDuration, double minAmplitude, double maxAmplitude, int syllables) {
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.minAmplitude = minAmplitude;
            this.maxAmplitude = maxAmplitude;
            this.syllables = syllables;
        }
        
        boolean matches(double duration, double amplitude, int detectedSyllables) {
            return duration >= minDuration && duration <= maxDuration &&
                   amplitude >= minAmplitude && amplitude <= maxAmplitude &&
                   Math.abs(detectedSyllables - syllables) <= 1;
        }
    }
    
    public EnhancedSpeechRecognizer(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeCommandPatterns();
    }
    
    private void initializeCommandPatterns() {
        commandPatterns = new HashMap<>();
        
        // "turn on" - 2 syllables, medium duration
        CommandPattern turnOn = new CommandPattern("turn on");
        turnOn.addSignature(0.8, 1.5, 1500, 3500, 2);
        commandPatterns.put("turn_on", turnOn);
        
        // "turn off" - 2 syllables, medium duration
        CommandPattern turnOff = new CommandPattern("turn off");
        turnOff.addSignature(0.8, 1.5, 1500, 3500, 2);
        commandPatterns.put("turn_off", turnOff);
        
        // "forward" - 2 syllables, medium duration
        CommandPattern forward = new CommandPattern("forward");
        forward.addSignature(0.6, 1.2, 2000, 4000, 2);
        commandPatterns.put("forward", forward);
        
        // "backward" - 2 syllables, medium duration
        CommandPattern backward = new CommandPattern("backward");
        backward.addSignature(0.7, 1.3, 2000, 4000, 2);
        commandPatterns.put("backward", backward);
        
        // "left" - 1 syllable, short duration
        CommandPattern left = new CommandPattern("left");
        left.addSignature(0.3, 0.8, 1800, 3500, 1);
        commandPatterns.put("left", left);
        
        // "right" - 1 syllable, short duration
        CommandPattern right = new CommandPattern("right");
        right.addSignature(0.3, 0.8, 1800, 3500, 1);
        commandPatterns.put("right", right);
        
        // "stop" - 1 syllable, short duration, sharp
        CommandPattern stop = new CommandPattern("stop");
        stop.addSignature(0.2, 0.7, 2500, 5000, 1);
        commandPatterns.put("stop", stop);
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
                BUFFER_SIZE * 4
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
        double maxEnergy = 0;
        
        while (isRecording) {
            int bytesRead = audioRecord.read(buffer, 0, buffer.length);
            
            if (bytesRead > 0) {
                double energy = calculateEnergy(buffer, bytesRead);
                maxEnergy = Math.max(maxEnergy, energy);
                
                // Adaptive threshold based on background noise
                double threshold = Math.max(800, maxEnergy * 0.1);
                
                if (energy > threshold) {
                    speechDetected = true;
                    silenceStart = 0;
                    
                    for (int i = 0; i < bytesRead; i++) {
                        audioData.add(buffer[i]);
                    }
                } else if (speechDetected) {
                    if (silenceStart == 0) {
                        silenceStart = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - silenceStart > 800) {
                        // 800ms of silence after speech
                        processAudioData(audioData);
                        break;
                    }
                }
                
                // Timeout after 4 seconds
                if (System.currentTimeMillis() - startTime > 4000) {
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
        return Math.sqrt(sum / length);
    }
    
    private void processAudioData(List<Short> audioData) {
        if (audioData.size() < 1000) {
            notifyError("Audio too short");
            return;
        }
        
        // Calculate audio characteristics
        double duration = audioData.size() / (double) SAMPLE_RATE;
        double avgAmplitude = calculateAverageAmplitude(audioData);
        int syllables = detectSyllables(audioData);
        
        // Find best matching command
        String bestMatch = findBestMatch(duration, avgAmplitude, syllables);
        
        if (bestMatch != null) {
            if (listener != null) {
                mainHandler.post(() -> listener.onSpeechRecognized(bestMatch));
            }
        } else {
            notifyError("Command not recognized");
        }
    }
    
    private String findBestMatch(double duration, double amplitude, int syllables) {
        String bestMatch = null;
        double bestScore = 0;
        
        for (CommandPattern pattern : commandPatterns.values()) {
            for (AudioSignature signature : pattern.signatures) {
                if (signature.matches(duration, amplitude, syllables)) {
                    // Calculate confidence score
                    double durationScore = 1.0 - Math.abs(duration - (signature.minDuration + signature.maxDuration) / 2) / 2.0;
                    double amplitudeScore = 1.0 - Math.abs(amplitude - (signature.minAmplitude + signature.maxAmplitude) / 2) / 3000.0;
                    double syllableScore = 1.0 - Math.abs(syllables - signature.syllables) / 3.0;
                    
                    double score = (durationScore + amplitudeScore + syllableScore) / 3.0;
                    
                    if (score > bestScore && score > 0.6) {
                        bestScore = score;
                        bestMatch = pattern.command;
                    }
                }
            }
        }
        
        return bestMatch;
    }
    
    private double calculateAverageAmplitude(List<Short> audioData) {
        double sum = 0;
        for (Short sample : audioData) {
            sum += Math.abs(sample);
        }
        return sum / audioData.size();
    }
    
    private int detectSyllables(List<Short> audioData) {
        // Simple syllable detection based on energy peaks
        int syllables = 0;
        double[] energyWindow = new double[SAMPLE_RATE / 10]; // 100ms windows
        int windowIndex = 0;
        double windowSum = 0;
        boolean inSyllable = false;
        
        for (Short sample : audioData) {
            double energy = sample * sample;
            windowSum += energy;
            windowIndex++;
            
            if (windowIndex >= energyWindow.length) {
                double avgEnergy = windowSum / energyWindow.length;
                
                if (avgEnergy > 1000000 && !inSyllable) {
                    syllables++;
                    inSyllable = true;
                } else if (avgEnergy < 500000) {
                    inSyllable = false;
                }
                
                windowSum = 0;
                windowIndex = 0;
            }
        }
        
        return Math.max(1, syllables); // At least 1 syllable
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