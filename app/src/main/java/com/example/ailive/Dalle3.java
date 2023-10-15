package com.example.ailive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.ailive.live2d.BackgroundImageListener;
import com.example.ailive.live2d.LAppDelegate;

import java.io.File;
import java.io.FileFilter;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Dalle3 {
    Context context;

    private BackgroundImageListener listener;
    public Dalle3(Context context) {
        this.context = context;
    }

    public void setBackgroundImageListener(BackgroundImageListener listener) {
        this.listener = listener;
    }

    public void setupAutoImageSwitching() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Bitmap newImage = randomSwitchImage();
                if (listener != null) {
                    listener.onNewBackgroundImage(newImage);
                }
            }
        }, 0, 10000);
    }
    private Bitmap randomSwitchImage() {

        File directory = context.getExternalFilesDir(null);

        // 获取PNG文件列表
        File[] files = directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().toLowerCase(Locale.getDefault()).endsWith(".png");
            }
        });

        // 随机选择一个文件
        int randomIndex = (int) (Math.random() * files.length);
        File selectedFile = files[randomIndex];
        Log.i("zhouzihong", selectedFile.getAbsolutePath());
        // 将文件转换为Bitmap
        Bitmap bitmap = BitmapFactory.decodeFile(selectedFile.getAbsolutePath());
        return bitmap;
    }
}
