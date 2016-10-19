package org.appspot.apprtc.util;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by tyazid on 11/02/16.
 */
public class Utils {

    private static final String TAG = Utils.class.getName();
    private static final boolean DBG = true;

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
                for (int j = 0; j < types.length; j++) {
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
        for (MediaCodecInfo codecInfo : availables) {
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
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        byte[] addr = inetAddress.getAddress();
                        final long pumpeIPAddress =
                                ((addr[3] & 0xFFl) << (3 * 8)) |
                                        ((addr[2] & 0xFFl) << (2 * 8)) |
                                        ((addr[1] & 0xFFl) << (1 * 8)) |
                                        (addr[0] & 0xFFl);
                        String ip = android.text.format.Formatter.formatIpAddress((int) pumpeIPAddress);
                        return ip;
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**************************
     * Keys
     ********************/

    private static Object inputmanager;
    private static Method injectInputEvent;
    private static Constructor key_event_ctor;
    private static Method sysClock;

    static int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    public static final int ACTION_MULTIPLE = 2;

    private static final int DEVICE_ID = 62;
    private static final int SOURCE_ID = 0x301;
    /**
     * ANDROID
     ****/
    private static final int KEYCODE_HOME = 3;
    private static final int KEYCODE_BACK = 4;
    private static final int KEYCODE_DPAD_UP = 19;
    private static final int KEYCODE_DPAD_DOWN = 20;
    private static final int KEYCODE_DPAD_LEFT = 21;
    private static final int KEYCODE_DPAD_RIGHT = 22;
    private static final int KEYCODE_CENTER = 23;
    private static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    private static final Map<String, Integer> actionMap = new HashMap<>();
    private static final Map<Integer, Integer> scanMap = new HashMap<>();

    static {
        actionMap.put("PRESSED", ACTION_DOWN);
        actionMap.put("RELEASED", ACTION_UP);
        actionMap.put("REPEATED", ACTION_MULTIPLE);
    }

    public static void main(String[] a) {
        sendKey("KPRESSED,65363,12345\nKRELEASED,65363,23456\n");
    }
    public static void sendKey(String toparse) throws IllegalStateException {
        //Pattern = "KPRESSED,65363," + keyCode + '\n'+ "KRELEASED,65363," + keyCode + '\n'
        boolean matches = Pattern.matches("KPRESSED,[0-9]+,[0-9]+\nKRELEASED,[0-9]+,[0-9]+\n", toparse);
        if(!matches)
            throw new IllegalStateException();
        Matcher m = Pattern.compile("[0-9]+").matcher(toparse);
        int i = 0, k = 0;
        while (m.find()) {
            String group = m.group();
            if (i%2 != 0){
                k = Integer.parseInt(group);
                break;
            }
            System.out.println(group);
        }
        sendKey(ACTION_DOWN, k, -1);
        sendKey(ACTION_UP, k, -1);
    }

        @SuppressWarnings({"unchecked"})
    private static void sendKey(int action, int code, int scan) throws IllegalStateException {
        try {
            initInj();
        } catch (Exception e) {
            throw new Error(e);
        }
        int flag = 0x08;
        if (action == ACTION_MULTIPLE)
            action = ACTION_DOWN;
        try {
            long now = (Long) sysClock.invoke(null);
            Object event = key_event_ctor.newInstance(now, now, action, code, 0, 0, DEVICE_ID/* VIRTUAL_KEYBOARD */,
                    scan, flag/* sys flag */, SOURCE_ID/* SOURCE_KEYBOARD */);
            if (DBG) System.out.println("-->Event = " + event);
            injectInputEvent.invoke(inputmanager, event, INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initInj() throws Exception {
        if (inputmanager != null)
            return;

        if (DBG) System.out.println(">>FacetsKeyInj.initInj");
        Class c = Class.forName("android.hardware.input.InputManager");
        if (DBG) System.out.println("info: android.hardware.input.InputManager.class=" + c);
        Class android_view_KeyEvent = Class.forName("android.view.KeyEvent");
        if (DBG) System.out.println("info: android.view.KeyEvent.class=" + android_view_KeyEvent);
        Class e = Class.forName("android.view.InputEvent");
        if (DBG) System.out.println("info: android.view.InputEvent.class=" + e);
        injectInputEvent = c.getMethod("injectInputEvent", e, int.class);
        if (DBG) System.out.println("info: injectInputEvent.method=" + injectInputEvent);
        key_event_ctor = android_view_KeyEvent.getConstructor(long.class, long.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class, int.class);
        inputmanager = c.getMethod("getInstance").invoke(null);
        INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = c.getField("INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH").getInt(null);
        if (DBG)
            System.out.println("info: INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH.field=" + INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
        sysClock = Class.forName("android.os.SystemClock").getMethod("uptimeMillis");
        if (DBG) System.out.println("<<FacetsKeyInj.initInj()");
    }
}
