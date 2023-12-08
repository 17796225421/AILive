package com.example.ailive;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    private final String GPT_API_ENDPOINT = "https://chatapi.onechat.fun/v1/chat/completions";
    private final String GPT_API_KEY = "sk-Ze1UOghr5qtuAdPRB4Dd030878B441EeBe92F2699e0bA8A6";


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
            URL url = new URL(GPT_API_ENDPOINT);
            // 创建和配置 HTTP 连接
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + GPT_API_KEY); // 使用您的 GPT API 密钥
            conn.setDoOutput(true);

            // 构建 JSON 请求体
            String jsonInputString = "{"
                    + "\"model\": \"GPT-4-1106-preview\","
                    + "\"messages\": [{ \"role\": \"user\", \"content\": \"" + asrText + "\" }],"
                    + "\"stream\": true" // 添加这一行来启用流式传输
                    + "}";

            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            // 读取响应
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    if(line.isEmpty()){
                        continue;
                    }
                    // 解析 JSON 数据
                    String jsonData = line.substring("data: ".length()).trim();
                    JSONObject jsonObject = new JSONObject(jsonData);
                    JSONArray choices = jsonObject.getJSONArray("choices");
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.getJSONObject("delta");

                    if (delta.has("content")) {
                        String content = delta.getString("content");
                        if(content.isEmpty()){
                            continue;
                        }
                        // 将内容添加到累积文本
                        accumulatedText.append(content);
                        segmentText.append(content);
                        ui.showText(ui.getGptView(), "GPT：" + accumulatedText); // 显示累积的文本
                    }else{
                        processSegmentText();
                        segmentText.setLength(0);
                        accumulatedText.setLength(0);
                        break;
                    }

                    handleAnswerEvent(jsonData);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAnswerEvent(String content) throws JSONException {
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