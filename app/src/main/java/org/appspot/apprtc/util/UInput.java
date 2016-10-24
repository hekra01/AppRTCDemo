package org.appspot.apprtc.util;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by hekra on 23/10/2016.
 */

public class UInput {
    public static final int DST_PORT = 7770;
    private static final UInput INSTANCE = new UInput();
    private static final String TAG = "UInput";
    private Socket socket;
    private BufferedWriter writer;

    private UInput() {
    }

    public static UInput getInstance() {
        return INSTANCE;
    }

    public void close() throws IOException {
        writer.close();
        writer = null;
        socket.close();
        socket = null;
    }

    public void writecmd(String cmd) throws IOException {
        Log.d(TAG, ">>>>>>>>writeCmd: " + cmd);
        try {
            if (socket == null) {
                InetAddress addr = InetAddress.getLocalHost();
                socket = new Socket(addr, DST_PORT);
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            }
            writer.write(cmd);
            writer.newLine();
            writer.flush();
            Log.d(TAG, "<<<<<<<<<writeCmd: " + cmd);
        }
        catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }
}
