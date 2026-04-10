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
