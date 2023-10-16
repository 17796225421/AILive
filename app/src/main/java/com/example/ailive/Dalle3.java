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
import android.graphics.Canvas;
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
import java.util.Collections;
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
        List<File> validFiles = new ArrayList<>();

        File downloadDir = new File("/storage/emulated/0/Download");
        File[] files = downloadDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (!file.getName().toLowerCase().endsWith(".png")) {
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

        Collections.shuffle(validFiles); // Shuffle to get randomness

        Bitmap firstBitmap = BitmapFactory.decodeFile(validFiles.get(0).getAbsolutePath());
        Bitmap secondBitmap = BitmapFactory.decodeFile(validFiles.get(1).getAbsolutePath());

        Bitmap result = Bitmap.createBitmap(1024, 2048, firstBitmap.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(firstBitmap, 0, 0, null);
        canvas.drawBitmap(secondBitmap, 0, 1024, null);

        return result;
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


}

