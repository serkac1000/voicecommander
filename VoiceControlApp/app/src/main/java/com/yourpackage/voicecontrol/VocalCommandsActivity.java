package com.yourpackage.voicecontrol;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class VocalCommandsActivity extends AppCompatActivity {
    private ListView commandsList;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocal_commands);
        
        setupToolbar();
        initViews();
        setupCommandsList();
    }
    
    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Vocal commands configuration");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void initViews() {
        commandsList = findViewById(R.id.commands_list);
    }
    
    private void setupCommandsList() {
        CommandsAdapter adapter = new CommandsAdapter(this);
        commandsList.setAdapter(adapter);
        
        commandsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(VocalCommandsActivity.this, CommandConfigActivity.class);
                intent.putExtra("command_number", position + 1);
                startActivity(intent);
            }
        });
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}