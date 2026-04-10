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
