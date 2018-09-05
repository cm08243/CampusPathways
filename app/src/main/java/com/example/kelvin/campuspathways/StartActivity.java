package com.example.kelvin.campuspathways;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class StartActivity extends AppCompatActivity {

    //UI Elements
    Button btDiscover, btDisplay, btNodes;
    Context thisContext;//Used when switching activities

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        thisContext = this;

        init();

        //Ask for required permissions
        getPermissions();

    }

    //Initialize UI elements and event listeners
    public void init(){
        btDisplay = findViewById(R.id.btShowMap);
        btDiscover = findViewById(R.id.btTrackPath);
        btNodes = findViewById(R.id.btNodesFromStart);

        btDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(thisContext, DisplayActivity.class);
                startActivity(intent);

            }
        });

        btNodes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(thisContext, NodeSelectionActivity.class);
                startActivity(intent);

            }
        });

        btDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //Check if given location access first; If not, tell user
                if (!getPermissions()) {
                    Toast.makeText(thisContext, "Error. Location access not granted", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(thisContext, DiscoverActivity.class);
                startActivity(intent);
            }
        });
    }

    //Asks User for runtime permission to access location
    //Required for discovery
    public boolean getPermissions() {

        //Check if permission granted
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            //If not already granted, prompt user for them
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION},
                    PackageManager.PERMISSION_GRANTED);

            return false;

        }

        //If permission already granted
        else {
            return true;
        }
    }

}
