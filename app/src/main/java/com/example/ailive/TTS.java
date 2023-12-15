package com.example.ailive;

import android.content.Context;
import android.util.Log;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
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
import com.google.gson.JsonObject;

public class TTS {
    private BlockingQueue<String> textSegmentsQueue;
    private Context context;
    private MediaPlayer audioPlayer;
    private BlockingQueue<File> audioFilesQueue;
    private final String TTS_API_ENDPOINT = "https://s1.v100.vip:11852/voice/bert-vits2";
    private final String TTS_API_KEY = "sk-Ze1UOghr5qtuAdPRB4Dd030878B441EeBe92F2699e0bA8A6";
    private File currentAudioFile;
    private Thread audioPlaybackThread;
    private Thread textPlaybackThread;

    public TTS(Context context) {
        this.context = context;
    }

    public void onStart() {
        audioPlayer = new MediaPlayer();
        audioFilesQueue = new LinkedBlockingQueue<>();
        textSegmentsQueue = new LinkedBlockingQueue<>();
        // 启动处理文本队列的线程
        textPlaybackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    try {
                        String text = textSegmentsQueue.take();
                        convertTextToSpeech(text);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        textPlaybackThread.start();

        // 启动处理音频队列的线程
        audioPlaybackThread = new Thread(new Runnable() { // 使用成员变量来创建线程
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    try {
                        if (!audioPlayer.isPlaying() && currentAudioFile == null) {
                            currentAudioFile = audioFilesQueue.take();
                            playAudioFile();
                        }
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        audioPlaybackThread.start();
    }

    private void playAudioFile() {
        try {
            audioPlayer.reset();
            audioPlayer.setDataSource(currentAudioFile.getPath());
            audioPlayer.prepare();
            audioPlayer.start();

            audioPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    currentAudioFile.delete();
                    currentAudioFile = null; // 确保文件播放完毕后将其设置为 null
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void convertTextToSpeech(String text) {
        // ...将文本转换为语音并将音频文件放入audioFilesQueue的逻辑...
        OkHttpClient client = new OkHttpClient();

        // 创建多部分请求体的媒体类型
        MediaType mediaType = MediaType.parse("multipart/form-data");

        // 构建多部分请求体
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("text", text)
                .addFormDataPart("id", "0")
                .addFormDataPart("format", "mp3")
                // 添加其他参数...
                ;

        // 构建请求
        Request request = new Request.Builder()
                .url(TTS_API_ENDPOINT)
                .post(builder.build())
                .build();

        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            // 获取输入流
            InputStream in = response.body().byteStream();

            // 创建临时文件来存储音频数据
            File tempFile = File.createTempFile("audio", ".mp3", context.getCacheDir());
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            out.close();
            in.close();

            // 将音频文件放入队列
            audioFilesQueue.put(tempFile);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void enqueueTextSegment(String segmentText) {
        try {
            textSegmentsQueue.put(segmentText);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onStop() {
        audioFilesQueue.clear();
        textSegmentsQueue.clear();
        if (audioPlayer.isPlaying()) {
            audioPlayer.stop(); // 如果在播放，先停止播放
        }
        audioPlayer.release();
        textPlaybackThread.interrupt();
        audioPlaybackThread.interrupt();
        currentAudioFile = null;
    }
}
