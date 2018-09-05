package com.example.kelvin.campuspathways;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class DisplayActivity extends FragmentActivity implements OnMapReadyCallback {

    //UI Elements
    Button btDiscover, btNodes;
    Context thisContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        init();
        thisContext = this;

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        //Once map loads, plot all paths
        String query = "SELECT Users.Android_ID, Step_Length, Path_ID, User_Path" +
                " FROM Users FULL OUTER JOIN Pathways ON Users.Android_ID = Pathways.Android_ID;";

        new DatabaseConnectionSelect(query, googleMap).execute();

    }

    public void init(){
        btDiscover = findViewById(R.id.btDiscoverPathFromDisplay);
        btNodes = findViewById(R.id.btNodesFromDisplay);

        //Change to Discover Activity
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

        //Change to Nodes activity
        btNodes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Change to nodes screen
                Intent intent = new Intent(thisContext, NodeSelectionActivity.class);
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
