package com.example.market_day_2013;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class BlobView extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blob_view);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.blob_view, menu);
        return true;
    }
    
}
