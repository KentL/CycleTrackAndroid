/**     CycleTracks, Copyright 2009,2010 San Francisco County Transportation Authority
 *                                    San Francisco, CA, USA
 *
 *          @author Billy Charlton <billy.charlton@sfcta.org>
 *
 *   This file is part of CycleTracks.
 *
 *   CycleTracks is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   CycleTracks is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with CycleTracks.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.kentli.cycletrack;

import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import android.
        Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.
        PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

public class RecordingActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final static int MENU_USER_INFO = 0;
    private final static int MENU_HELP = 1;
    private final static int MENU_HISTORY = 2;

    private final static int CONTEXT_RETRY = 0;
    private final static int CONTEXT_DELETE = 1;

    Intent fi;
    TripData trip;
    boolean isRecording = false;
    Button finishButton;
    Button startButton;
    Button pauseButton;
    Timer timer;
    float curDistance;

    TextView txtStat;
    TextView txtDistance;
    TextView txtDuration;
    TextView txtCurSpeed;
    TextView txtMaxSpeed;
    TextView txtAvgSpeed;

    LinearLayout pauseStopLayout;
    GoogleMap map;
    Intent rService;
    IRecordService recordService;

    final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    // Need handler for callbacks to the UI thread
    final Handler mHandler = new Handler();
    final Runnable mUpdateTimer = new Runnable() {
        public void run() {
            updateTimer();
        }
    };

    /* About getting current location
     * http://stackoverflow.com/questions/13756261/how-to-get-the-current-location-in-google-maps-android-api-v2
     */
    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
        private boolean init=false;
        @Override
        public void onMyLocationChange(Location location) {
            LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
            if(map != null){
                map.moveCamera(CameraUpdateFactory.newLatLng(loc));
                if (!init) {
                    map.animateCamera(CameraUpdateFactory.zoomTo(15));
                    init=true;
                }
            }
        }
    };
    @Override
    public void onMapReady(GoogleMap map) {
        this.map=map;
        map.setOnMyLocationChangeListener(myLocationChangeListener);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);

        } else {
            // Show rationale and request permission.
        }

        // Zoom in the Google Map
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recording);
        initWidget();

        if(rService==null)
            rService = new Intent(RecordingActivity.this, RecordingService.class);
        startService(rService);

        ServiceConnection sc = new ServiceConnection() {
            public void onServiceDisconnected(ComponentName name) {}
            public void onServiceConnected(ComponentName name, IBinder service) {
                IRecordService rs = (IRecordService) service;
                recordService=rs;

                int state = rs.getState();
                updateUIAccordingToState(rs.getState());
                if (state > RecordingService.STATE_IDLE) {
                    if (state == RecordingService.STATE_FULL) {
                        startActivity(new Intent(RecordingActivity.this, SaveTrip.class));
                    }else {
                        if (state==RecordingService.STATE_RECORDING) {
                            isRecording = true;
                            initTrip();
                        }
                        //PAUSE or RECORDING...
                        recordService.setListener(RecordingActivity.this);
                    }
                } else {
                    //  First run? Switch to user prefs screen if there are no prefs stored yet
                    SharedPreferences settings = getSharedPreferences("PREFS", 0);
                    if (settings.getAll().isEmpty()) {
                        showWelcomeDialog();
                    }
                }
                RecordingActivity.this.unbindService(this); // race?  this says we no longer care

                // Before we go to record, check GPS status
                final LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );
                if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
                    buildAlertMessageNoGps();
                }
            }
        };
        // This needs to block until the onServiceConnected (above) completes.
        // Thus, we can check the recording status before continuing on.
        bindService(rService, sc, Context.BIND_AUTO_CREATE);


        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        //Google Map
        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        setButtonOnClickListeners();

    }

    private void initWidget(){

        txtStat =     (TextView) findViewById(R.id.TextRecordStats);
        txtDistance = (TextView) findViewById(R.id.TextDistance);
        txtDuration = (TextView) findViewById(R.id.TextDuration);
        txtCurSpeed = (TextView) findViewById(R.id.TextSpeed);
        txtMaxSpeed = (TextView) findViewById(R.id.TextMaxSpeed);
        txtAvgSpeed = (TextView) findViewById(R.id.TextAvgSpeed);

        startButton = (Button) findViewById(R.id.startButton);
        finishButton = (Button) findViewById(R.id.stopButton);
        pauseButton=(Button) findViewById(R.id.pauseButton);

        pauseStopLayout=(LinearLayout)findViewById(R.id.pause_stop_layout);
    }
    private void setButtonOnClickListeners(){
        // Start button
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rService==null)
                    rService = new Intent(RecordingActivity.this, RecordingService.class);

                trip = TripData.createTrip(RecordingActivity.this);
                recordService.startRecording(trip);
                recordService.setListener(RecordingActivity.this);
                isRecording = true;
                updateUIAccordingToState(recordService.getState());
            }

    });
        // Pause button
        pauseButton.setEnabled(false);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (trip==null)
                    initTrip();

                isRecording = !isRecording;
                if (isRecording) {
                    pauseButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.pause_button));
                    RecordingActivity.this.setTitle("CycleTracks - Recording...");
                    // Don't include pause time in trip duration
                    if (trip.pauseStartedAt > 0) {
                        trip.totalPauseTime += (System.currentTimeMillis() - trip.pauseStartedAt);
                        trip.pauseStartedAt = 0;
                    }
                    Toast.makeText(getBaseContext(),"GPS restarted. It may take a moment to resync.", Toast.LENGTH_LONG).show();
                } else {
                    pauseButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.resume_button));
                    RecordingActivity.this.setTitle("CycleTracks - Paused...");
                    trip.pauseStartedAt = System.currentTimeMillis();
                    Toast.makeText(getBaseContext(),"Recording paused; GPS now offline", Toast.LENGTH_LONG).show();
                }
                RecordingActivity.this.setListener();
            }
        });

        // Finish button
        finishButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (trip==null)
                    initTrip();
                isRecording=false;
                // If we have points, go to the save-trip activity
                if (trip.numpoints > 0) {
                    // Handle pause time gracefully
                    if (trip.pauseStartedAt > 0) {
                        trip.totalPauseTime += (System.currentTimeMillis() - trip.pauseStartedAt);
                    }
                    if (trip.totalPauseTime > 0) {
                        trip.endTime = System.currentTimeMillis() - trip.totalPauseTime;
                    }
                    // Save trip so far (points and extent, but no purpose or notes)
                    fi = new Intent(RecordingActivity.this, SaveTrip.class);
                    trip.updateTrip("", "", "", "");
                    recordService.cancelRecording();
                }
                // Otherwise, cancel and go back to main screen
                else {
                    Toast.makeText(getBaseContext(), "No GPS data acquired; nothing to submit.", Toast.LENGTH_SHORT).show();
                    recordService.cancelRecording();
                    updateUIAccordingToState(recordService.getState());
                }

                updateUIAccordingToState(recordService.getState());
                if (fi!=null) {
                    startActivity(fi);
                    RecordingActivity.this.finish();
                }
            }
        });
    }
    private void initTrip(){
        long tid = recordService.getCurrentTrip();
        trip = TripData.fetchTrip(RecordingActivity.this, tid);
        if (trip==null)
            trip = TripData.createTrip(RecordingActivity.this);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_HELP, 0, "Help and FAQ").setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, MENU_USER_INFO, 0, "Edit User Info").setIcon(android.R.drawable.ic_menu_edit);
        menu.add(0, MENU_HISTORY, 0, "Trip History").setIcon(android.R.drawable.ic_menu_recent_history);

        return true;
    }
    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_USER_INFO:
                startActivity(new Intent(this, UserInfoActivity.class));
                return true;
            case MENU_HELP:
                Intent myIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://www.sfcta.org/cycletracks-androidhelp.html"));
                startActivity(myIntent);
                return true;
            case MENU_HISTORY:
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
        }
        return false;
    }


    private void updateUIAccordingToState(int state){
        switch (state) {
            case RecordingService.STATE_IDLE:
                isRecording=false;
                RecordingActivity.this.txtDuration.setText("00:00:00");
                RecordingActivity.this.txtStat.setText("");
                RecordingActivity.this.pauseStopLayout.setVisibility(View.GONE);
                RecordingActivity.this.startButton.setVisibility(View.VISIBLE);
                RecordingActivity.this.pauseButton.setEnabled(true);
                RecordingActivity.this.setTitle("CycleTracks");
                break;
            case RecordingService.STATE_RECORDING:
                isRecording=true;
                RecordingActivity.this.pauseStopLayout.setVisibility(View.VISIBLE);
                RecordingActivity.this.startButton.setVisibility(View.GONE);
                RecordingActivity.this.pauseButton.setEnabled(true);
                RecordingActivity.this.setTitle("CycleTracks - Recording...");
                break;
            case RecordingService.STATE_PAUSED:
                isRecording=true;
                RecordingActivity.this.pauseStopLayout.setVisibility(View.VISIBLE);
                RecordingActivity.this.startButton.setVisibility(View.GONE);
                RecordingActivity.this.pauseButton.setEnabled(true);
                RecordingActivity.this.pauseButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.resume_button));
                RecordingActivity.this.setTitle("CycleTracks - Paused...");
                break;
            case RecordingService.STATE_FULL:
                // Should never get here, right?
                break;
        }
    }
    public void updateStatus(int points, float distance, float spdCurrent, float spdMax) {
        this.curDistance = distance;

        //TODO: check task status before doing this?
        if (points>0) {
            txtStat.setText(""+points+" data points received...");
        } else {
            txtStat.setText("Waiting for GPS fix...");
        }
        txtCurSpeed.setText(String.format("%1.1f", spdCurrent));
        txtMaxSpeed.setText(String.format("%1.1f", spdMax));

        float miles = 0.0006212f * distance;
        txtDistance.setText(String.format("%1.1f", miles));


    }


    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your phone's GPS is disabled. CycleTracks needs GPS to determine your location.\n\nGo to System Settings now to enable GPS?")
                .setCancelable(false)
                .setPositiveButton("GPS Settings...", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        final ComponentName toLaunch = new ComponentName("com.android.settings","com.android.settings.SecuritySettings");
                        final Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        intent.setComponent(toLaunch);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivityForResult(intent, 0);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void showWelcomeDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Please enter your personal details so we can learn a bit about you.\n\nThen, try to use CycleTracks every time you ride. Your trip routes will be sent to the SFCTA so we can plan for better biking!\n\nThanks,\nThe SFCTA CycleTracks team")
                .setCancelable(false).setTitle("Welcome to CycleTracks!")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(RecordingActivity.this, UserInfoActivity.class));
                    }
                });

        final AlertDialog alert = builder.create();
        alert.show();
    }
    void setListener() {
        Intent rService = new Intent(this, RecordingService.class);
        ServiceConnection sc = new ServiceConnection() {
            public void onServiceDisconnected(ComponentName name) {}
            public void onServiceConnected(ComponentName name, IBinder service) {
                IRecordService rs = (IRecordService) service;
                if (RecordingActivity.this.isRecording) {
                    rs.resumeRecording();
                } else {
                    rs.pauseRecording();
                }
                unbindService(this);
            }
        };
        // This should block until the onServiceConnected (above) completes, but doesn't
        bindService(rService, sc, Context.BIND_AUTO_CREATE);
    }

    void cancelRecording() {
        Intent rService = new Intent(this, RecordingService.class);
        ServiceConnection sc = new ServiceConnection() {
            public void onServiceDisconnected(ComponentName name) {}
            public void onServiceConnected(ComponentName name, IBinder service) {
                IRecordService rs = (IRecordService) service;
                rs.cancelRecording();
                unbindService(this);
            }
        };
        // This should block until the onServiceConnected (above) completes.
        bindService(rService, sc, Context.BIND_AUTO_CREATE);
    }

    // onResume is called whenever this activity comes to foreground.
    // Use a timer to update the trip duration.
    @Override
    public void onResume() {
        super.onResume();

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(mUpdateTimer);
            }
        }, 0, 1000);  // every second
    }

    void updateTimer() {
        if (trip != null && isRecording) {
            double dd = System.currentTimeMillis()
                    - trip.startTime
                    - trip.totalPauseTime;

            txtDuration.setText(sdf.format(dd));

            double avgSpeed = 3600.0 * 0.6212 * this.curDistance / dd;
            txtAvgSpeed.setText(String.format("%1.1f", avgSpeed));
        }
    }

    // Don't do pointless UI updates if the activity isn't being shown.
    @Override
    public void onPause() {
        super.onPause();
        if (timer != null) timer.cancel();
    }
}