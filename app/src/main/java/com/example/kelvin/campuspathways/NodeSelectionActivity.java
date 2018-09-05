package com.example.kelvin.campuspathways;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;

public class NodeSelectionActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Button btDisplay, btDiscover;
    private Context thisContext;

    private DatabaseConnectionCreateNodes databaseConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node_selection);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        thisContext = this;
        init();
    }

    //Initialize objects
    private void init() {
        btDiscover = findViewById(R.id.btDiscoverFromNodes);
        btDisplay = findViewById(R.id.btDisplayFromNodes);

        btDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(thisContext, DisplayActivity.class);
                startActivity(intent);
            }
        });

        btDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(thisContext, DiscoverActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        databaseConnection = new DatabaseConnectionCreateNodes(mMap);

        //Get nodes
        databaseConnection.execute();

        String help = "Tap a circle to see paths that connect to it";
        Toast.makeText(thisContext, help, Toast.LENGTH_SHORT).show();

        //Filter paths based on node interactions
        googleMap.setOnCircleClickListener(new GoogleMap.OnCircleClickListener() {
            @Override
            public void onCircleClick(Circle circle) {

                //Get center point of node, for method calls
                LatLng marker = circle.getCenter();

                //Message to help user
                String message = "Debug only";

                //Times a node has been clicked
                int clicks = databaseConnection.markersClicked;

                //If no prior clicks, show all paths that start or end at selected node
                if (clicks == 0) {
                    databaseConnection.plotPaths(marker);
                    databaseConnection.markersClicked = 1;
                    message = "Tap another circle to get connecting paths";
                }

                //Else if 1 prior click, show all paths that connect 2 selected nodes
                else if (clicks == 1) {
                    databaseConnection.plotPaths(marker);
                    databaseConnection.markersClicked = 2;
                    message = "Tap a circle to reset";
                }

                //Else, show all paths again
                else {
                    databaseConnection.resetPaths();
                    databaseConnection.markersClicked = 0;
                    message = "Tap a circle to see paths that connect to it";
                }

                //Help user
                Toast.makeText(thisContext, message, Toast.LENGTH_SHORT).show();

            }
        });

    }

    /*
    //Use marker clicks to filter paths
    @Override
    public boolean onMarkerClick(final Marker marker){

        int clicks = databaseConnection.markersClicked;

        //If no prior clicks, show all paths that start or end at selected node
        if(clicks == 0) {
            databaseConnection.plotPaths(marker);
            databaseConnection.markersClicked++;
        }

        //Else if 1 prior click, show all paths that connect 2 selected nodes
        else if(clicks == 1){
            databaseConnection.plotPaths(marker);
            databaseConnection.markersClicked++;
        }

        //Else, show all paths again
        else{
            databaseConnection.resetPaths();
        }

        return true;

    }
    */


}
