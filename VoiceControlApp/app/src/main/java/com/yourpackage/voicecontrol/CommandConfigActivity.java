package com.yourpackage.voicecontrol;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CommandConfigActivity extends AppCompatActivity {
    private TextView commandLabel, dataLabel;
    private Button configureCommandBtn, configureDataBtn;
    private int commandNumber;
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_config);
        
        commandNumber = getIntent().getIntExtra("command_number", 1);
        prefs = getSharedPreferences("voice_commands", MODE_PRIVATE);
        
        setupToolbar();
        initViews();
        loadSavedData();
        setupButtons();
    }
    
    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Vocal command n°" + commandNumber);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void initViews() {
        commandLabel = findViewById(R.id.command_label);
        dataLabel = findViewById(R.id.data_label);
        configureCommandBtn = findViewById(R.id.configure_command_btn);
        configureDataBtn = findViewById(R.id.configure_data_btn);
    }
    
    private void loadSavedData() {
        String savedCommand = prefs.getString("command_" + commandNumber, "");
        String savedData = prefs.getString("data_" + commandNumber, "");
        
        if (!savedCommand.isEmpty()) {
            commandLabel.setText(savedCommand);
        }
        if (!savedData.isEmpty()) {
            dataLabel.setText(savedData);
        }
    }
    
    private void setupButtons() {
        configureCommandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputDialog("Vocal command", "Type the vocal command to pronounce", 
                    commandLabel.getText().toString(), new InputCallback() {
                    @Override
                    public void onInput(String input) {
                        commandLabel.setText(input);
                        prefs.edit().putString("command_" + commandNumber, input).apply();
                    }
                });
            }
        });
        
        configureDataBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputDialog("Data to send to arduino", "Set the corresponding data to send", 
                    dataLabel.getText().toString(), new InputCallback() {
                    @Override
                    public void onInput(String input) {
                        dataLabel.setText(input);
                        prefs.edit().putString("data_" + commandNumber, input).apply();
                    }
                });
            }
        });
    }
    
    private void showInputDialog(String title, String hint, String currentValue, InputCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        
        final EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(currentValue);
        builder.setView(input);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            String inputText = input.getText().toString().trim();
            if (!inputText.isEmpty()) {
                callback.onInput(inputText);
            }
        });
        
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    interface InputCallback {
        void onInput(String input);
    }
}