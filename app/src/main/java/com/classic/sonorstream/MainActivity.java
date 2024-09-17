package com.classic.sonorstream;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import com.classic.sonorstream.databinding.ActivityMainBinding;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    static float toggle_opt_x;
    int streamLoaded = 1;
    // Used to load the 'sonorstream' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.classic.sonorstream.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewPager = findViewById(R.id.view_pager);
        viewPager.post(() -> toggle_opt_x = viewPager.getY());

        MaterialToolbar toolbar = findViewById(R.id.appbar2);
        setSupportActionBar(toolbar);

        MaterialButtonToggleGroup toggleButton = findViewById(R.id.toggleButton);

        TogglePagerAdapter adapter = new TogglePagerAdapter(this);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if(position == 0) {
                    toggleButton.check(R.id.stream_button);
                    streamLoaded = 1;
                }
                else {
                    toggleButton.check(R.id.receive_button);
                    streamLoaded = 0;
                }
            }
        });

        //Stream button code
        Button stream = findViewById(R.id.stream_button);
        stream.setOnClickListener(view -> {
            if(streamLoaded == 0) {
                viewPager.setCurrentItem(0);
                streamLoaded = 1;
            }
        });
        Button receive = findViewById(R.id.receive_button);
        receive.setOnClickListener(view -> {
            if(streamLoaded == 1) {
                viewPager.setCurrentItem(1);
                streamLoaded = 0;
            }
        });
//        FloatingActionButton fabSettings = findViewById(R.id.fab_settings);
//        fabSettings.setOnClickListener(view -> {
//            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
//            startActivity(intent);
//        });
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_act_top_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Switching on the item id of the menu item
        if (item.getItemId() == R.id.settings_mainact) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } else if (item.getItemId() == R.id.about_act) {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    /*
      A native method that is implemented by the 'sonorstream' native library,
      which is packaged with this application.
     */
}