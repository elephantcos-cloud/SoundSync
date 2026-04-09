package com.elephantcos.soundsync.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.elephantcos.soundsync.MainActivity;
import com.elephantcos.soundsync.R;
import com.elephantcos.soundsync.audio.AudioStreamer;

public class StreamFragment extends Fragment {

    private Button    startBtn, stopBtn;
    private TextView  statusText;
    private AudioStreamer streamer;
    private boolean   isHost    = false;
    private String    hostAddr  = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup cont, @Nullable Bundle saved) {
        View v = inf.inflate(R.layout.fragment_stream, cont, false);
        startBtn   = v.findViewById(R.id.start_stream_btn);
        stopBtn    = v.findViewById(R.id.stop_stream_btn);
        statusText = v.findViewById(R.id.stream_status);

        if (getActivity() instanceof MainActivity) {
            MainActivity m = (MainActivity) getActivity();
            isHost   = m.isGroupOwner();
            hostAddr = m.getGroupOwnerAddress();
        }

        streamer = new AudioStreamer(new AudioStreamer.StatusListener() {
            @Override public void onClientConnected() { ui(() -> statusText.setText("🎙 Client connected — streaming!")); }
            @Override public void onStreamStopped()   { ui(() -> { statusText.setText("Stopped"); startBtn.setEnabled(true); stopBtn.setEnabled(false); }); }
            @Override public void onError(String msg) { ui(() -> statusText.setText("⚠ " + msg)); }
        });

        stopBtn.setEnabled(false);
        startBtn.setOnClickListener(x -> startStream());
        stopBtn .setOnClickListener(x -> { streamer.stop(); });

        if (hostAddr.isEmpty()) {
            statusText.setText("Connect to a device first (Devices tab)");
            startBtn.setEnabled(false);
        } else {
            statusText.setText(isHost ? "You are Host — tap Start to stream mic" : "You are Client — tap Start to listen");
        }
        return v;
    }

    private void startStream() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 200);
            return;
        }
        startBtn.setEnabled(false);
        stopBtn .setEnabled(true);
        if (isHost) {
            statusText.setText("⏳ Waiting for client…");
            streamer.startServer();
        } else {
            statusText.setText("⏳ Connecting to host…");
            streamer.startClient(hostAddr);
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (streamer != null) streamer.stop();
    }

    private void ui(Runnable r) { if (getActivity() != null) getActivity().runOnUiThread(r); }
}
