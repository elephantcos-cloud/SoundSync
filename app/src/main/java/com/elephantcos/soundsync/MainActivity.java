package com.elephantcos.soundsync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.elephantcos.soundsync.fragment.DevicesFragment;
import com.elephantcos.soundsync.fragment.StreamFragment;
import com.elephantcos.soundsync.fragment.FilesFragment;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_CODE = 101;
    private Socket activeSocket;
    private String peerIp = "";

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
            if (ActivityCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED)
                denied.add(p);
        if (!denied.isEmpty())
            ActivityCompat.requestPermissions(this,
                denied.toArray(new String[0]), PERM_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
    }

    public void onConnected(String ip, Socket socket) {
        this.peerIp       = ip;
        this.activeSocket = socket;
    }

    public void onConnected(String address, boolean isOwner) { this.peerIp = address; }
    public Socket  getActiveSocket()       { return activeSocket; }
    public String  getPeerIp()             { return peerIp; }
    public String  getGroupOwnerAddress()  { return peerIp; }
    public boolean isGroupOwner()          { return false; }

    private void loadFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, f).commit();
    }
}
