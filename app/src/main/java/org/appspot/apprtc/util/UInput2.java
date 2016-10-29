package org.appspot.apprtc.util;

/**
 * Created by hekra on 24/10/2016.
 */

import java.io.File;
import java.io.IOException;

public class UInput2 {
    private static final UInput2 INSTANCE = new UInput2();
    private Process su;

    private UInput2() {
    }

    public static UInput2 getInstance() {
        return INSTANCE;
    }

    public void sendCmd(String cmd) throws IOException {
        try {
            if (su == null) {
                ProcessBuilder pb = new ProcessBuilder();//
                System.out.println(" FFFF : " + new File("/sdcard/Download/totobin2").exists());
                su = pb.command("sh", "/sdcard/Download/totobin2").redirectErrorStream(false).start();
            }
            System.out.println("#### KeyInj.sendCmd CM = [" + cmd + "]");
            su.getOutputStream().write((cmd).getBytes());
            su.getOutputStream().flush();
        }
        catch (IOException e) {
            if (su != null)
                su.destroy();
            throw e;
        }
    }
}
