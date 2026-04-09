package com.elephantcos.soundsync.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import java.util.ArrayList;
import java.util.List;

public class WifiDirectManager {

    public interface Listener {
        void onPeersChanged(List<WifiP2pDevice> peers);
        void onConnected(String groupOwnerAddress, boolean isGroupOwner);
        void onDisconnected();
        void onError(String message);
    }

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final BroadcastReceiver receiver;
    private final Listener listener;

    public WifiDirectManager(Context ctx, Listener listener) {
        this.listener = listener;
        manager = (WifiP2pManager) ctx.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(ctx, ctx.getMainLooper(), null);
        receiver = buildReceiver();
    }

    private BroadcastReceiver buildReceiver() {
        return new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                switch (action) {
                    case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                        manager.requestPeers(channel, list ->
                            listener.onPeersChanged(new ArrayList<>(list.getDeviceList())));
                        break;
                    case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                        NetworkInfo info = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                        if (info != null && info.isConnected()) {
                            manager.requestConnectionInfo(channel, connInfo -> {
                                if (connInfo.groupFormed) {
                                    listener.onConnected(
                                        connInfo.groupOwnerAddress.getHostAddress(),
                                        connInfo.isGroupOwner);
                                }
                            });
                        } else { listener.onDisconnected(); }
                        break;
                }
            }
        };
    }

    public IntentFilter getIntentFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        return f;
    }

    public BroadcastReceiver getReceiver() { return receiver; }

    public void discoverPeers() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {}
            @Override public void onFailure(int r) { listener.onError("Discovery failed (" + r + ")"); }
        });
    }

    public void connectTo(WifiP2pDevice device) {
        WifiP2pConfig cfg = new WifiP2pConfig();
        cfg.deviceAddress = device.deviceAddress;
        manager.connect(channel, cfg, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {}
            @Override public void onFailure(int r) { listener.onError("Connect failed (" + r + ")"); }
        });
    }

    public void disconnect() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { listener.onDisconnected(); }
            @Override public void onFailure(int r) {}
        });
    }
}
