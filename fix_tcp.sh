#!/bin/bash
cd ~/SoundSync

echo "=== SoundSync TCP Fix Script ==="

# 1. ConnectionManager
mkdir -p app/src/main/java/com/elephantcos/soundsync/connection
cat > app/src/main/java/com/elephantcos/soundsync/connection/ConnectionManager.java << 'JAVAEOF'
package com.elephantcos.soundsync.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionManager {

    public interface Listener {
        void onConnected(String peerIp, Socket socket);
        void onDisconnected();
        void onError(String msg);
    }

    private static final int PORT = 47832;
    private final Context         ctx;
    private final Listener        listener;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private       ServerSocket    serverSocket;
    private       Socket          activeSocket;
    private       boolean         running  = false;

    public ConnectionManager(Context ctx, Listener listener) {
        this.ctx      = ctx.getApplicationContext();
        this.listener = listener;
    }

    public String getMyUUID() {
        SharedPreferences prefs = ctx.getSharedPreferences("soundsync", Context.MODE_PRIVATE);
        String id = prefs.getString("uuid", null);
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            prefs.edit().putString("uuid", id).apply();
        }
        return id;
    }

    public String getMyIp() {
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return Formatter.formatIpAddress(ip);
            }
            for (NetworkInterface ni :
                    java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr :
                        java.util.Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception e) { /* ignore */ }
        return "0.0.0.0";
    }

    public String getDeviceName() { return android.os.Build.MODEL; }

    public String getQrContent() {
        return "SOUNDSYNC:" + getMyIp() + ":" + PORT + ":" +
               getDeviceName() + ":" + getMyUUID();
    }

    public void startServer() {
        running = true;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    activeSocket  = client;
                    listener.onConnected(client.getInetAddress().getHostAddress(), client);
                }
            } catch (IOException e) {
                if (running) listener.onError("Server error: " + e.getMessage());
            }
        });
    }

    public void connectTo(String ip, int port) {
        executor.execute(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip, port), 5000);
                activeSocket = s;
                listener.onConnected(ip, s);
            } catch (IOException e) {
                listener.onError("Connection failed: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        running = false;
        try { if (activeSocket  != null) activeSocket.close();  } catch (Exception ignored) {}
        try { if (serverSocket  != null) serverSocket.close();  } catch (Exception ignored) {}
        listener.onDisconnected();
    }

    public Socket getActiveSocket() { return activeSocket; }
    public int    getPort()         { return PORT; }
}
JAVAEOF
echo "[1/7] ConnectionManager.java done"

# 2. DevicesFragment
cat > app/src/main/java/com/elephantcos/soundsync/fragment/DevicesFragment.java << 'JAVAEOF'
package com.elephantcos.soundsync.fragment;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.Fragment;
import com.elephantcos.soundsync.MainActivity;
import com.elephantcos.soundsync.R;
import com.elephantcos.soundsync.connection.ConnectionManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.net.Socket;

public class DevicesFragment extends Fragment implements ConnectionManager.Listener {

