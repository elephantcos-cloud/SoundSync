package com.elephantcos.soundsync;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.elephantcos.soundsync.fragment.DevicesFragment;
import com.elephantcos.soundsync.fragment.StreamFragment;
import com.elephantcos.soundsync.fragment.FilesFragment;

public class MainActivity extends AppCompatActivity {

    private String groupOwnerAddress = "";
    private boolean groupOwner       = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        loadFragment(new DevicesFragment());

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if      (id == R.id.nav_stream) f = new StreamFragment();
            else if (id == R.id.nav_files)  f = new FilesFragment();
            else                             f = new DevicesFragment();
            loadFragment(f);
            return true;
        });
    }

    private void loadFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, f).commit();
    }

    public void onConnected(String address, boolean isOwner) {
        this.groupOwnerAddress = address;
        this.groupOwner        = isOwner;
    }

    public String  getGroupOwnerAddress() { return groupOwnerAddress; }
    public boolean isGroupOwner()         { return groupOwner; }
}
