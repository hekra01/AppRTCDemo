package org.appspot.apprtc.util;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by tyazid on 11/02/16.
 */
public class Utils {

    private static final String TAG = Utils.class.getName();

    public static boolean mkFifo(String path) {
        try {
            Log.d(TAG, "#### .mkFifo set enforcing 0 ");

            Runtime.getRuntime().exec(" su -c setenforce Permissive");
            File f = new File(path);
            Log.d(TAG, "#### .mkFifo delete existing fifo " + path);
            while (f.exists()) f.delete();

            Log.d(TAG, "#### .mkFifo deleted ");

            android.system.Os.mkfifo(path, 0777);
            Log.d(TAG, "#### s.mkFifo Created!! ");

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    public static String getHWEncoder(String mimeType, int w, int h, int fps, boolean softEncoderAllowed) {
        Log.d(TAG, "##   Utils.getHWEncoder looking for HW encoder for mime type =" + mimeType + " size =" + w + "x" + h + ", fps=" + fps + ", Soft-Encoder-Allowed = " + softEncoderAllowed);

        int numCodecs = MediaCodecList.getCodecCount();

        List<MediaCodecInfo> availables = new ArrayList<>();
        boolean soft;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            soft = codecInfo.getName().contains("google");
            if (codecInfo.isEncoder() && (softEncoderAllowed || !soft))//soft
            {

                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++){
                    if (types[j].equalsIgnoreCase(mimeType)) {
                         Log.d(TAG, "\n## Utils.getHWEncoder   codec : " + codecInfo.getName() + " supports  " + mimeType);
                        if (!soft)
                            availables.add(0, codecInfo);
                        else availables.add(codecInfo);
                        Log.d(TAG, "## encoder " + codecInfo.getName() + " pushed at pos = " + (!soft ? 0 : availables.size() - 1));
                        break;
                    }
                }
                    //h/w are pushed at the 1st pos
            }
        }
        Log.d(TAG, "## Encoders: ");
        for(MediaCodecInfo codecInfo:availables) {
            Log.d(TAG, "### encoder " + codecInfo.getName());
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
            if (capabilities != null) {
                Log.d(TAG, "\n## Having video capabilities  ");

                MediaCodecInfo.VideoCapabilities vc = capabilities.getVideoCapabilities();
                Log.d(TAG, "Utils.getHWEncoder SUP W :" + vc.getSupportedWidths().getLower() + " -- " + vc.getSupportedWidths().getUpper());
                Log.d(TAG, "Utils.getHWEncoder SUP H :" + vc.getSupportedHeights().getLower() + " -- " + vc.getSupportedHeights().getUpper());

                if (vc != null && vc.areSizeAndRateSupported(w, h, fps)) {
                    Log.d(TAG, " ********* FOUND HW.ENCODER(MimeType=\"" + mimeType + "\"; Name={" + codecInfo.getName() + "} :");
                    return codecInfo.getName();
                }
            }
        }
        return null;
    }




    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        byte[] addr =inetAddress. getAddress();
                        final long pumpeIPAddress =
                                ((addr [3] & 0xFFl) << (3*8)) |
                                        ((addr [2] & 0xFFl) << (2*8)) |
                                        ((addr [1] & 0xFFl) << (1*8)) |
                                        (addr [0] &  0xFFl);
                        String ip =   android.text.format.Formatter.formatIpAddress((int)pumpeIPAddress) ;
                        return ip;
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
