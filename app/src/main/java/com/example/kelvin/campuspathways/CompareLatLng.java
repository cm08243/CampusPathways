package com.example.kelvin.campuspathways;

import com.google.android.gms.maps.model.LatLng;

import java.util.Comparator;

//Sorts LatLng points
class CompareLatLng implements Comparator<LatLng> {

    @Override
    public int compare(LatLng latLng1, LatLng latLng2) {

        double temp = latLng1.latitude - latLng2.latitude;

        //LatLng 1 < LatLng 2
        if (temp < 0)
            return -1;

        //LatLng1 > LatLng 2
        if (temp > 0)
            return 1;

            //LatLng 1 == LatLng2
        else {
            return 0;
        }

    }

}
