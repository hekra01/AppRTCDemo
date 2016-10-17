/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import org.appspot.apprtc.R;
import org.appspot.apprtc.util.Utils;

import java.io.IOException;
import java.util.List;

/**
 * An implementation of VideoCapturer to capture the screen content as a video stream.
 * Capturing is done by {@code MediaProjection} on a {@code SurfaceTexture}. We interact with this
 * {@code SurfaceTexture} using a {@code SurfaceTextureHelper}.
 * The {@code SurfaceTextureHelper} is created by the native code and passed to this capturer in
 * {@code VideoCapturer.initialize()}. On receiving a new frame, this capturer passes it
 * as a texture to the native code via {@code CapturerObserver.onTextureFrameCaptured()}. This takes
 * place on the HandlerThread of the given {@code SurfaceTextureHelper}. When done with each frame,
 * the native code returns the buffer to the  {@code SurfaceTextureHelper} to be used for new
 * frames. At any time, at most one frame is being processed.
 */
@TargetApi(21)
public class ScreenCapturerAndroid
        implements VideoCapturer, SurfaceTextureHelper.OnTextureFrameAvailableListener {
    private static final int DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    // DPI for VirtualDisplay, does not seem to matter for us.
    private static final int VIRTUAL_DISPLAY_DPI = 400;
    public static final String TAG = "ScreenCapturerAndroid";
    private static final String MIMETYPE = "video/avc";
    private static final boolean VENC = false;

    private Intent mediaProjectionPermissionResultData;
    private final MediaProjection.Callback mediaProjectionCallback;

    private int width;
    private int height;
    private VirtualDisplay virtualDisplay;
    private SurfaceTextureHelper surfaceTextureHelper;
    private CapturerObserver capturerObserver;
    private long numCapturedFrames = 0;
    private MediaProjection mediaProjection;
    private boolean isDisposed = false;
    private MediaProjectionManager mediaProjectionManager;
    private Context context;
    private MediaCodec videoEncoder;
    private Handler surfaceThread;

    /**
     * Constructs a new Screen Capturer.
     *
     * @param mediaProjectionPermissionResultData the result data of MediaProjection permission
     *     activity; the calling app must validate that result code is Activity.RESULT_OK before
     *     calling this method.
     * @param mediaProjectionCallback MediaProjection callback to implement application specific
     *     logic in events such as when the user revokes a previously granted capture permission.
     **/
    public ScreenCapturerAndroid(Intent mediaProjectionPermissionResultData,
                                 MediaProjection.Callback mediaProjectionCallback) {
        this.mediaProjectionPermissionResultData = mediaProjectionPermissionResultData;
        this.mediaProjectionCallback = mediaProjectionCallback;
    }

    private void checkNotDisposed() {
        if (isDisposed) {
            throw new RuntimeException("capturer is disposed.");
        }
    }

    @Override
    public List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats() {
        return null;
    }

    @Override
    public synchronized void initialize(final SurfaceTextureHelper surfaceTextureHelper,
                                        final Context applicationContext, final VideoCapturer.CapturerObserver capturerObserver) {
        Log.d(TAG, "initialize");
        checkNotDisposed();

        if (capturerObserver == null) {
            throw new RuntimeException("capturerObserver not set.");
        }
        this.capturerObserver = capturerObserver;

        if (surfaceTextureHelper == null) {
            throw new RuntimeException("surfaceTextureHelper not set.");
        }
        this.surfaceTextureHelper = surfaceTextureHelper;

        mediaProjectionManager = (MediaProjectionManager) applicationContext.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        mediaProjectionPermissionResultData = mediaProjectionManager.createScreenCaptureIntent();
        this.context = applicationContext;
        this.surfaceThread = surfaceTextureHelper == null?null:surfaceTextureHelper.getHandler();
    }

    @Override
    public synchronized void startCapture(
            final int width, final int height, final int ignoredFramerate) {
        checkNotDisposed();
        Log.d(TAG, "startCapture w " + width +  " h " + height + " ignoredFramerate " + ignoredFramerate);
        this.width = width;
        this.height = height;

        if (VENC) {
            try {
                this.videoEncoder = createVideoEncoder();
            }
            catch (IOException e) {
                throw new Error(e);
            }
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(
                Activity.RESULT_OK, mediaProjectionPermissionResultData);

        // Let MediaProjection callback use the SurfaceTextureHelper thread.
        mediaProjection.registerCallback(mediaProjectionCallback, surfaceTextureHelper.getHandler());

        createVirtualDisplay();
        capturerObserver.onCapturerStarted(true);
        surfaceTextureHelper.startListening(ScreenCapturerAndroid.this);
    }

    @Override
    public synchronized void stopCapture() {
        checkNotDisposed();
        Log.d(TAG, "stopCapture");
        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper.getHandler(), new Runnable() {
            @Override
            public void run() {
                surfaceTextureHelper.stopListening();
                capturerObserver.onCapturerStopped();

                if (VENC) {
                    if (videoEncoder != null) {
                        videoEncoder.stop();
                        videoEncoder.release();
                        videoEncoder = null;
                    }
                }

                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }

                if (mediaProjection != null) {
                    // Unregister the callback before stopping, otherwise the callback recursively
                    // calls this method.
                    mediaProjection.unregisterCallback(mediaProjectionCallback);
                    mediaProjection.stop();
                    mediaProjection = null;
                }
            }
        });
    }

    @Override
    public synchronized void onOutputFormatRequest(
            final int width, final int height, final int framerate) {
        checkNotDisposed();
        surfaceTextureHelper.getHandler().post(new Runnable() {
            @Override
            public void run() {
                capturerObserver.onOutputFormatRequest(width, height, framerate);
            }
        });
    }

    @Override
    public synchronized void dispose() {
        isDisposed = true;
    }

    /**
     * Changes output video format. This method can be used to scale the output
     * video, or to change orientation when the captured screen is rotated for example.
     *
     * @param width new output video width
     * @param height new output video height
     * @param ignoredFramerate ignored
     */
    @Override
    public synchronized void changeCaptureFormat(
            final int width, final int height, final int ignoredFramerate) {
        checkNotDisposed();

        this.width = width;
        this.height = height;

        if (virtualDisplay == null) {
            // Capturer is stopped, the virtual display will be created in startCaptuer().
            return;
        }

        // Create a new virtual display on the surfaceTextureHelper thread to avoid interference
        // with frame processing, which happens on the same thread (we serialize events by running
        // them on the same thread).
        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper.getHandler(), new Runnable() {
            @Override
            public void run() {
                virtualDisplay.release();
                createVirtualDisplay();
            }
        });
    }

    @TODO (msg = "Later check if input surface correct")
    private void createVirtualDisplay() {
        VirtualDisplay.Callback callback = new VirtualDisplay.Callback() {
            @Override
            public void onPaused() {
                Log.d(TAG,">>Capturer.onPaused");
            }

            @Override
            public void onStopped() {
                Log.d(TAG,">>Capturer.onStopped");
            }

            @Override
            public void onResumed() {
                Log.d(TAG,">>Capturer.onResumed");
            }
        };

        surfaceTextureHelper.getSurfaceTexture().setDefaultBufferSize(width, height);
        if (VENC) {
            virtualDisplay = mediaProjection.createVirtualDisplay("WebRTC_ScreenCapture", width, height,
                    VIRTUAL_DISPLAY_DPI, DISPLAY_FLAGS, videoEncoder.createInputSurface() /*new Surface (surfaceTextureHelper.getSurfaceTexture())*/,
                    callback /* callback */, null /* callback handler */);
            videoEncoder.start();
        }
        else {
            virtualDisplay = mediaProjection.createVirtualDisplay("WebRTC_ScreenCapture", width, height,
                    VIRTUAL_DISPLAY_DPI, DISPLAY_FLAGS, new Surface(surfaceTextureHelper.getSurfaceTexture()),
                    callback /* callback */, null /* callback handler */);
        }
    }

    // This is called on the internal looper thread of {@Code SurfaceTextureHelper}.
    @Override
    public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
        numCapturedFrames++;
        capturerObserver.onTextureFrameCaptured(
                width, height, oesTextureId, transformMatrix, 0 /* rotation */, timestampNs);
    }

    //@Override
    public boolean isScreencast() {
        return true;
    }

    public long getNumCapturedFrames() {
        return numCapturedFrames;
    }

    private MediaCodec createVideoEncoder() throws IOException {
        Log.i(TAG, "createVideoEncoder");
        int w = width;
        int h = height;
        int fps = context.getResources().getInteger(R.integer.fps);
        int bitrate = context.getResources().getInteger(R.integer.bitrate);
        boolean allow_soft_video_encoder = context.getResources().getBoolean(R.bool.allow_soft_video_encoder);
        String encoderName = Utils.getHWEncoder(MIMETYPE, w, h, fps, allow_soft_video_encoder);// this.metrics.widthPixels, this.metrics.heightPixels, streamConfig.getFps());
        if (encoderName == null) {
            capturerObserver.onCapturerStopped();
            throw new IOException("no H/W encoder supporting mimetype:video/avc");
        }

        MediaCodec mVideoMediaCodec = MediaCodec.createByCodecName(encoderName);// debugger.getEncoderName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIMETYPE, w, h);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mVideoMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Log.i(TAG, "initVideoMediaRecorder Ok ");
        return mVideoMediaCodec;
    }

}
