package com.example.ailive;


import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GPT implements Runnable {
    private UI ui;
    private String asrText;
    StringBuilder accumulatedText;
    StringBuilder segmentText;
    Context context;
    TTS tts;
    private final String FAST_GPT_API_ENDPOINT = "https://fastgpt.run/api/v1/chat/completions";
    private final String FAST_GPT_API_KEY = "fastgpt-4LYNv2qsOd19hJd34i83lkctaGfLcXdsI6VEr";


    GPT(UI ui, Context context) {
        this.ui = ui;
        this.context = context;
        accumulatedText = new StringBuilder();
        segmentText = new StringBuilder();
        tts = new TTS(context);
    }

    public void processAsrText() {
        Thread thread = new Thread(this);
        thread.start();  // 开始线程
    }

    @Override
    public void run() {
        try {
            URL url = new URL(FAST_GPT_API_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + FAST_GPT_API_KEY);
            conn.setDoOutput(true);

            String jsonInputString = "{"
                    + "\"chatId\":\"111\","
                    + "\"stream\":true,"
                    + "\"detail\": true,"
                    + "\"variables\": { \"cTime\": \"2022/2/2 22:22\" },"
                    + "\"messages\": [{ \"content\": \"" + asrText + "\", \"role\": \"user\" }]"
                    + "}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("event: ")) continue;

                    String eventType = line.substring("event: ".length()).trim();
                    String dataLine = br.readLine();

                    if (dataLine == null || !dataLine.startsWith("data: ")) continue;
                    String jsonData = dataLine.substring("data: ".length()).trim();

                    if (jsonData.contains("DONE")) {
                        processSegmentText();
                        segmentText.setLength(0);
                        accumulatedText.setLength(0);
                        break;
                    }

                    handleEvent(eventType, jsonData);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleEvent(String eventType, String jsonData) throws JSONException {
        switch (eventType) {
            case "answer":
                handleAnswerEvent(jsonData);
                break;
            case "appStreamResponse":
                // TODO: 添加处理appStreamResponse的逻辑
                break;
        }
    }

    private void handleAnswerEvent(String jsonData) throws JSONException {
        org.json.JSONObject jsonResponse = new org.json.JSONObject(jsonData);
        if (!jsonResponse.has("choices")) {
            return;
        }

        org.json.JSONArray choicesArray = jsonResponse.getJSONArray("choices");
        if (choicesArray.length() == 0) {
            return;
        }

        org.json.JSONObject choiceObject = choicesArray.getJSONObject(0);
        if (!choiceObject.has("delta") || !choiceObject.getJSONObject("delta").has("content")) {
            return;
        }

        String content = choiceObject.getJSONObject("delta").getString("content");
        accumulatedText.append(content);
        ui.showText(ui.getGptView(), "GPT：" + accumulatedText);
        segmentText.append(content);
        if (segmentText.toString().contains("\n")) {
            processSegmentText();
            segmentText.setLength(0);
        }
    }

    private void processSegmentText() {
        tts.produce(segmentText.toString());
    }

    public String getAsrText() {
        return asrText;
    }

    public void setAsrText(String asrText) {
        this.asrText = asrText;
    }

    public void release(){
        accumulatedText.setLength(0);
        tts.release();
    }

}