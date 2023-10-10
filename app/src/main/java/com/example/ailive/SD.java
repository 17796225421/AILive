package com.example.ailive;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

import com.example.ailive.live2d.BackgroundImageListener;
import com.example.ailive.live2d.LAppDelegate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SD {
    Context context;

    private BackgroundImageListener listener;

    public SD(Context context) {
        this.context = context;
    }

    public void setBackgroundImageListener(BackgroundImageListener listener) {
        this.listener = listener;
    }

    private class FetchImageTask implements Runnable {
        @Override
        public void run() {
            try {
                Bitmap latestImage = callSdApi();
                if (latestImage != null) {
                    updateUIWithImage(latestImage);
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap callSdApi() throws IOException, JSONException {
        String endpoint = "http://sd.fc-stable-diffusion-api.1928670300438578.cn-shenzhen.fc.devsapp.net";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS) // 设置连接超时
                .readTimeout(60, TimeUnit.SECONDS)    // 设置读取超时
                .writeTimeout(60, TimeUnit.SECONDS)   // 设置写入超时
                .build();

        JSONObject json = new JSONObject();
        // 设置基本属性
        json.put("denoising_strength", 0);
        json.put("prompt", "puppy dogs");
        json.put("negative_prompt", "");
        json.put("seed", -1);
        json.put("batch_size", 1);
        json.put("n_iter", 1);
        json.put("steps", 20);
        json.put("cfg_scale", 7);
        json.put("restore_faces", false);
        json.put("tiling", false);
        json.put("sampler_index", "Euler");
        // 设置override_settings属性
        JSONObject overrideSettings = new JSONObject();
        overrideSettings.put("sd_model_checkpoint", "wlop-any.ckpt [7331f3bc87]");
        json.put("override_settings", overrideSettings);
        // 设置script_args属性
        JSONArray scriptArgs = new JSONArray();
        scriptArgs.put(0);
        scriptArgs.put(true);
        scriptArgs.put(true);
        scriptArgs.put("LoRA");
        scriptArgs.put("dingzhenlora_v1(fa7c1732cc95)");
        scriptArgs.put(1);
        scriptArgs.put(1);
        json.put("script_args", scriptArgs);

        // 获取屏幕的宽度和高度
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);  // 使用context来获取WindowManager
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int deviceWidth = displayMetrics.widthPixels;
        int deviceHeight = displayMetrics.heightPixels;
        // 计算缩放因子
        float scaleFactor = 1f;
        if (deviceWidth > deviceHeight) {
            scaleFactor = 1000f / deviceWidth;
        } else {
            scaleFactor = 1000f / deviceHeight;
        }
        // 如果任何一个维度大于1000，重新计算宽度和高度
        if (deviceWidth > 1000 || deviceHeight > 1000) {
            deviceWidth = Math.round(deviceWidth * scaleFactor);
            deviceHeight = Math.round(deviceHeight * scaleFactor);
        }
        // 设置图片的宽度和高度
        json.put("width", deviceWidth);
        json.put("height", deviceHeight);

        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(json.toString(), mediaType);

        Request request = new Request.Builder()
                .url(endpoint + "/sdapi/v1/txt2img")
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        int statusCode = response.code();

        if (statusCode == 200) {
            JSONObject jsonResponse = new JSONObject(response.body().string());
            String base64Image = jsonResponse.getJSONArray("images").getString(0);

            byte[] decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "output_" + timeStamp + ".png";

            saveBitmapToFile(decodedByte, fileName);

            return decodedByte;
        } else {
            // 这里可以添加其他状态码的处理逻辑，例如错误处理
            Log.e("API_ERROR", "Server returned error: " + statusCode);
            return null;
        }
    }

    private void saveBitmapToFile(Bitmap bitmap, String fileName) {
        File file = new File(context.getExternalFilesDir(null), fileName);

        try {
            FileOutputStream outStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
            Log.i("zhouzihong", "Image saved at: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("zhouzihong", "Error saving image: " + e.getMessage());
        }
    }

    private void updateUIWithImage(final Bitmap bitmap) {
        if (listener != null) {
            listener.onNewBackgroundImage(bitmap);
        }
    }

    public void fetchAndSetNewBackgroundImage() {
        new Thread(new FetchImageTask()).start();
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
        }, 0, 60000);
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
