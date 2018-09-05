package com.example.kelvin.campuspathways;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by Kelvin on 2/25/2018.
 * Class that combines a location with a time
 * Used for storage of paths in database
 */

public class TimedLocation {

    private LatLng location;//Location at instant
    private long timestamp;//Time of instant

    //Only constructor
    public TimedLocation(LatLng location) {
        this.location = location;
        timestamp = System.currentTimeMillis();
    }

    //Accessor methods
    public LatLng getLocation() {
        return location;
    }

    public long getTimestamp() {
        return timestamp;
    }

}
