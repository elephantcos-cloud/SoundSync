package com.elephantcos.soundsync.fragment;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.elephantcos.soundsync.MainActivity;
import com.elephantcos.soundsync.R;
import com.elephantcos.soundsync.file.FileTransferManager;
import java.io.*;

public class FilesFragment extends Fragment {

    private TextView    statusText;
    private ProgressBar progress;
    private FileTransferManager transferMgr;
    private String hostAddr = "";
    private ActivityResultLauncher<Intent> picker;

    @Override public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) sendUri(uri);
            }
        });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup cont, @Nullable Bundle saved) {
        View v = inf.inflate(R.layout.fragment_files, cont, false);
        statusText = v.findViewById(R.id.file_status);
        progress   = v.findViewById(R.id.file_progress);
        transferMgr = new FileTransferManager();

        if (getActivity() instanceof MainActivity)
            hostAddr = ((MainActivity) getActivity()).getGroupOwnerAddress();

        Button sendBtn = v.findViewById(R.id.send_file_btn);
        Button recvBtn = v.findViewById(R.id.receive_file_btn);

        if (hostAddr.isEmpty()) {
            statusText.setText("Connect to a device first");
            sendBtn.setEnabled(false); recvBtn.setEnabled(false);
        }

        sendBtn.setOnClickListener(x -> { Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("*/*"); picker.launch(i); });
        recvBtn.setOnClickListener(x -> startReceive());
        return v;
    }

    private void sendUri(Uri uri) {
        try {
            File f = uriToFile(uri);
            if (f == null) { statusText.setText("Cannot read file"); return; }
            statusText.setText("Sending: " + f.getName());
            progress.setProgress(0); progress.setVisibility(View.VISIBLE);
            transferMgr.sendFile(hostAddr, f, makeListener("Sent!"));
        } catch (Exception e) { statusText.setText("Error: " + e.getMessage()); }
    }

    private void startReceive() {
        String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        statusText.setText("⏳ Waiting for file…");
        progress.setProgress(0); progress.setVisibility(View.VISIBLE);
        transferMgr.receiveFile(dir, makeListener("Received!"));
    }

    private FileTransferManager.TransferListener makeListener(String doneMsg) {
        return new FileTransferManager.TransferListener() {
            @Override public void onProgress(int p) { ui(() -> progress.setProgress(p)); }
            @Override public void onComplete(String path) {
                ui(() -> { statusText.setText(doneMsg + " " + new File(path).getName()); progress.setVisibility(View.GONE); });
            }
            @Override public void onError(String msg) {
                ui(() -> { statusText.setText("⚠ " + msg); progress.setVisibility(View.GONE); });
            }
        };
    }

    private File uriToFile(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return null;
            String name = "received_file";
            Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
                c.close();
            }
            File tmp = new File(requireContext().getCacheDir(), name);
            FileOutputStream fos = new FileOutputStream(tmp);
            byte[] buf = new byte[4096]; int r;
            while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
            fos.close(); is.close();
            return tmp;
        } catch (Exception e) { return null; }
    }

    private void ui(Runnable r) { if (getActivity() != null) getActivity().runOnUiThread(r); }
}