    private ConnectionManager connManager;
    private TextView statusText;
    private TextView myInfoText;
    private ActivityResultLauncher<ScanOptions> scanLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) parseAndConnect(result.getContents());
        });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup cont, @Nullable Bundle saved) {
        View v = inf.inflate(R.layout.fragment_devices, cont, false);
        statusText = v.findViewById(R.id.status_text);
        myInfoText = v.findViewById(R.id.my_info_text);

        connManager = new ConnectionManager(requireContext(), this);

        String ip   = connManager.getMyIp();
        String uuid = connManager.getMyUUID();
        String name = connManager.getDeviceName();
        myInfoText.setText(name + "  |  " + ip + "  |  ID: " + uuid);

        WifiManager wm = (WifiManager)
            requireContext().getSystemService(android.content.Context.WIFI_SERVICE);
        if (wm != null && !wm.isWifiEnabled()) {
            statusText.setText("WiFi বন্ধ! চালু করুন।");
        } else if (ip.equals("0.0.0.0")) {
            statusText.setText("WiFi তে connect হননি। একই WiFi network এ থাকুন।");
        } else {
            statusText.setText("Ready — দুটো ফোন একই WiFi তে রাখুন");
            connManager.startServer();
        }

        v.findViewById(R.id.show_qr_btn).setOnClickListener(x -> showMyQr());
        v.findViewById(R.id.scan_qr_btn).setOnClickListener(x -> openQrScanner());
        v.findViewById(R.id.disconnect_btn).setOnClickListener(x -> connManager.disconnect());
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (connManager != null) connManager.disconnect();
    }

    private void showMyQr() {
        String ip = connManager.getMyIp();
        if (ip.equals("0.0.0.0")) {
            Toast.makeText(getContext(), "WiFi connect নেই!", Toast.LENGTH_LONG).show();
            return;
        }
        Bitmap qr = generateQr(connManager.getQrContent(), 600);
        if (qr == null) return;
        Dialog d = new Dialog(requireContext());
        d.setContentView(R.layout.dialog_qr);
        d.setCancelable(true);
        ((ImageView) d.findViewById(R.id.qr_image)).setImageBitmap(qr);
        ((TextView)  d.findViewById(R.id.qr_device_name)).setText(connManager.getDeviceName());
        ((TextView)  d.findViewById(R.id.qr_mac_text))
            .setText("IP: " + ip + "   ID: " + connManager.getMyUUID());
        d.findViewById(R.id.qr_close_btn).setOnClickListener(x -> d.dismiss());
        d.show();
    }

    private void openQrScanner() {
        ScanOptions o = new ScanOptions();
        o.setPrompt("অন্য ফোনের QR Code স্ক্যান করুন");
        o.setBeepEnabled(true);
        o.setOrientationLocked(false);
        scanLauncher.launch(o);
    }

    private void parseAndConnect(String raw) {
        if (!raw.startsWith("SOUNDSYNC:")) {
            Toast.makeText(getContext(), "Invalid QR", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String[] parts = raw.substring(10).split(":");
            String ip   = parts[0];
            int    port = Integer.parseInt(parts[1]);
            String name = parts.length > 2 ? parts[2] : "Unknown";
            statusText.setText("Connecting to " + name + " (" + ip + ")...");
            connManager.connectTo(ip, port);
        } catch (Exception e) {
            Toast.makeText(getContext(), "QR parse error", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap generateQr(String content, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            return b;
        } catch (WriterException e) { return null; }
    }

    @Override
    public void onConnected(String peerIp, Socket socket) {
        ui(() -> {
            statusText.setText("Connected! → " + peerIp);
            Toast.makeText(getContext(),
                "Connected! Stream বা Files ট্যাবে যান।", Toast.LENGTH_LONG).show();
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).onConnected(peerIp, socket);
        });
    }

    @Override public void onDisconnected() {
        ui(() -> statusText.setText("Disconnected"));
    }

    @Override public void onError(String msg) {
        ui(() -> {
            statusText.setText("Error: " + msg);
            Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
        });
    }

    private void ui(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }
}
JAVAEOF
echo "[2/7] DevicesFragment.java done"

# 3. MainActivity
cat > app/src/main/java/com/elephantcos/soundsync/MainActivity.java << 'JAVAEOF'
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
JAVAEOF
echo "[3/7] MainActivity.java done"

# 4. fragment_devices.xml
cat > app/src/main/res/layout/fragment_devices.xml << 'XMLEOF'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_dark"
    android:padding="20dp">

    <TextView android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="Connect Devices"
        android:textColor="@color/white" android:textSize="26sp"
        android:textStyle="bold" android:layout_marginBottom="8dp" />

    <TextView android:id="@+id/my_info_text"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="Loading..."
        android:textColor="#6C63FF" android:textSize="12sp"
        android:fontFamily="monospace"
        android:background="#1A1A2E" android:padding="10dp"
        android:layout_marginBottom="8dp" />

    <TextView android:id="@+id/status_text"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="Waiting..."
        android:textColor="@color/text_secondary" android:textSize="14sp"
        android:layout_marginBottom="20dp" />

    <Button android:id="@+id/show_qr_btn"
        android:layout_width="match_parent" android:layout_height="64dp"
        android:text="Show My QR Code"
        android:textSize="16sp" android:textStyle="bold"
        android:textColor="@color/white"
        android:background="@drawable/btn_primary"
        android:layout_marginBottom="12dp" />

    <Button android:id="@+id/scan_qr_btn"
        android:layout_width="match_parent" android:layout_height="64dp"
        android:text="Scan Other Phone QR"
        android:textSize="16sp" android:textStyle="bold"
        android:textColor="@color/white"
        android:background="@drawable/btn_secondary"
        android:layout_marginBottom="12dp" />

    <Button android:id="@+id/disconnect_btn"
        android:layout_width="match_parent" android:layout_height="52dp"
        android:text="Disconnect"
        android:textColor="@color/white"
        android:background="@drawable/btn_danger" />

    <TextView android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="দুটো ফোন একই WiFi তে রাখুন। একটায় QR দেখাও, অন্যটায় Scan করো।"
        android:textColor="#606080" android:textSize="13sp"
        android:layout_marginTop="24dp" android:gravity="center" />
</LinearLayout>
XMLEOF
echo "[4/7] fragment_devices.xml done"

# 5. WiFi Direct ফাইল মুছে দাও
rm -f app/src/main/java/com/elephantcos/soundsync/wifi/WifiDirectManager.java
echo "[5/7] Old WifiDirectManager removed"

# 6. AndroidManifest fix (package attr ছাড়া, CAMERA আছে)
cat > app/src/main/AndroidManifest.xml << 'XMLEOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
XMLEOF
echo "[6/7] AndroidManifest.xml done"

# 7. Push
git add .
git commit -m "refactor: TCP socket + QR connect, remove WiFi Direct"
git push

echo ""
echo "✅ সব শেষ! GitHub Actions এ build হবে।"
