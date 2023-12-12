package com.example.ailive;

import android.content.Context;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;

import com.example.ailive.token.AccessToken;

public class TTS {
    private Thread consumerThread;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final OkHttpClient client = new OkHttpClient();
    private String APPKEY = "8Xu8cELorObhV2cQ";  // 请替换为您的appkey
    AccessToken accessToken = AccessToken.getInstance();
    private Context context;
    private MediaPlayer mediaPlayer;
    private final BlockingQueue<File> audioQueue = new LinkedBlockingQueue<>();

    public TTS(Context context) {
        this.context = context;
        File cacheDir = context.getCacheDir();
        if (consumerThread == null) {
            consumerThread = new Thread(this::consume);
            consumerThread.start();
        }
        // 启动播放线程
        new Thread(this::playAudioLoop).start();
    }

    private void consume() {
        while (true) {
            try {
                String segmentText = queue.take();  // 阻塞，直到有可用的segmentText
                processSegmentText(segmentText);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // 恢复中断标志
            }
        }
    }

    private void processSegmentText(String text) {
        String url = buildRequestUrl(text);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.e("TTS", "Request failed with status code: " + response.code());
                return;
            }

            // 保存响应数据到文件
            File audioFile = saveResponseToFile(response);
            if (audioFile != null) {
                // 将文件添加到音频队列
                audioQueue.put(audioFile);
            }
        } catch (Exception e) {
            Log.e("TTS", "Network request or streaming to file failed", e);
        }
    }

    private File saveResponseToFile(Response response) {
        long timestamp = System.currentTimeMillis();
        File audioSaveFile = new File(context.getCacheDir(), "audio_" + timestamp + ".mp3");

        try {
            byte[] bytes = response.body().bytes(); // 直接读取响应体的所有字节
            FileOutputStream fos = new FileOutputStream(audioSaveFile);
            fos.write(bytes);
            fos.close();
            return audioSaveFile;
        } catch (IOException e) {
            Log.e("TTS", "Failed to save audio file", e);
            return null;
        }
    }

    private String buildRequestUrl(String text) {
        String url = "https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts";
        url += "?appkey=" + APPKEY;
        url += "&token=" + accessToken.getToken();
        url += "&text=" + text;
        url += "&format=mp3";
        url += "&sample_rate=16000";
        url += "&voice=aibao";
        url += "&volume=100";
        url += "&speech_rate=200";
        url += "&pitch_rate=0";
        return url;
    }

    public void produce(String segmentText) {
        try {
            queue.put(segmentText);  // 将segmentText添加到队列中
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 恢复中断标志
        }
    }

    // 持续播放音频的线程
    private void playAudioLoop() {
        while (true) {
            try {
                // 如果有音频正在播放，等待音频播放完毕
                while (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    Thread.sleep(100); // 等待100ms再次检查
                }

                // 从阻塞队列中取出音频文件
                File audioFile = audioQueue.take();
                playAudio(audioFile);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void onStop() {
        // 停止并释放MediaPlayer
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // 清空队列
        queue.clear();
        audioQueue.clear();

    }



    private void playAudio(File audioFile) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 当音频播放完成后，您可以根据需要添加额外的逻辑
                }
            });
        } catch (IOException e) {
            Log.e("TTS", "Error playing audio", e);
        }
    }
}
