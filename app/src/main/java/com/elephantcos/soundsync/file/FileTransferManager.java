package com.elephantcos.soundsync.file;

import java.io.*;
import java.net.*;

public class FileTransferManager {

    public static final int PORT = 8889;

    public interface TransferListener {
        void onProgress(int percent);
        void onComplete(String filePath);
        void onError(String msg);
    }

    public void sendFile(String serverIp, File file, TransferListener listener) {
        new Thread(() -> {
            try (Socket s = new Socket(serverIp, PORT);
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {

                dos.writeUTF(file.getName());
                dos.writeLong(file.length());

                byte[] buf = new byte[8192];
                long sent = 0; int read;
                while ((read = fis.read(buf)) != -1) {
                    dos.write(buf, 0, read);
                    sent += read;
                    if (listener != null)
                        listener.onProgress((int)((sent * 100) / file.length()));
                }
                dos.flush();
                if (listener != null) listener.onComplete(file.getAbsolutePath());

            } catch (IOException e) {
                if (listener != null) listener.onError(e.getMessage());
            }
        }).start();
    }

    public void receiveFile(String saveDir, TransferListener listener) {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT);
                 Socket s = ss.accept();
                 DataInputStream dis = new DataInputStream(s.getInputStream())) {

                String name = dis.readUTF();
                long   size = dis.readLong();
                File out = new File(saveDir, name);

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    long recv = 0; int read;
                    while (recv < size && (read = dis.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                        recv += read;
                        if (listener != null)
                            listener.onProgress((int)((recv * 100) / size));
                    }
                }
                if (listener != null) listener.onComplete(out.getAbsolutePath());

            } catch (IOException e) {
                if (listener != null) listener.onError(e.getMessage());
            }
        }).start();
    }
}
