package com.android.encoder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO}, 0);
            }
        }

        YUVToVideoEncoder yuvToVideoEncoder =new YUVToVideoEncoder();
        new Thread(() -> {
            yuvToVideoEncoder.encodeYUVtoVideo("/sdcard/yuv420sp.yuv", "/sdcard/video.h265");
            runOnUiThread(() -> {
                TextView tvNotify = findViewById(R.id.tv_notify);
                tvNotify.setText(R.string.finish_encode);
            });
        }).start();
    }
}
