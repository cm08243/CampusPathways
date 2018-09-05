package com.example.kelvin.campuspathways;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by Kelvin on 3/11/2018.
 * Queries database to get path information
 * Extracts start and end point of each path
 * Groups points together to create nodes
 */

public class DatabaseConnectionCreateNodes extends AsyncTask<Void, Void, Void> {

    //Max distance for points to be in same node, in meters
    private final double maxNodeDistance = 100.0;
    //Number of times markers have been clicked
    int markersClicked = 0;
    //List of paths
    private ArrayList<LatLng> points;
    //List of node groupings
    private ArrayList<ArrayList> nodes;
    //List of node center points
    private ArrayList<LatLng> nodeCenters;
    //Map to use
    private GoogleMap gMap;
    //List of paths between 2 selected nodes
    private ArrayList<String> paths;
    //List of all paths
    private ArrayList<String> allPaths;
    //List of all visible paths
    private ArrayList<Polyline> pathLines;

    //Constructor
    DatabaseConnectionCreateNodes(GoogleMap map) {

        //Initialize objects
        points = new ArrayList<>();
        nodes = new ArrayList<>();
        nodeCenters = new ArrayList<>();
        paths = new ArrayList<>();
        allPaths = new ArrayList<>();
        pathLines = new ArrayList<>();
        gMap = map;

    }

    //This is a helper function needed for convex hull
    private static int orientation(LatLng p, LatLng q, LatLng r) {
        double val = (q.longitude - p.longitude) * (r.latitude - q.latitude) -
                (q.latitude - p.latitude) * (r.longitude - q.longitude);

        if (val == 0) return 0;  // collinear
        return (val > 0) ? 1 : 2; // clock or counterclock wise
    }

    @Override
    protected Void doInBackground(Void... voids) {

        //Connect to database to get path information
        try {

            //Connection information
            String dns = "on-campus-navigation.caqb3uzoiuo3.us-east-1.rds.amazonaws.com";
            String aClass = "net.sourceforge.jtds.jdbc.Driver";
            Class.forName(aClass).newInstance();

            //Connect to database
            Connection dbConnection = DriverManager.getConnection("jdbc:jtds:sqlserver://" + dns +
                    "/Campus-Navigation;user=Android;password=password");

            //Create query
            String query = "SELECT * FROM Pathways";

            //Execute query; In this case Selection
            Statement statement = dbConnection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);

            //Iterate through result and put in ArrayList
            while (resultSet.next()) {

                //Get path JSON
                String pathJSONString = resultSet.getString("User_Path");
                JSONArray pathJSON = new JSONArray(pathJSONString);

                //Get JSON of start and end points
                JSONObject startPointJSON = pathJSON.getJSONObject(0);
                JSONObject endPointJSON = pathJSON.getJSONObject(pathJSON.length() - 1);

                //Get LatLng of start and end points
                LatLng startPoint =
                        new LatLng(startPointJSON.getDouble("Latitude"), startPointJSON.getDouble("Longitude"));
                LatLng endPoint =
                        new LatLng(endPointJSON.getDouble("Latitude"), endPointJSON.getDouble("Longitude"));

                //Insert points into ArrayList
                points.add(startPoint);
                points.add(endPoint);

                //Insert path into ArrayList
                paths.add(pathJSONString);

            }

            allPaths.addAll(paths);

            //Close connection to database
            dbConnection.close();

        } catch (Exception e) {
            Log.w("Error", "" + e.getMessage());
            return null;
        }

