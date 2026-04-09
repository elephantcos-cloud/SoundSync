package com.elephantcos.soundsync.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
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
import com.elephantcos.soundsync.MainActivity;
import com.elephantcos.soundsync.R;
import com.elephantcos.soundsync.adapter.DeviceAdapter;
import com.elephantcos.soundsync.model.DeviceItem;
import com.elephantcos.soundsync.wifi.WifiDirectManager;
import java.util.ArrayList;
import java.util.List;

public class DevicesFragment extends Fragment implements WifiDirectManager.Listener {

    private WifiDirectManager wifiManager;
    private DeviceAdapter adapter;
    private final List<DeviceItem>   deviceList = new ArrayList<>();
    private final List<WifiP2pDevice> p2pList   = new ArrayList<>();
    private TextView statusText;
    private static final int PERM_CODE = 100;

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
        return v;
    }

    @Override public void onStart() {
        super.onStart();
        if (getActivity() != null) {
            wifiManager = new WifiDirectManager(getActivity(), this);
            getActivity().registerReceiver(wifiManager.getReceiver(), wifiManager.getIntentFilter());
        }
    }

    @Override public void onStop() {
        super.onStop();
        if (getActivity() != null && wifiManager != null) {
            try { getActivity().unregisterReceiver(wifiManager.getReceiver()); } catch (Exception ignored) {}
        }
    }

    private void startDiscovery() {
        if (!checkPerms()) return;
        statusText.setText("🔍 Scanning…");
        deviceList.clear(); p2pList.clear();
        adapter.refresh();
        if (wifiManager != null) wifiManager.discoverPeers();
    }

    private boolean checkPerms() {
        List<String> needed = new ArrayList<>();
        needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            needed.add(Manifest.permission.RECORD_AUDIO);

        List<String> denied = new ArrayList<>();
        for (String p : needed)
            if (ActivityCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED)
                denied.add(p);
        if (!denied.isEmpty()) {
            requestPermissions(denied.toArray(new String[0]), PERM_CODE);
            return false;
        }
        return true;
    }

    private void connectTo(DeviceItem item) {
        int idx = deviceList.indexOf(item);
        if (idx >= 0 && idx < p2pList.size()) {
            item.status = 1; adapter.refresh();
            wifiManager.connectTo(p2pList.get(idx));
        }
    }

    @Override public void onPeersChanged(List<WifiP2pDevice> peers) {
        p2pList.clear(); p2pList.addAll(peers);
        deviceList.clear();
        for (WifiP2pDevice d : peers)
            deviceList.add(new DeviceItem(
                d.deviceName.isEmpty() ? "Unknown" : d.deviceName, d.deviceAddress, 0));
        ui(() -> {
            adapter.refresh();
            statusText.setText(peers.isEmpty() ? "No devices found" : peers.size() + " device(s) found");
        });
    }

    @Override public void onConnected(String ownerAddr, boolean isOwner) {
        ui(() -> {
            statusText.setText(isOwner ? "✅ Connected (Host)" : "✅ Connected (Client)");
            for (DeviceItem d : deviceList) d.status = 2;
            adapter.refresh();
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).onConnected(ownerAddr, isOwner);
        });
    }

    @Override public void onDisconnected() {
        ui(() -> {
            statusText.setText("Disconnected");
            for (DeviceItem d : deviceList) d.status = 0;
            adapter.refresh();
        });
    }

    @Override public void onError(String msg) {
        ui(() -> {
            statusText.setText("⚠ " + msg);
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void ui(Runnable r) { if (getActivity() != null) getActivity().runOnUiThread(r); }
}
