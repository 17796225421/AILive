package com.example.ailive;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

// 1. UI 类
public class UI extends Activity {

    // 你的UI组件和方法放这里，例如开始、停止按钮的监听器等
    private Button startButton;
    private Button cancelButton;
    private TextView askView;
    private TextView gptView;
    private Handler mHandler;
    private HandlerThread mHanderThread;
    private ASR asr;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        askView = (TextView) findViewById(R.id.askView);
        gptView = (TextView) findViewById(R.id.GPTView);

        startButton = (Button) findViewById(R.id.button_start);
        cancelButton = (Button) findViewById(R.id.button_cancel);

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                showText(askView, "");
                showText(gptView, "");
                asr.startDialog(mHandler);

            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setButtonState(startButton, true);
                setButtonState(cancelButton, false);
                asr.stopDialog(mHandler);
            }
        });

        mHanderThread = new HandlerThread("process_thread");
        mHanderThread.start();
        mHandler = new Handler(mHanderThread.getLooper());

        asr = new ASR(this, this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        doInit();
    }

    @Override
    protected void onStop() {
        super.onStop();
        asr.onStop();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }

    private void doInit() {
        showText(askView, "提问");
        showText(gptView, "GPT");

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);

        asr.doInit();
    }

    public void showText(final TextView who, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(text)) {
                    if (who == askView) {
                        who.setText("提问");
                    } else if (who == gptView) {
                        who.setText("GPT");
                    }
                } else {
                    who.setText(text);
                }
            }
        });
    }

    public void setButtonState(final Button btn, final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btn.setEnabled(state);
            }
        });
    }

    public Button getStartButton() {
        return startButton;
    }

    public void setStartButton(Button startButton) {
        this.startButton = startButton;
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    public void setCancelButton(Button cancelButton) {
        this.cancelButton = cancelButton;
    }
    public TextView getAskView() {
        return askView;
    }

    public void setAskView(TextView askView) {
        this.askView = askView;
    }

    public TextView getGptView() {
        return gptView;
    }

    public void setGptView(TextView gptView) {
        this.gptView = gptView;
    }


}