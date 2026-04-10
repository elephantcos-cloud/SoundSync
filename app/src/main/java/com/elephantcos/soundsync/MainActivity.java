package com.elephantcos.soundsync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.elephantcos.soundsync.fragment.DevicesFragment;
import com.elephantcos.soundsync.fragment.StreamFragment;
import com.elephantcos.soundsync.fragment.FilesFragment;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_CODE = 101;
    private String  groupOwnerAddress = "";
    private boolean groupOwner        = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestAllPermissions();

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

    private void requestAllPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.RECORD_AUDIO);
        perms.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        List<String> denied = new ArrayList<>();
        for (String p : perms)
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                denied.add(p);

        if (!denied.isEmpty())
            ActivityCompat.requestPermissions(this, denied.toArray(new String[0]), PERM_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERM_CODE) {
            for (int i = 0; i < results.length; i++) {
                if (results[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                        perms[i].substring(perms[i].lastIndexOf('.')+1) +
                        " permission ছাড়া কিছু feature কাজ করবে না।",
                        Toast.LENGTH_LONG).show();
                }
            }
        }
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
