package com.example.ailive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.example.ailive.live2d.BackgroundImageListener;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Dalle3 {
    Context context;
    private static final int REQUEST_CODE_SELECT_PHOTOS = 1;

    public Map<String, Integer> getImageWeights() {
        return imageWeights;
    }

    private Map<String, Integer> imageWeights = new HashMap<>();

    private final String GPT_API_ENDPOINT = "https://chatapi.onechat.fun/v1/chat/completions";
    private final String GPT_API_KEY = "sk-Ze1UOghr5qtuAdPRB4Dd030878B441EeBe92F2699e0bA8A6";


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
        }, 0, 1 * 1000);
    }

    private Bitmap randomSwitchImage() {
        List<File> validFiles = new ArrayList<>();

        // 修改为从内部存储的 "background" 文件夹获取
        File storageDir = new File(context.getFilesDir(), "background");
        File[] files = storageDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                // 检查文件是否以 .jpg 结尾
                if (!file.getName().toLowerCase().endsWith(".jpg")) {
                    return false;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                return options.outWidth == 1024 && options.outHeight == 1024;
            }
        });

        if (files != null && files.length > 1) {
            Collections.addAll(validFiles, files);
        }

        if (validFiles.size() < 2) {
            return null;
        }

        // 根据权重创建加权列表
        List<File> weightedList = new ArrayList<>();
        for (File file : validFiles) {
            int weight = imageWeights.getOrDefault(file.getAbsolutePath(), 1);
            for (int i = 0; i < weight; i++) {
                weightedList.add(file);
            }
        }

        Collections.shuffle(weightedList); // Shuffle to get randomness

        // 选择两张图片
        Bitmap firstBitmap = BitmapFactory.decodeFile(weightedList.get(0).getAbsolutePath());
        Bitmap secondBitmap = BitmapFactory.decodeFile(weightedList.get(1).getAbsolutePath());

        // 降低这两张图片的权重
        decreaseImageWeight(weightedList.get(0).getAbsolutePath());
        decreaseImageWeight(weightedList.get(1).getAbsolutePath());


        Bitmap result = Bitmap.createBitmap(1024, 2048, firstBitmap.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(firstBitmap, 0, 0, null);
        canvas.drawBitmap(secondBitmap, 0, 1024, null);

        return result;
    }


    public String getImageGeneratePrompt() throws IOException {
        // 利用gpt根据上下文生成合成图片的prompt
        URL url = new URL(GPT_API_ENDPOINT);
        // 创建和配置 HTTP 连接
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + GPT_API_KEY); // 使用您的 GPT API 密钥
        conn.setDoOutput(true);
        String generateImageText = "根据当前上下文，提供符合当前场景的prompt。";
        // 构建 JSON 请求体
        String jsonInputString = "{"
                + "\"model\": \"GPT-4-1106-preview\","
                + "\"messages\": [{ \"role\": \"user\", \"content\": \"" + generateImageText + "\" }]"
                + "}";
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        String imageGeneratePrompt = "";
        // 读取响应
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            String jsonResponse = response.toString();
            try {
                JSONObject obj = new JSONObject(jsonResponse);
                JSONArray choices = obj.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    if (firstChoice.has("message") && firstChoice.getJSONObject("message").has("content")) {
                        String content = firstChoice.getJSONObject("message").getString("content");
                        imageGeneratePrompt = content;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 处理异常，例如打印错误日志
            }
        }
        return imageGeneratePrompt;
    }


    private String processWithGPT(String input) throws IOException, JSONException {
        String 约束 = "";
        String finalInput = "结合我们之前的对话内容，结合关键词" + input + "，在" + 约束 + "约束下，生成绘画prompt，";


        String FAST_GPT_API_ENDPOINT = "https://fastgpt.run/api/v1/chat/completions";
        String FAST_GPT_API_KEY = "fastgpt-4LYNv2qsOd19hJd34i83lkctaGfLcXdsI6VEr";

        URL url = new URL(FAST_GPT_API_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + FAST_GPT_API_KEY);
        conn.setDoOutput(true);

        String jsonInputString = "{"
                + "\"chatId\":\"111\","
                + "\"stream\":false,"
                + "\"detail\": false,"
                + "\"variables\": { \"cTime\": \"2022/2/2 22:22\" },"
                + "\"messages\": [{ \"content\": \"" + finalInput + "\", \"role\": \"user\" }]"
                + "}";

        try (OutputStream os = conn.getOutputStream()) {
            byte[] tmpInput = jsonInputString.getBytes("utf-8");
            os.write(tmpInput, 0, tmpInput.length);
        } catch (IOException e) {
            e.printStackTrace(); // This prints the exception stack trace to the standard error stream.
        }
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // print result
            return new JSONObject(response.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

        }
        return null;
    }


    public String callImageGenerateApi(String prompt) throws IOException, JSONException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // 设置连接超时时间
                .readTimeout(30, TimeUnit.SECONDS)    // 设置读取超时时间
                .writeTimeout(30, TimeUnit.SECONDS)   // 设置写入超时时间
                .build();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("prompt", prompt);
        jsonObject.put("n", 1);
        jsonObject.put("size", "1024x1024");
        jsonObject.put("model", "dall-e-3");

        RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url("https://chatapi.onechat.fun/v1/images/generations")
                .header("Authorization", "Bearer sk-Ze1UOghr5qtuAdPRB4Dd030878B441EeBe92F2699e0bA8A6")
                .post(body)
                .build();

        Response response = client.newCall(request).execute();
        if (response.isSuccessful() && response.body() != null) {
            String responseData = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseData);
            JSONArray dataArray = jsonResponse.getJSONArray("data");
            if (dataArray.length() > 0) {
                JSONObject imageObject = dataArray.getJSONObject(0);
                return imageObject.getString("url");
            }
        }
        return null;
    }

    public void increaseImageWeight(String imagePath) {
        // 检查图像路径是否已存在于 imageWeights 中
        if (imageWeights.containsKey(imagePath)) {
            // 如果存在，增加权重值
            int currentWeight = imageWeights.get(imagePath);
            imageWeights.put(imagePath, currentWeight + 1);
        } else {
            // 如果不存在，设置权重为 2
            imageWeights.put(imagePath, 2);
        }
        Log.i("imageWeight", imagePath + ":" + imageWeights.get(imagePath));
    }

    public void decreaseImageWeight(String imagePath) {
        Integer currentWeight = imageWeights.get(imagePath);
        if (currentWeight != null && currentWeight > 2) {
            // 找到比当前图片权重小的最大权重
            int minHigherWeight = imageWeights.values().stream()
                    .filter(weight -> weight < currentWeight)
                    .max(Integer::compare)
                    .orElse(1);

            // 更新权重，确保不低于 minHigherWeight
            imageWeights.put(imagePath, Math.max(minHigherWeight + 1, currentWeight - 1));
        }
    }
}

