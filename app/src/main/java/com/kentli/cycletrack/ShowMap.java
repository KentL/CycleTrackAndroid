/**
 * CycleTracks, Copyright 2009,2010 San Francisco County Transportation Authority
 * San Francisco, CA, USA
 *
 * @author Billy Charlton <billy.charlton@sfcta.org>
 * <p/>
 * This file is part of CycleTracks.
 * <p/>
 * CycleTracks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * CycleTracks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with CycleTracks.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.kentli.cycletrack;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;


public class ShowMap extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mapView;
    ArrayList<CyclePoint> gpspoints;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mapview);

        try {
            // Set zoom controls
             ((MapFragment) getFragmentManager().findFragmentById(R.id.map_ObservationDetailActivity)).getMapAsync(this);

            Bundle cmds = getIntent().getExtras();
            long tripid = cmds.getLong("showtrip");

            TripData trip = TripData.fetchTrip(this, tripid);

            // Show trip details
            TextView t1 = (TextView) findViewById(R.id.TextViewT1);
            TextView t2 = (TextView) findViewById(R.id.TextViewT2);
            TextView t3 = (TextView) findViewById(R.id.TextViewT3);
            t1.setText(trip.purp);
            t2.setText(trip.info);
            t3.setText(trip.fancystart);

            AddPointsToMapLayerTask maptask = new AddPointsToMapLayerTask();
            maptask.execute(trip);

            if (trip.status < TripData.STATUS_SENT
                    && cmds != null
                    && cmds.getBoolean("uploadTrip", false)) {
                // And upload to the cloud database, too!  W00t W00t!
                TripUploader uploader = new TripUploader(ShowMap.this);
                uploader.execute(trip.tripid);
            }

        } catch (Exception e) {
            Log.e("GOT!", e.toString());
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mapView = googleMap;
    }


    private class AddPointsToMapLayerTask extends AsyncTask<TripData, Integer, ArrayList<CyclePoint>> {
        TripData trip;

        @Override
        protected ArrayList<CyclePoint> doInBackground(TripData... trips) {
            trip = trips[0]; // always get just the first trip

            if (gpspoints == null)
                gpspoints = trip.getPoints();

            return gpspoints;
        }

        @Override
        protected void onPostExecute(ArrayList<CyclePoint> gpspoints) {
            if (gpspoints.size()<=0)
                return;
            ArrayList<LatLng> points = new ArrayList<>();
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (int i = 0; i < gpspoints.size(); i++) {
                CyclePoint point = gpspoints.get(i);
                double lat = point.geoPoint.latitude;
                double lng = point.geoPoint.longitude;
                LatLng position = new LatLng(lat, lng);
                points.add(position);
                builder.include(position);
            }
            Polyline route = mapView.addPolyline(new PolylineOptions()
                            .width(5)
                            .color(Color.BLUE)
                            .geodesic(true)
            );
            route.setPoints(points);

            LatLngBounds bounds = builder.build();
            int padding = 30; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            mapView.moveCamera(cu);
            mapView.animateCamera(cu);

            LatLng startPoint=new LatLng(gpspoints.get(0).geoPoint.latitude,gpspoints.get(0).geoPoint.longitude);
            LatLng endPoint=new LatLng(gpspoints.get(gpspoints.size()-1).geoPoint.latitude,gpspoints.get(gpspoints.size()-1).geoPoint.longitude);
            mapView.addMarker(new MarkerOptions()
                    .position(startPoint))
                    .setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            mapView.addMarker(new MarkerOptions()
                    .position(endPoint))
                    .setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        }
    }

}
