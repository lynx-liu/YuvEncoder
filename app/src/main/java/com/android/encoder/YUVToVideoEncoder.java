package com.android.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class YUVToVideoEncoder {

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 864;
    private static final int BIT_RATE = 5*1000*1000; // 比特率
    private static final int MAX_FPS = 60; // 最大帧率
    private static final int DEFAULT_QP = 32;
    private static final int MIN_QP = 0;
    private static final int MAX_QP = 34;

    public void encodeYUVtoVideo(String yuvFilePath, String outputFile) {
        MediaFormat format = createFormat(WIDTH, HEIGHT, BIT_RATE, MAX_FPS, MIME_TYPE, DEFAULT_QP, MAX_QP, MIN_QP);
        try {
            MediaCodec codec = MediaCodec.createEncoderByType(MIME_TYPE);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();

            File file = new File(outputFile);
            FileOutputStream fos = new FileOutputStream(file);

            int frameSize = WIDTH*HEIGHT*3/2;

            // 分批次读取YUV文件
            FileInputStream fis = new FileInputStream(yuvFilePath);
            byte[] data = new byte[frameSize];

            long frameInterval = 1000 / MAX_FPS;

            int size = 0;
            while ((size = fis.read(data)) != -1) {
                long startTime = System.currentTimeMillis();

                int inputBufferIndex = codec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();

                    inputBuffer.put(data, 0, size);
                    codec.queueInputBuffer(inputBufferIndex, 0, size, System.nanoTime() / 1000, 0);
                }

                ByteBuffer[] outputBuffers = codec.getOutputBuffers();
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                    byte[] encodedData = new byte[bufferInfo.size];
                    outputBuffer.get(encodedData, bufferInfo.offset, bufferInfo.size);
                    fos.write(encodedData);

                    codec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
                }

                long useTime = System.currentTimeMillis()-startTime;
                if(useTime<frameInterval) try {
                    Thread.sleep(useTime);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            fis.close();
            fos.close();
            codec.stop();
            codec.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getProperty(final String key, final String defaultValue) {
        String value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String)(get.invoke(c, key, defaultValue ));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private static MediaFormat createFormat(int width, int height, int bitRate, int maxFps,
                                            String mimeType, int defaultQP, int maxQP, int minQP) {
        // 你提供的createFormat方法
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mimeType);
        format.setInteger(MediaFormat.KEY_WIDTH, width);
        format.setInteger(MediaFormat.KEY_HEIGHT, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFps);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10000*maxFps);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        format.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, maxFps * 30);

        format.setInteger("vendor.qti-ext-enc-blurinfo.info", 2);
        if (defaultQP != 0 || minQP != 0 || maxQP != 0) {
            String model = getProperty("ro.soc.model", "QCS865");
            if (model.contains("QCS8550")) {
                format.setInteger(MediaFormat.KEY_VIDEO_QP_MIN, minQP);
                format.setInteger(MediaFormat.KEY_VIDEO_QP_MAX, maxQP);
            } else {
                format.setInteger("vendor.qti-ext-enc-blurinfo.info", 2);

                format.setInteger("vendor.qti-ext-enc-initial-qp.qp-i", defaultQP);
                format.setInteger("vendor.qti-ext-enc-initial-qp.qp-i-enable", 1);
                format.setInteger("vendor.qti-ext-enc-qp-range.qp-i-min", minQP);
                format.setInteger("vendor.qti-ext-enc-qp-range.qp-i-max", maxQP);

                format.setInteger("vendor.qti-ext-enc-initial-qp.qp-p", defaultQP);
                format.setInteger("vendor.qti-ext-enc-initial-qp.qp-p-enable", 1);
                format.setInteger("vendor.qti-ext-enc-qp-range.qp-p-min", minQP);
                format.setInteger("vendor.qti-ext-enc-qp-range.qp-p-max", maxQP);
            }
        }

        if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        } else if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
            format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, maxFps);
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
        }
        return format;
    }
}