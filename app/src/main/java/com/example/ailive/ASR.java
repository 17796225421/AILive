package com.example.ailive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.idst.nui.AsrResult;
import com.alibaba.idst.nui.CommonUtils;
import com.alibaba.idst.nui.Constants;
import com.alibaba.idst.nui.INativeNuiCallback;
import com.alibaba.idst.nui.KwsResult;
import com.alibaba.idst.nui.NativeNui;
import com.example.ailive.token.AccessToken;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class ASR implements INativeNuiCallback {
    // ASR 相关的方法和变量
    private static final String APPKEY = "8Xu8cELorObhV2cQ";

    String token;
    private static final String URL = "wss://nls-gateway.aliyuncs.com:443/ws/v1";
    private AudioRecord mAudioRecorder;
    private Context context;
    private UI ui;
    NativeNui nui_instance = new NativeNui();
    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 * 16000 / 1000; //20ms audio for 16k/16bit/mono
    final static int SAMPLE_RATE = 16000;
    AccessToken accessToken = AccessToken.getInstance();
    private GPT gpt;

    public ASR(Context context, UI ui) {
        this.context = context;
        this.ui = ui;
        gpt = new GPT(ui, context);
        Thread th = new Thread() {
            @Override
            public void run() {
                try {
                    accessToken.apply();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        };
        th.start();
        try {
            th.join(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        token = accessToken.getToken();
    }

    private static final List<String> STOP_COMMANDS = Arrays.asList("勾勒", "够了", "狗了", "勾了", "狗乐", "构乐", "购乐", "勾乐", "狗拉", "够拉", "勾拉", "过了", "国乐", "锅了", "郭乐", "果乐", "国拉", "过拉", "郭拉", "锅拉", "高子", "钩子", "停下来", "停下", "听下", "挺下", "停停停", "停下", "听下", "挺下", "亭下", "庭下", "廷下", "艇下", "葶下", "汀下", "铤下", "蜓下", "町下", "婷下", "停车", "听车", "挺车", "亭车", "庭车", "廷车", "艇车", "葶车", "汀车", "铤车", "蜓车", "町车", "婷车", "停步", "听步", "挺步", "亭步", "庭步", "廷步", "艇步", "葶步", "汀步", "铤步", "蜓步", "町步", "婷步"


            // ... 其他可能的词
    );

    @SuppressLint("MissingPermission")
    public void doInit() {
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, WAVE_FRAM_SIZE * 4000);

        String workspace = CommonUtils.getModelPath(context);

        int ret = nui_instance.initialize(this, genInitParams(workspace), Constants.LogLevel.LOG_LEVEL_VERBOSE, false);
        nui_instance.setParams(genParams());
    }

    @Override
    public void onNuiEventCallback(Constants.NuiEvent nuiEvent, int resultCode, int arg2, KwsResult kwsResult, AsrResult asrResult) {
        if (nuiEvent == Constants.NuiEvent.EVENT_TRANSCRIBER_COMPLETE) {
            ui.setButtonState(ui.getStartButton(), true);
            ui.setButtonState(ui.getCancelButton(), false);
            return;
        }
        if (nuiEvent == Constants.NuiEvent.EVENT_SENTENCE_START) {
            return;
        }
        if (nuiEvent == Constants.NuiEvent.EVENT_SENTENCE_END) {
            try {
                org.json.JSONObject mainJsonObject = new org.json.JSONObject(asrResult.asrResult);
                if (!mainJsonObject.has("payload")) {
                    return;
                }
                org.json.JSONObject payloadObject = mainJsonObject.getJSONObject("payload");
                if (!payloadObject.has("result")) {
                    return;
                }
                String resultValue = payloadObject.getString("result");

                // 检查结果长度
                if (resultValue.length() < 5) {
                    for (String stopCommand : STOP_COMMANDS) {
                        if (resultValue.contains(stopCommand)) {
                            gpt.onStop();
                            gpt.onStart();
                        }
                    }
                    return;  // 小于5个字，不进行任何处理，直接返回
                }

                ui.showText(ui.getAskView(), "我：" + resultValue);

                processInputText(resultValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if (nuiEvent == Constants.NuiEvent.EVENT_ASR_ERROR) {
            return;
        }
    }

    public void processInputText(String text) {
        gpt.setAsrText(text);
        gpt.processAsrText();
    }

    @Override
    public int onNuiNeedAudioData(byte[] buffer, int len) {
        return mAudioRecorder.read(buffer, 0, len);
    }

    @Override
    public void onNuiAudioStateChanged(Constants.AudioState audioState) {
        if (audioState == Constants.AudioState.STATE_OPEN) {
            mAudioRecorder.startRecording();
            return;
        }
        if (audioState == Constants.AudioState.STATE_CLOSE) {
            mAudioRecorder.release();
            return;
        }
        if (audioState == Constants.AudioState.STATE_PAUSE) {
            mAudioRecorder.stop();
        }
    }

    @Override
    public void onNuiAudioRMSChanged(float v) {
    }

    @Override
    public void onNuiVprEventCallback(Constants.NuiVprEvent nuiVprEvent) {
    }

    String genInitParams(String workspace) {
        JSONObject object = new JSONObject();
        object.put("app_key", APPKEY);
        object.put("format", "MP3");
        object.put("token", token);
        object.put("device_id", Utils.getDeviceId());
        object.put("url", URL);
        object.put("workspace", workspace);
        object.put("service_mode", Constants.ModeFullCloud);
        return object.toString();
    }

    private String genParams() {
        // 接口说明可见https://help.aliyun.com/document_detail/173528.html
        JSONObject params = new JSONObject();
        JSONObject nls_config = new JSONObject();
        nls_config.put("enable_intermediate_result", false); // 是否返回中间识别结果，默认值：false。
        nls_config.put("enable_punctuation_prediction", true); // 是否在后处理中添加标点，默认值：false。
        nls_config.put("max_sentence_silence", 2000); // 语音断句检测阈值，静音时长超过该阈值被认为断句。取值范围：200ms～2000ms，默认值：800ms。
        nls_config.put("disfluency", true); // 过滤语气词，即声音顺滑，默认值false
        nls_config.put("speech_noise_threshold", 0.98); // 噪音参数阈值，取值范围：-1～+1。取值越接近+1，判定为噪音的越多，亦即有可能更多语音被当作噪音被拒绝掉。
        params.put("nls_config", nls_config);
        params.put("service_type", Constants.kServiceTypeSpeechTranscriber);
        return params.toString();
    }

    public void startDialog(Handler mHandler) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int ret = nui_instance.startDialog(Constants.VadMode.TYPE_P2T, genDialogParams());
            }
        });
    }

    private String genDialogParams() {
        String params = "";
        try {
            JSONObject dialog_param = new JSONObject();
            params = dialog_param.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return params;
    }

    public void stopDialog(Handler mHandler) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int ret = nui_instance.stopDialog();
            }
        });
    }

    protected void onStart(Handler mHandler) {
        startDialog(mHandler);
        gpt.onStart();
    }

    protected void onStop() {
        nui_instance.stopDialog();
        gpt.onStop();
    }

    public GPT getGpt() {
        return gpt;
    }
}