package com.elephantcos.soundsync.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class AudioStreamer {

    public static final int PORT = 8888;
    private static final int SAMPLE_RATE   = 44100;
    private static final int IN_CHANNEL    = AudioFormat.CHANNEL_IN_MONO;
    private static final int OUT_CHANNEL   = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING      = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE   =
        AudioRecord.getMinBufferSize(SAMPLE_RATE, IN_CHANNEL, ENCODING) * 2;

    public interface StatusListener {
        void onClientConnected();
        void onStreamStopped();
        void onError(String msg);
    }

    private volatile boolean streaming = false;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private AudioRecord recorder;
    private AudioTrack   player;
    private final StatusListener listener;

    public AudioStreamer(StatusListener listener) { this.listener = listener; }

    /** Host: capture mic → stream to one client */
    public void startServer() {
        streaming = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Socket socket = serverSocket.accept();
                if (listener != null) listener.onClientConnected();

                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, IN_CHANNEL, ENCODING, BUFFER_SIZE);
                recorder.startRecording();

                OutputStream out = socket.getOutputStream();
                byte[] buf = new byte[BUFFER_SIZE];
                while (streaming) {
                    int read = recorder.read(buf, 0, buf.length);
                    if (read > 0) { out.write(buf, 0, read); out.flush(); }
                }
                recorder.stop(); recorder.release();
                socket.close(); serverSocket.close();
            } catch (IOException e) {
                if (streaming && listener != null) listener.onError("Server: " + e.getMessage());
            }
            if (listener != null) listener.onStreamStopped();
        }).start();
    }

    /** Client: receive audio from host → play */
    public void startClient(String serverIp) {
        streaming = true;
        new Thread(() -> {
            try {
                clientSocket = new Socket(serverIp, PORT);
                int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, OUT_CHANNEL, ENCODING);
                player = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    OUT_CHANNEL, ENCODING, minBuf, AudioTrack.MODE_STREAM);
                player.play();

                InputStream in = clientSocket.getInputStream();
                byte[] buf = new byte[BUFFER_SIZE];
                while (streaming) {
                    int read = in.read(buf);
                    if (read > 0) player.write(buf, 0, read);
                }
                player.stop(); player.release();
                clientSocket.close();
            } catch (IOException e) {
                if (streaming && listener != null) listener.onError("Client: " + e.getMessage());
            }
            if (listener != null) listener.onStreamStopped();
        }).start();
    }

    public void stop() {
        streaming = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
    }

    public boolean isActive() { return streaming; }
}
