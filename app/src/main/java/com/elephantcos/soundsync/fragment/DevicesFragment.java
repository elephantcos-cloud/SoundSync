package com.elephantcos.soundsync.fragment;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import com.elephantcos.soundsync.MainActivity;
import com.elephantcos.soundsync.R;
import com.elephantcos.soundsync.adapter.DeviceAdapter;
import com.elephantcos.soundsync.model.DeviceItem;
import com.elephantcos.soundsync.wifi.WifiDirectManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.List;

public class DevicesFragment extends Fragment implements WifiDirectManager.Listener {

    private WifiDirectManager wifiManager;
    private DeviceAdapter adapter;
    private final List<DeviceItem>    deviceList = new ArrayList<>();
    private final List<WifiP2pDevice> p2pList    = new ArrayList<>();
    private TextView statusText;
    private String myDeviceMac  = "";
    private String myDeviceName = "";
    private String pendingConnectMac = "";
    private ActivityResultLauncher<ScanOptions> scanLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                String raw = result.getContents();
                if (raw.startsWith("SOUNDSYNC:")) {
                    pendingConnectMac = raw.substring(10).trim();
                    statusText.setText("QR scanned! Searching device...");
                    startDiscoveryForQr();
                } else {
                    Toast.makeText(getContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup cont, @Nullable Bundle saved) {
        View v = inf.inflate(R.layout.fragment_devices, cont, false);
        statusText = v.findViewById(R.id.status_text);
        RecyclerView rv = v.findViewById(R.id.device_list);
        adapter = new DeviceAdapter(deviceList, item -> connectTo(item));
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        v.findViewById(R.id.scan_btn).setOnClickListener(x -> startDiscovery());
        v.findViewById(R.id.disconnect_btn).setOnClickListener(x -> {
            if (wifiManager != null) wifiManager.disconnect();
        });
        v.findViewById(R.id.show_qr_btn).setOnClickListener(x -> showMyQr());
        v.findViewById(R.id.scan_qr_btn).setOnClickListener(x -> openQrScanner());
        return v;
    }

    @Override public void onStart() {
        super.onStart();
        if (getActivity() != null) {
            wifiManager = new WifiDirectManager(getActivity(), this);
            getActivity().registerReceiver(wifiManager.getReceiver(), wifiManager.getIntentFilter());
            wifiManager.requestMyDeviceInfo((name, mac) -> { myDeviceName = name; myDeviceMac = mac; });
        }
    }

    @Override public void onStop() {
        super.onStop();
        if (getActivity() != null && wifiManager != null)
            try { getActivity().unregisterReceiver(wifiManager.getReceiver()); } catch (Exception ignored) {}
    }

    private void startDiscovery() {
        if (!checkPerms()) return;
        pendingConnectMac = "";
        statusText.setText("Scanning...");
        deviceList.clear(); p2pList.clear(); adapter.refresh();
        if (wifiManager != null) wifiManager.discoverPeers();
    }

    private void startDiscoveryForQr() {
        if (!checkPerms()) return;
        deviceList.clear(); p2pList.clear(); adapter.refresh();
        if (wifiManager != null) wifiManager.discoverPeers();
    }

    private void showMyQr() {
        if (myDeviceMac.isEmpty()) {
            Toast.makeText(getContext(), "Device info লোড হয়নি। একটু অপেক্ষা করুন।", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap qrBitmap = generateQr("SOUNDSYNC:" + myDeviceMac, 600);
        if (qrBitmap == null) { Toast.makeText(getContext(), "QR তৈরি করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show(); return; }
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_qr);
        dialog.setCancelable(true);
        ((ImageView) dialog.findViewById(R.id.qr_image)).setImageBitmap(qrBitmap);
        ((TextView)  dialog.findViewById(R.id.qr_device_name)).setText(myDeviceName.isEmpty() ? "My Device" : myDeviceName);
        ((TextView)  dialog.findViewById(R.id.qr_mac_text)).setText(myDeviceMac);
        dialog.findViewById(R.id.qr_close_btn).setOnClickListener(x -> dialog.dismiss());
        dialog.show();
    }

    private void openQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("অন্য ফোনের QR Code স্ক্যান করুন");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private Bitmap generateQr(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (WriterException e) { return null; }
    }

    private void connectTo(DeviceItem item) {
        int idx = deviceList.indexOf(item);
        if (idx >= 0 && idx < p2pList.size()) {
            item.status = 1; adapter.refresh();
            wifiManager.connectTo(p2pList.get(idx));
        }
    }

    private boolean checkPerms() {
        List<String> needed = new ArrayList<>();
        needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        List<String> denied = new ArrayList<>();
        for (String p : needed)
            if (ActivityCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED)
                denied.add(p);
        if (!denied.isEmpty()) { requestPermissions(denied.toArray(new String[0]), 100); return false; }
        return true;
    }

    @Override public void onPeersChanged(List<WifiP2pDevice> peers) {
        p2pList.clear(); p2pList.addAll(peers);
        deviceList.clear();
        for (WifiP2pDevice d : peers)
            deviceList.add(new DeviceItem(d.deviceName.isEmpty() ? "Unknown" : d.deviceName, d.deviceAddress, 0));
        ui(() -> {
            adapter.refresh();
            if (!pendingConnectMac.isEmpty()) {
                for (int i = 0; i < p2pList.size(); i++) {
                    if (p2pList.get(i).deviceAddress.equalsIgnoreCase(pendingConnectMac)) {
                        deviceList.get(i).status = 1; adapter.refresh();
                        wifiManager.connectTo(p2pList.get(i));
                        statusText.setText("Connecting via QR...");
                        pendingConnectMac = ""; return;
                    }
                }
                statusText.setText("Device not found. Both phones nearby রাখুন।");
            } else {
                statusText.setText(peers.isEmpty() ? "No devices found" : peers.size() + " device(s) found");
            }
        });
    }

    @Override public void onConnected(String ownerAddr, boolean isOwner) {
        ui(() -> {
            statusText.setText(isOwner ? "Connected (Host)" : "Connected (Client)");
            for (DeviceItem d : deviceList) d.status = 2; adapter.refresh();
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).onConnected(ownerAddr, isOwner);
        });
    }

    @Override public void onDisconnected() {
        ui(() -> { statusText.setText("Disconnected"); for (DeviceItem d : deviceList) d.status = 0; adapter.refresh(); });
    }

    @Override public void onError(String msg) {
        ui(() -> { statusText.setText(msg); Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show(); });
    }

    private void ui(Runnable r) { if (getActivity() != null) getActivity().runOnUiThread(r); }
}
