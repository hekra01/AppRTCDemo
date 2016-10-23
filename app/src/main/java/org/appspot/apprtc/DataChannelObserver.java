package org.appspot.apprtc;

import android.util.Log;

import org.webrtc.DataChannel;

import java.nio.ByteBuffer;

/**
 * Created by hekra on 23/10/2016.
 */

public abstract class DataChannelObserver  implements DataChannel.Observer{
    private static final String TAG = "DataChannelObserver";
    private final DataChannel dc;

    public DataChannelObserver(DataChannel dc){
        this.dc = dc;
    }

    public void onBufferedAmountChange(long var1){
        Log.d(TAG,
                "Data channel buffered amount changed: " + dc.label() + ": " + dc.state());
    }

    @Override
    public void onStateChange() {
        Log.d(TAG,
                "Data channel state changed: " + dc.label() + ": " + dc.state());
    }

    @Override
    public void onMessage(final DataChannel.Buffer buffer) {
        if (buffer.binary) {
            Log.d(TAG, "Received binary msg over " + dc);
            return;
        }
        ByteBuffer data = buffer.data;
        final byte[] bytes = new byte[ data.capacity() ];
        data.get(bytes);
        String strData = new String( bytes );
        processMessage(strData);
    }

    protected abstract void  processMessage(String msg);
}
