package com.kentli.cycletrack;

import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class HistoryActivity extends AppCompatActivity {
    private final static int CONTEXT_RETRY = 0;
    private final static int CONTEXT_DELETE = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);
        ListView listTrips = (ListView) findViewById(R.id.ListTrips);
        populateList(listTrips);
        setTitle("Saved Trips");
    }

    void populateList(ListView lv) {
        // Get list from the real phone database. W00t!
        DbAdapter mDb = new DbAdapter(HistoryActivity.this);
        mDb.open();

        // Clean up any bad trips & coords from crashes
        int cleanedTrips = mDb.cleanTables();
        if (cleanedTrips > 0) {
            Toast.makeText(getBaseContext(), "" + cleanedTrips + " bad trip(s) removed.", Toast.LENGTH_SHORT).show();
        }

        try {
            Cursor allTrips = mDb.fetchAllTrips();

            SimpleCursorAdapter sca = new SimpleCursorAdapter(this,
                    R.layout.twolinelist, allTrips,
                    new String[] { "purp", "fancystart", "fancyinfo"},
                    new int[] {R.id.TextView01, R.id.TextView03, R.id.TextInfo}
            );

            lv.setAdapter(sca);
            TextView counter = (TextView) findViewById(R.id.TextViewPreviousTrips);

            int numtrips = allTrips.getCount();
            switch (numtrips) {
                case 0:
                    counter.setText("No saved trips.");
                    break;
                case 1:
                    counter.setText("1 saved trip:");
                    break;
                default:
                    counter.setText("" + numtrips + " saved trips:");
            }
            // allTrips.close();
        } catch (SQLException sqle) {
            // Do nothing, for now!
        }
        mDb.close();

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                Intent i = new Intent(HistoryActivity.this, ShowMap.class);
                i.putExtra("showtrip", id);
                startActivity(i);
            }
        });
        registerForContextMenu(lv);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, CONTEXT_RETRY, 0, "Retry Upload");
        menu.add(0, CONTEXT_DELETE, 0, "Delete");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_RETRY:
                retryTripUpload(info.id);
                return true;
            case CONTEXT_DELETE:
                deleteTrip(info.id);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void retryTripUpload(long tripId) {
        TripUploader uploader = new TripUploader(HistoryActivity.this);
        uploader.execute(tripId);
    }

    private void deleteTrip(long tripId) {
        DbAdapter mDbHelper = new DbAdapter(HistoryActivity.this);
        mDbHelper.open();
        mDbHelper.deleteAllCoordsForTrip(tripId);
        mDbHelper.deleteTrip(tripId);
        mDbHelper.close();
        ListView listSavedTrips = (ListView) findViewById(R.id.ListTrips);
        listSavedTrips.invalidate();
        populateList(listSavedTrips);
    }

}
