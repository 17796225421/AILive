package com.example.ailive;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.example.ailive.live2d.BackgroundImageListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Dalle3 {
    Context context;
    private static final int REQUEST_CODE_SELECT_PHOTOS = 1;


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
    public void openInChrome() {
        // Create an EditText widget for user input
        final EditText input = new EditText(context);
        input.setHint("请输入内容");

        // Create an AlertDialog to get user input
        new AlertDialog.Builder(context)
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final String userInput = input.getText().toString();

                        // Start a new thread for the network request
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final String processedText = processWithGPT(userInput);

                                    // Return to the main thread to update the UI
                                    ((Activity) context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Copy the processed text to clipboard
                                            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                            ClipData clip = ClipData.newPlainText("label", processedText);
                                            if (clipboard != null) {
                                                clipboard.setPrimaryClip(clip);
                                            }

                                            // Open the URL in Chrome
                                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.openai.com/?model=gpt-4-dalle"));
                                            intent.setPackage("com.android.chrome");
                                            context.startActivity(intent);
                                        }
                                    });

                                } catch (final IOException e) {
                                    // Return to the main thread to handle the exception
                                    ((Activity) context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(context, "Error processing input: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }).start();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }


    private String processWithGPT(String input) throws IOException, JSONException {
        String 约束= "";
        String finalInput="结合我们之前的对话内容，结合关键词"+input+"，在"+约束+"约束下，生成绘画prompt，";


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

    public void openInPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        ((Activity) context).startActivityForResult(intent, REQUEST_CODE_SELECT_PHOTOS);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) throws IOException {
        List<Bitmap> bitmapList = new ArrayList<>();
        if (requestCode == REQUEST_CODE_SELECT_PHOTOS && resultCode == RESULT_OK) {
            if (data.getClipData() != null) { // 多选的图片
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
                    bitmapList.add(bitmap);
                }
            } else if (data.getData() != null) { // 单选的图片
                Uri uri = data.getData();
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
                bitmapList.add(bitmap);
            }
        }
        for (Bitmap bitmap : bitmapList) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "output_" + timeStamp + ".png";
            saveBitmapToFile(bitmap, fileName);
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

}

