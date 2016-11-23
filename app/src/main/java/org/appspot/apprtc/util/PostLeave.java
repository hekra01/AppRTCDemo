package org.appspot.apprtc.util;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * Created by hekra on 22/11/2016.
 */

public class PostLeave {
    private enum MessageType { MESSAGE, LEAVE }
    private  static  final String TAG = "POST_LEAVE";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private static void sendPostMessageStatic(
            final MessageType messageType, final String url, final String message, boolean sync) {
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        System.out.println(TAG + "C->GAE: " + logInfo);

        final boolean[] syncWait = sync ? new boolean[]{true} : null;

        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("POST", url, message, new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        notifyLock(syncWait);
                        Thread.dumpStack();
                        System.out.println("##############ERROR " + errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        notifyLock(syncWait);

                        if (messageType == MessageType.MESSAGE) {
                            try {
                                JSONObject roomJson = new JSONObject(response);
                                String result = roomJson.getString("result");
                                System.out.println("############## GAE POST result: " + result);
                                if (!result.equals("SUCCESS")) {
                                    Thread.dumpStack();
                                    System.out.println("##############ERROR GAE POST error: " + result);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                System.out.println("##############ERROR GAE POST JSON error: " + e.toString());
                            }
                        }
                    }
                });
        httpConnection.send();
        waitLock(syncWait);
    }
    private static void sendPostMessageStatic(
            final MessageType messageType, final String url, final String message) {
        sendPostMessageStatic(messageType, url, message, false);
    }

    public static void main(String[] args) {
        String roomUrl ="http://10.60.61.27:8084";
        String roomId="shuttle-instance-4";
        String clientId="54872117";
        String previousLeave = roomUrl+ "/" + ROOM_LEAVE + "/" + roomId + "/"
                + clientId;
        sendPostMessageStatic(MessageType.LEAVE, previousLeave, null);
    }

    private static void waitLock(boolean[] syncWait) {
        if (syncWait != null){
            synchronized (syncWait) {
                if (syncWait[0])
                    try {
                        syncWait.wait();
                    }
                    catch (InterruptedException e) {}
            }
        }
    }

    private static void notifyLock(boolean[] syncWait) {
        if (syncWait != null){
            synchronized (syncWait) {
                syncWait.notify();
                syncWait[0] = false;
            }
        }
    }

}