        return null;

    }

    @Override
    protected void onPostExecute(Void aVoid) {

        super.onPostExecute(aVoid);

        //Get points corresponding to each node
        createNodes();

        //Apply Convex Hull to nodes to get node center point
        for (ArrayList node : nodes) {

            //By using the ConvexHull, we leave out a significant amount of points to be
            //included in the average, thus getting the node more efficiently
            LatLng temp = convexHull(node);//We get the convexHull to filter out results

            //Filter out null results
            if (temp == null) continue;

            //We store the node in an arrayList
            nodeCenters.add(temp);

        }

        plotNodes();

    }

    //Recursive method to create node groupings
    private void createNodes() {

        //Base case; End
        if (points.isEmpty()) return;

        //ArrayList of points in node
        ArrayList<LatLng> node = new ArrayList<>();

        //Get first point in ArrayList
        LatLng startPoint = points.get(0);
        node.add(startPoint);

        //Second base case; Save last point and return
        if (points.size() == 1) {
            nodes.add(node);
            return;
        }

        //Get distance to every other point in ArrayList

        //Iterate though points in Array List to get distances
        for (int i = 1; i < points.size(); i++) {

            //Calculate distance between first point and current point
            LatLng point = points.get(i);
            double distance = SphericalUtil.computeDistanceBetween(startPoint, point);

            //Add to node List if close enough
            if (distance <= maxNodeDistance) {
                node.add(point);
            }

        }

        //Sort Node points
        Collections.sort(node, new CompareLatLng());

        //Add node to list of nodes
        nodes.add(node);

        //Remove points in node from overall list
        points.removeAll(node);

        //Recurse
        createNodes();

    }

    //The idea behind this is to create a perimeter using starting and stopping points.
    //The points on the edge of the perimeter will be averaged together to find the center.
    //The center of the convex hull will be used as a node (Point of interest)
    //This convex hull algorithm is based on Jarvis' Algorithm/Wrapping
    private LatLng convexHull(ArrayList<LatLng> points) {

        int numOfPoints = points.size();

        //Ensure that at least 3 points are in the list
        if (points.size() < 3) return null;

        //Initialize result
        ArrayList<LatLng> hull = new ArrayList<>();

        //This loop runs O(h) times where h is
        // number of points in result or output.
        int p = 0, q;
        do {
            //Add current point to result
            hull.add(points.get(p));

            //The idea is to keep
            // track of last visited most counterclock-
            // wise point in q. If any point 'i' is more
            // counterclock-wise than q, then update q.
            q = (p + 1) % numOfPoints;

            for (int i = 0; i < numOfPoints; i++) {
                // If i is more counterclockwise than
                // current q, then update q
                if (orientation(points.get(p), points.get(i), points.get(q)) == 2)
                    q = i;
            }

            // Now q is the most counterclockwise with
            // respect to p. Set p as q for next iteration,
            // so that q is added to result 'hull'
            p = q;

        } while (p != 0);  // While we don't come to first

        //We first get the points that have been added to the hull and store them
        double tempLat[];
        double tempLng[];
        tempLat = new double[hull.size()];
        tempLng = new double[hull.size()];

        for (int i = 0; i < hull.size(); i++) {
            tempLat[i] = hull.get(i).latitude;       //We store the points temporarily to avoid a nullPointerException
            tempLng[i] = hull.get(i).longitude;       //We store the points temporarily to avoid a nullPointerException
        }

        //Convex hull
        LatLng[] mConvexHull;

        //We get the number of points that will be in the convexHull with hull.size()
        mConvexHull = new LatLng[hull.size()];
        for (int i = 0; i < hull.size(); i++) {
            mConvexHull[i] = new LatLng(tempLat[i], tempLng[i]);  //We add the tempPoints into the convexHull
        }

        double avgLat = 0, avgLng = 0;

        //We get the average of the convexHull points rather than all the starting points
        //We first add them up
        for (LatLng aMConvexHull : mConvexHull) {
            avgLat += aMConvexHull.latitude;
            avgLng += aMConvexHull.longitude;
        }

        //We then divide by number of points we added up to get average
        avgLat /= mConvexHull.length;
        avgLng /= mConvexHull.length;

        //return center point
        return new LatLng(avgLat, avgLng);

    }

    //Removes any paths from the list that don't have node as the start or end node
    private void filterPathsNode(LatLng node) {

        //No use if list is empty
        if (paths.isEmpty()) return;

        //Buffer to store paths to remove
        ArrayList<String> pathsToRemove = new ArrayList<>();

        //Iterate through paths and filter
        for (String path : paths) {

            //Parse JSON to get start and top points
            JSONArray pathJSON = null;
            try {
                pathJSON = new JSONArray(path);

                JSONObject startPointJSON = pathJSON.getJSONObject(0);
                JSONObject endPointJSON = pathJSON.getJSONObject(pathJSON.length() - 1);

                LatLng startPoint = new LatLng(startPointJSON.getDouble("Latitude"),
                        startPointJSON.getDouble("Longitude"));
                LatLng endPoint = new LatLng(endPointJSON.getDouble("Latitude"),
                        endPointJSON.getDouble("Longitude"));

                //Check if start or end point is near node
                if (SphericalUtil.computeDistanceBetween(startPoint, node) > maxNodeDistance / 2
                        && SphericalUtil.computeDistanceBetween(endPoint, node) > maxNodeDistance / 2) {

                    //If start and end point too far from node, store in buffer
                    pathsToRemove.add(path);

                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        //Remove paths in buffer from global list
        paths.removeAll(pathsToRemove);

    }

    //Puts node markers on map
    private void plotNodes() {

        //If not enough points for nodes, return
        if (nodeCenters.isEmpty()) return;

        //Move map to 1st point of 1st path
        LatLng mapStart = nodeCenters.get(0);
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mapStart, 16.0f));//Zoom domain: [2.0,21.0]

        //Display a marker at each node
        for (LatLng point : nodeCenters) {

            /*
            //Get marker at node
            Marker marker = gMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title("A node"));

            marker.setVisible(false);
            */

            //Draw circle around node; Clickable to filter paths
            Circle circle = gMap.addCircle(new CircleOptions()
                    .center(point)
                    .radius(maxNodeDistance / 2));

            circle.setClickable(true);
            circle.setFillColor(circle.getStrokeColor());

        }

    }

    //Reset paths to all paths and reset counter
    void resetPaths() {

        //Remove drawn lines
        for (Polyline line : pathLines) line.remove();
        pathLines.clear();

        paths.clear();
        paths.addAll(allPaths);
        markersClicked = 0;
    }

    //Plot paths
    void plotPaths(LatLng markerPosition) {

        gMap.clear();

        filterPathsNode(markerPosition);
        plotNodes();

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

                //Draw pathways and make clickable
                Polyline path = gMap.addPolyline(new PolylineOptions().addAll(points).width(10).color(Color.RED));
                path.setClickable(true);
                pathLines.add(path);

            }

        } catch (Exception e) {
            e.getStackTrace();
        }

    }

}