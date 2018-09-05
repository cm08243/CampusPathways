package com.example.kelvin.campuspathways;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * Created by Kelvin on 2/25/2018.
 * Used to asynchronously select data from the database
 */

public class DatabaseConnectionSelect extends AsyncTask<String, Void, String> {

    private String query;//Query to be performed
    private GoogleMap googleMap;//Map to be used for drawing of paths

    private ArrayList<String> paths;//List of paths to be drawn
    private ArrayList<Integer> pathTimes;//List of time taken for each path, in milliseconds
    private ArrayList<Double> userHeights;//List of distances for each path
    private LatLng mapStart;//Sets camera start

    private Marker m1, m2;//Markers on start and end of selected path

    //Only constructor
    DatabaseConnectionSelect(String query, GoogleMap gMap) {
        this.query = query;
        this.googleMap = gMap;

        paths = new ArrayList<>();
        pathTimes = new ArrayList<>();
        userHeights = new ArrayList<>();
    }

    //Connect to database and perform query
    @Override
    protected String doInBackground(String... strings) {

        try {

            //Connection information
            String dns = "on-campus-navigation.caqb3uzoiuo3.us-east-1.rds.amazonaws.com";
            String aClass = "net.sourceforge.jtds.jdbc.Driver";
            Class.forName(aClass).newInstance();

            //Connect to database
            Connection dbConnection = DriverManager.getConnection("jdbc:jtds:sqlserver://" + dns +
                    "/Campus-Navigation;user=Android;password=password");

            //Execute query; In this case Selection
            Statement statement = dbConnection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);

            //Iterate through result and put in ArrayList
            while (resultSet.next()) {

                //4 Columns: Android_ID, Step_Length, Path_ID, User_Path
                String Android_ID = resultSet.getString("Android_ID");
                double step_length = resultSet.getDouble("Step_Length");
                int path_id = resultSet.getInt("Path_ID");
                String path = resultSet.getString("User_Path");

                if (path != null) {
                    paths.add(resultSet.getString("User_Path"));
                    userHeights.add(step_length);
                }

            }

            //Close connection to database
            dbConnection.close();

        } catch (Exception e) {
            Log.w("Error", "" + e.getMessage());
            return null;
        }

        return null;
    }

    @Override
    protected void onPostExecute(String result) {

        //Iterate through List of JSON arrays
        //Each JSON array is 1 pathway
        try {
            for (int i = 0; i < paths.size(); i++) {

                //Decode JSON array into polyline
                JSONArray pathJSON = new JSONArray(paths.get(i));

                ArrayList<LatLng> points = new ArrayList<>();
                //Get JSON array into list of points
                for (int j = 0; j < pathJSON.length(); j++) {
                    //Get data from JSON object
                    JSONObject point = pathJSON.getJSONObject(j);
                    double lat = point.getDouble("Latitude");
                    double lng = point.getDouble("Longitude");

                    //Make point from JSON data and add to list
                    points.add(new LatLng(lat, lng));
                }

                //Get time taken for path
                long startTime = pathJSON.getJSONObject(0).getLong("Time");
                long endTime = pathJSON.getJSONObject(pathJSON.length() - 1).getLong("Time");
                int timeTaken = (int) (endTime - startTime);
                pathTimes.add(timeTaken);

                //Make map start at position of 1st start point
                if (mapStart == null) mapStart = points.get(0);

                //Draw pathways and make clickable
                Polyline path = googleMap.addPolyline(new PolylineOptions().addAll(points).width(10).color(Color.RED));
                path.setClickable(true);

            }

            //Move map to 1st point of 1st path
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mapStart, 20.0f));

            //Make line interact with click
            googleMap.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
                @SuppressLint("DefaultLocale")
                @Override
                public void onPolylineClick(Polyline polyline) {

                    //Get start and end points of line
                    LatLng start = polyline.getPoints().get(0);
                    LatLng end = polyline.getPoints().get(polyline.getPoints().size() - 1);
                    String ss = polyline.getId().substring(2);//String of line index
                    int i = Integer.parseInt(ss);//Path index
                    double timeTaken = (pathTimes.get(i)) / 1000.0;//Time taken for path, seconds
                    double distance = userHeights.get(i) * polyline.getPoints().size() * 2;

                    //Remove markers of previous path when new one clicked
                    if (m1 != null && m2 != null) {
                        m1.remove();
                        m1 = null;
                        m2.remove();
                        m2 = null;
                    }

                    //Get string detailing time taken
                    String timeInfo, distInfo;
                    int minutesTaken = (int) timeTaken / 60;
                    double extraSeconds = timeTaken - (60 * minutesTaken);
                    timeInfo = "Time taken: " + minutesTaken + " minutes, " + (int) extraSeconds
                            + " seconds";
                    distInfo = "Distance: " + distance + " meters";

                    //Place markers at start and end of selected path
                    m1 = googleMap.addMarker(new MarkerOptions().position(start)
                            .title("Pathway #" + (i + 1) + " start")
                            .snippet(timeInfo).snippet(distInfo));
                    m2 = googleMap.addMarker(new MarkerOptions().position(end)
                            .title("Pathway #" + (i + 1) + " end")
                            .snippet(timeInfo));

                }
            });


        } catch (Exception e) {
            Log.w("Error", "" + e.getMessage());
        }

    }
}
