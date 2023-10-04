package com.example.ailive;


import android.content.Context;

import com.example.ailive.token.AccessToken;

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
    private Thread gptThread;  // 添加这一行
    private SegmentProcessingStatus segmentStatus = SegmentProcessingStatus.FIRST_40; // 初始化为首10个字的状态

    private enum SegmentProcessingStatus {
        FIRST_40,
        FIRST_40_READY_TO_SPLIT, // 添加此标识
        FIRST_50,
        FIRST_50_READY_TO_SPLIT, // 添加此标识
        DONE
    }

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
        segmentStatus = SegmentProcessingStatus.FIRST_40;
        gptThread = new Thread(this);  // 使用成员变量
        gptThread.start();
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
                while ((line = br.readLine()) != null && !Thread.currentThread().isInterrupted()) {
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
        if (segmentStatus != SegmentProcessingStatus.DONE) {
            handleFirstSegment(segmentText);
        } else {
            if (segmentText.toString().contains("\n")) {
                processSegmentText();
                segmentText.setLength(0);
            }
        }
    }

    private void handleFirstSegment(StringBuilder segmentText) {
        if (segmentText.length() == 0) {
            return;
        }

        char currentChar = segmentText.charAt(segmentText.length() - 1);
        switch (segmentStatus) {
            case FIRST_40:
                if (segmentText.length() >= 20) {
                    segmentStatus = SegmentProcessingStatus.FIRST_40_READY_TO_SPLIT;
                }
                break;
            case FIRST_40_READY_TO_SPLIT:
                if (isPauseCharacter(currentChar)) {
                    processSegmentText();
                    segmentText.setLength(0);
                    segmentStatus = SegmentProcessingStatus.FIRST_50;
                }
                break;
            case FIRST_50:
                if (segmentText.length() >= 10) {
                    segmentStatus = SegmentProcessingStatus.FIRST_50_READY_TO_SPLIT;
                }
                break;
            case FIRST_50_READY_TO_SPLIT:
                if (isPauseCharacter(currentChar)) {
                    processSegmentText();
                    segmentText.setLength(0);
                    segmentStatus = SegmentProcessingStatus.DONE;
                }
                break;
            default:
                break;
        }
    }

    private boolean isPauseCharacter(char c) {
        return c == '。' || c == ',' || c == '?' || c == '!' || c == ';' || c == '\n';
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

    protected void onStart() {
        accumulatedText.setLength(0);
    }

    protected void onStop() {
        segmentText.setLength(0);
        segmentStatus = SegmentProcessingStatus.FIRST_40;
        asrText = "";
        if (gptThread != null && gptThread.isAlive()) {  // 检查线程是否还在运行
            gptThread.interrupt();  // 尝试中断线程
            gptThread = null;
        }
        tts.onStop();
    }

    protected void onSentenceStop() {
        segmentText.setLength(0);
        segmentStatus = SegmentProcessingStatus.FIRST_40;
        asrText = "";
        accumulatedText.setLength(0);
        if (gptThread != null && gptThread.isAlive()) {  // 检查线程是否还在运行
            gptThread.interrupt();  // 尝试中断线程
            gptThread = null;
        }
        tts.onStop();
    }

}