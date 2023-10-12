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
                .connectTimeout(120, TimeUnit.SECONDS) // 设置连接超时
                .readTimeout(120, TimeUnit.SECONDS)    // 设置读取超时
                .writeTimeout(120, TimeUnit.SECONDS)   // 设置写入超时
                .build();

        JSONObject json = getJson();


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

    private JSONObject getJson() throws JSONException {
        JSONObject json = new JSONObject();

        // 是否启用高分辨率模式 (High Resolution mode)。当设置为 `true` 时，将使用高分辨率的生成模型。默认值为 `false`。
        json.put("enable_hr", false);

        // 噪声抑制强度。该参数控制生成图像的噪声水平，较高的值可以减少噪声，但可能会损失图像的细节。默认值为 `0`。
        json.put("denoising_strength", 0.57);

        // 第一阶段生成的图像宽度。默认值为 `0`，表示使用默认的图像宽度。
        json.put("firstphase_width", 0);

        // 第一阶段生成的图像高度。默认值为 `0`，表示使用默认的图像高度。
        json.put("firstphase_height", 0);

        // 高分辨率模式下的放大倍数。当启用高分辨率模式时，生成的图像将以指定的倍数进行放大。默认值为 `2`。
        json.put("hr_scale", 2);

        // 高分辨率模式下的上采样器。该参数指定用于图像上采样的算法或模型。它可以是一个字符串，表示特定的上采样器，或者是一个模型的标识符。默认值为 "string"。
        json.put("hr_upscaler", "R-ESRGAN 4x+ Anime6B");

        // 高分辨率模式下的第二阶段生成步数。默认值为 `0`，表示只进行一次生成过程。
        json.put("hr_second_pass_steps", 0);

        // 高分辨率模式下的水平调整大小。该参数用于调整生成的图像的宽度。默认值为 `0`，表示不进行调整。
        json.put("hr_resize_x", 0);

        // 高分辨率模式下的垂直调整大小。该参数用于调整生成的图像的高度。默认值为 `0`，表示不进行调整。
        json.put("hr_resize_y", 0);

        // 用于生成的文本提示。可以提供一段文字描述或问题，以引导图像生成的方向。默认为空字符串。
        json.put("prompt", "");

        // 一个字符串数组，包含用于生成图像的风格模型的标识符或名称。可以提供一个或多个风格模型，API将根据提供的风格进行图像生成。
        json.put("styles", new JSONArray(new String[]{}));

        // 随机种子。该参数用于控制生成过程的随机性。不同的种子值会产生不同的图像结果。默认值为 `-1`，表示使用随机种子。
        json.put("seed", -1);

        // 子种子 (Subseed)。该参数用于控制生成过程中的子随机性。不同的子种子值会导致略微不同的图像生成结果。默认值为 `-1`，表示使用随机子种子。
        json.put("subseed", -1);

        // 子种子强度。该参数控制子种子的影响力。较高的值会增加子种子的影响，从而导致更大的图像变化。默认值为 `0`。
        json.put("subseed_strength", 0);

        // 调整大小的种子高度。该参数指定生成过程中用于调整大小的种子图像的高度。默认值为 `-1`，表示不使用调整大小的种子图像。
        json.put("seed_resize_from_h", -1);

        // 调整大小的种子宽度。该参数指定生成过程中用于调整大小的种子图像的宽度。默认值为 `-1`，表示不使用调整大小的种子图像。
        json.put("seed_resize_from_w", -1);

        // 采样器名称。该参数指定用于生成图像的采样器的名称或标识符。可以选择不同的采样器来获得不同的生成效果。默认值为 "string"。
        json.put("sampler_name", null);

        // 批量大小。该参数控制每次生成图像的批量大小。默认值为 `1`，表示每次生成一个图像。
        json.put("batch_size", 1);

        // 迭代次数。该参数指定生成过程的迭代次数。默认值为 `1`，表示只进行一次迭代。
        json.put("n_iter", 1);

        // 步数。该参数指定每个迭代步骤中生成器和判别器的更新次数。较大的值可能会增加图像生成的质量，但也会增加计算时间。默认值为 `50`。
        json.put("steps", 30);

        // 配置缩放。该参数控制生成过程中的配置缩放。较高的值可以产生更高分辨率的图像，但也会增加计算时间和资源消耗。默认值为 `7`。
        json.put("cfg_scale", 7);

        // 输出图像的宽度。默认值为 `512`。
        json.put("width", 512);

        // 输出图像的高度。默认值为 `512`。
        json.put("height", 512);

        // 是否修复图像中的面部。当设置为 `true` 时，API将尝试修复生成图像中的任何扭曲或损坏的面部。默认值为 `false`。
        json.put("restore_faces", false);

        // 是否启用分块渲染。当设置为 `true` 时，API将使用分块渲染技术来生成图像，从而减少资源消耗和计算时间。默认值为 `false`。
        json.put("tiling", false);

        // 是否保存样本图像。当设置为 `true` 时，API将保存生成过程中的样本图像。这可以用于调试或可视化目的。默认值为 `false`。
        json.put("do_not_save_samples", false);

        // 是否保存网格图像。当设置为 `true` 时，API将保存生成过程中的网格图像。这可以用于调试或可视化目的。默认值为 `false`。
        json.put("do_not_save_grid", false);

        // 用于生成的负面文本提示。可以提供一段负面的文字描述或问题，以避免生成特定的内容。默认为 "string"。
        json.put("negative_prompt", "NG_DeepNegative_V1_75T, EasyNegative, error, missing fingers, extra digit, fewer digits, cropped, worst quality, low quality, normal quality, jpeg artifacts, signature, watermark, username, blurry, (worst quality, low quality:1.4), (bad anatomy), (inaccurate limb:1.2), bad composition, inaccurate eyes, extra digit,fewer digits, (extra arms:1.2), (bad-artist:0.6), bad-image-v2-39000");

        // 未提供详细注释的部分：
        json.put("eta", 0);
        json.put("s_min_uncond", 0);
        json.put("s_churn", 0);
        json.put("s_tmax", 0);
        json.put("s_tmin", 0);
        json.put("s_noise", 1);
        JSONObject overrideSettings = new JSONObject();
        overrideSettings.put("sd_model_checkpoint", "cuteyukimixAdorable_midchapter3.safetensors [0212c833dc]");
        overrideSettings.put("sd_vae", "anything-v4.0.vae.pt");
        json.put("override_settings", overrideSettings);
        json.put("override_settings_restore_afterwards", true);

        JSONArray scriptArgsArray = new JSONArray(new String[]{});
        json.put("script_args", scriptArgsArray);
        json.put("sampler_index", "DPM++ SDE Karras");
        json.put("script_name", null);
        json.put("clip_skip", 2);
        json.put("send_images", true);
        json.put("save_images", false);
        json.put("alwayson_scripts", new JSONObject());

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

        return json;
    }

}
