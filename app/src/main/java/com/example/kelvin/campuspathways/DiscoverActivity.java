package com.example.kelvin.campuspathways;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class DiscoverActivity extends AppCompatActivity implements SensorEventListener {

    /*
    References
    [1] http://www.codingforandroid.com/2011/01/using-orientation-sensors-simple.html
    [2] https://www.built.io/blog/applying-low-pass-filter-to-android-sensor-s-readings
    */

    Context thisContext;
    //UI Elements
    private TextView tvDiscoverStatus;
    private Button btPathControl, btDisplayPaths, btNodes;
    private Spinner dropdownFeet, dropdownInches;
    //Sensors
    private FusedLocationProviderClient fusedLocationProviderClient;//Gets starting location
    private SensorManager sensorManager;
    private Sensor stepSensor;
    //Gravity and rotation info; Used for calculating orientation
    private Sensor accelSensor, magnetSensor, gyroSensor;
    private float[] lastAccel = new float[3];
    private float[] lastMagnet = new float[3];
    private boolean accelSet = false, magnetSet = false;
    private float[] rotation = new float[9];
    private float[] orientation = new float[3];
    private float currentAngle = 0f;
    private int sensorChanged = 0;
    private GeomagneticField geomagneticField;
    //List of points in user path
    private ArrayList<TimedLocation> userPath;

    private int userHeightInches = 48;//User height; Used in step length calculation
    private boolean tracking = false;//Checks whether user is being tracked
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover);
        thisContext = this;

        androidId = getAndroidID();

        initObjects();

    }

    //Initialize elements
    public void initObjects() {

        //Path buffer
        userPath = new ArrayList<>();

        //UI Elements
        btPathControl = findViewById(R.id.btPathControl);
        btDisplayPaths = findViewById(R.id.btDisplayPathFromDiscover);
        btNodes = findViewById(R.id.btNodesFromDiscover);
        tvDiscoverStatus = findViewById(R.id.tvDiscoverStatus);
        dropdownFeet = findViewById(R.id.dropdownDiscoverFeet);
        dropdownInches = findViewById(R.id.dropdownDiscoverInches);

        //Sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert sensorManager != null;//Assume phone has necessary sensors
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //Set up location client
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        //Give choices to dropdowns
        ArrayList<Integer> feetList = new ArrayList<>(4);
        ArrayList<Integer> inchesList = new ArrayList<>(12);

        feetList.add(4);
        feetList.add(5);
        feetList.add(6);
        feetList.add(7);

        inchesList.add(0);
        inchesList.add(1);
        inchesList.add(2);
        inchesList.add(3);
        inchesList.add(4);
        inchesList.add(5);
        inchesList.add(6);
        inchesList.add(7);
        inchesList.add(8);
        inchesList.add(9);
        inchesList.add(10);
        inchesList.add(11);

        ArrayAdapter<Integer> adapterFeet = new ArrayAdapter<>(thisContext,
                android.R.layout.simple_list_item_1, feetList);
        adapterFeet.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dropdownFeet.setAdapter(adapterFeet);


        ArrayAdapter<Integer> adapterInches = new ArrayAdapter<>(thisContext,
                android.R.layout.simple_list_item_1, inchesList);
        adapterInches.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dropdownInches.setAdapter(adapterInches);

        //Event listeners for dropdowns
        dropdownFeet.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                //Adjust global var with updated height
                userHeightInches = (12 * (int) adapterView.getItemIdAtPosition(i));
                userHeightInches += (int) dropdownInches.getSelectedItem();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        dropdownInches.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                //Adjust global var with updated height
                userHeightInches = (int) adapterView.getItemIdAtPosition(i);
                userHeightInches += 12 * (int) dropdownFeet.getSelectedItem();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //Start and stop tracking
        btPathControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //Not currently tracking; Start
                if (btPathControl.getText().toString().startsWith("Start")) {

                    if (ActivityCompat.checkSelfPermission(thisContext, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {

                        boolean perms = getPermissions();

                        if (!perms) {
                            Toast.makeText(thisContext, "Error. Location access not granted", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    fusedLocationProviderClient.getLastLocation().addOnSuccessListener(DiscoverActivity.this,
                            new OnSuccessListener<Location>() {

                                @Override
                                public void onSuccess(Location location) {
                                    if (location != null) {

                                        //Get starting location
                                        LatLng start = new LatLng(location.getLatitude(), location.getLongitude());

                                        //Add starting location to List
                                        userPath.add(new TimedLocation(start));

                                        //Get declination for finding true north
                                        geomagneticField = new GeomagneticField((float) location.getLatitude(),
                                                (float) location.getLatitude(), (float) location.getAltitude(),
                                                System.currentTimeMillis());

                                    } else {
                                        Toast.makeText(thisContext,
                                                "Error. Location not found. Make sure Location is enabled on your phone",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }

                            });

                    //Update button text and boolean
                    btPathControl.setText(R.string.stopPathDisc);
                    tracking = true;
                }

                //Currently tracking; Stop
                else{

                    //Serialize list of points
                    ArrayList<JSONObject> pathTemp = new ArrayList<>();

                    for (int i = 0; i < userPath.size(); i += 2) {
                        try {
                            JSONObject temp = new JSONObject();
                            temp.put("Latitude", userPath.get(i).getLocation().latitude);
                            temp.put("Longitude", userPath.get(i).getLocation().longitude);
                            temp.put("Time", userPath.get(i).getTimestamp());

                            pathTemp.add(temp);

                        } catch (JSONException e) {
                            //Exit on JSON error
                            e.printStackTrace();
                            Toast.makeText(thisContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    //Convert ArrayList into JSON Array
                    JSONArray pathJSON = new JSONArray(pathTemp);

                    //Build query
                    String st = "'" + pathJSON.toString() + "'";

                    //Query 1: Create or update User
                    double step_length = userHeightInches * 0.0254 * 0.413;//Step length, in meters
                    String query1 = "IF EXISTS(SELECT * FROM Users where Android_ID = '" + androidId
                            + "') \n"
                            + " UPDATE Users SET Step_Length = " + step_length
                            + " WHERE Android_ID = '" + androidId + "' \n"
                            + " ELSE INSERT INTO Users(Android_ID, Step_Length) VALUES('"
                            + androidId + "'," + step_length + ");";

                    //Query 2: Upload path
                    String query2 = "INSERT INTO Pathways(Android_ID, User_Path)" +
                            " VALUES ('" + androidId + "', " + st + ");";

                    //Send height to database
                    new DatabaseConnectionInsert(query1).execute();

                    //Don't save path if too few points
                    if (pathTemp.size() <= 3) {
                        String ss = "Height updated";
                        Toast.makeText(thisContext, ss, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    //Send path to database
                    new DatabaseConnectionInsert(query2).execute();

                    //Reset buffer
                    userPath.clear();

                    //Notify user
                    String ss = "Path sent to server";
                    Toast.makeText(thisContext, ss, Toast.LENGTH_SHORT).show();

                    //Update button text and boolean
                    btPathControl.setText(R.string.startPathDisc);
                    tracking = false;

                }

            }
        });

        //Change to pathway display
        btDisplayPaths.setOnClickListener(new View.OnClickListener() {
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

    }

    //Filters sensor data to improve accuracy
    //Based on code from [2]
    protected float[] filter(float[] in, float[] out) {

        final float ALPHA = (float) 0.25;//Filtering constant

        if (out == null) return in;

        for (int i = 0; i < in.length; i++) {
            out[i] = out[i] + (ALPHA * (in[i] - out[i]));
        }

        return out;

    }

    //Asks User for runtime permission to access location
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

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent event) {

        //First check if tracking mode is enabled; Return if not
        if (!tracking) return;

        //This will help up get direction the phone is pointing
        sensorChanged++;    //This will keep track of how many times the sensor has changed
        //Accel sensor
        if (event.sensor == accelSensor) {
            lastAccel = filter(event.values.clone(), lastAccel);
            accelSet = true;
        }

        //Magnet sensor
        else if (event.sensor == magnetSensor) {
            lastMagnet = filter(event.values.clone(), lastMagnet);
            magnetSet = true;
        }

        if (accelSet && magnetSet && geomagneticField != null) {
            SensorManager.getRotationMatrix(rotation, null, lastAccel, lastMagnet);
            SensorManager.getOrientation(rotation, orientation);
            float azimuthRadians = orientation[0];
            currentAngle = ((float) (Math.toDegrees(azimuthRadians) + 360) % 360) - geomagneticField.getDeclination();
        }

        //If event is a step
        if (event.sensor == stepSensor && userPath.size() >= 1) {

            //Calculate current step length, in meters
            double stepLength = userHeightInches * 0.0254 * 0.413;

            LatLng lastLocation = userPath.get(userPath.size() - 1).getLocation();

            //Calculate new LatLng
            LatLng currentPos = SphericalUtil.computeOffset(lastLocation, stepLength, currentAngle);

            //Show location; Debug only
            tvDiscoverStatus.setText("Lat: " + currentPos.latitude + ", Lng: " + currentPos.longitude);

            //Store step in path
            userPath.add(new TimedLocation(currentPos));

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //Do nothing
    }

    protected void onResume() {
        super.onResume();
        //Register sensor listeners
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);

        //Reset tracking boolean
        tracking = !btPathControl.getText().toString().startsWith("Start");

    }

    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this, stepSensor);
        sensorManager.unregisterListener(this, accelSensor);
        sensorManager.unregisterListener(this, magnetSensor);
        sensorManager.unregisterListener(this, gyroSensor);
    }

    //Returns Unique ID for each device
    private String getAndroidID() {

        return Settings.Secure.getString(thisContext.getContentResolver(), Settings.Secure.ANDROID_ID)
                + Build.SERIAL;
    }

}
