package com.example.kelvin.campuspathways;

import android.os.AsyncTask;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Created by Kelvin on 2/25/2018.
 * Used to asynchronously insert data into the database
 */

public class DatabaseConnectionInsert extends AsyncTask<String, Void, Void> {

    private String query;//Query to be executed

    //Only constructor
    public DatabaseConnectionInsert(String query) {
        this.query = query;
    }

    //Connect to database and perform query
    @Override
    protected Void doInBackground(String... strings) {

        try {

            //Connection information
            String dns = "on-campus-navigation.caqb3uzoiuo3.us-east-1.rds.amazonaws.com";
            String aClass = "net.sourceforge.jtds.jdbc.Driver";
            Class.forName(aClass).newInstance();

            //Connect to database
            Connection dbConnection = DriverManager.getConnection("jdbc:jtds:sqlserver://" + dns +
                    "/Campus-Navigation;user=Android;password=password");

            //Execute query; In this case Insertion
            Statement statement = dbConnection.createStatement();
            statement.execute(query);

            //Close connection to database
            dbConnection.close();

        } catch (Exception e) {
            Log.w("Error", "" + e.getMessage());
            return null;
        }

        return null;
    }
}
