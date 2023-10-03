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

import java.util.Objects;


public class ASR implements INativeNuiCallback {
    // ASR 相关的方法和变量
    private static final String APPKEY = "8Xu8cELorObhV2cQ";
    private static final String TOKEN = "90294235bb7e455b8e6a5fb308b92aff  ";
    private static final String URL = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1";
    private AudioRecord mAudioRecorder;
    private Context context;
    private UI ui;
    NativeNui nui_instance = new NativeNui();
    final static int WAVE_FRAM_SIZE = 20 * 2 * 1 * 16000 / 1000; //20ms audio for 16k/16bit/mono
    final static int SAMPLE_RATE = 16000;
    private GPT gpt;

    public ASR(Context context, UI ui) {
        this.context = context;
        this.ui = ui;
        gpt = new GPT(ui, context);
    }

    @SuppressLint("MissingPermission")
    public void doInit() {

        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, WAVE_FRAM_SIZE * 4);

        String workspace = CommonUtils.getModelPath(context);

        nui_instance.initialize(this, genInitParams(workspace), Constants.LogLevel.LOG_LEVEL_VERBOSE, false);
        nui_instance.setParams(genParams());
    }

    @Override
    public void onNuiEventCallback(Constants.NuiEvent nuiEvent, int resultCode, int arg2, KwsResult kwsResult, AsrResult asrResult) {
        if (nuiEvent == Constants.NuiEvent.EVENT_TRANSCRIBER_COMPLETE) {
            ui.setButtonState(ui.getStartButton(), true);
            ui.setButtonState(ui.getCancelButton(), false);
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
                ui.showText(ui.getAskView(), "我：" + resultValue);

                gpt.setAsrText(resultValue);
                gpt.processAsrText();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if (nuiEvent == Constants.NuiEvent.EVENT_ASR_ERROR) {
            return;
        }
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
        object.put("token", TOKEN);
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
        nls_config.put("enable_intermediate_result", true);
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

    protected void onStop() {
        nui_instance.release();
        gpt.release();
    }

}