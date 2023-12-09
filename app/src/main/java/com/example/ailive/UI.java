package com.example.ailive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.idst.nui.CommonUtils;
import com.example.ailive.live2d.GLRenderer;
import com.example.ailive.live2d.LAppDelegate;

import java.io.IOException;

// 1. UI 类
public class UI extends Activity {

    // 你的UI组件和方法放这里，例如开始、停止按钮的监听器等
    private Button startButton;
    private Button cancelButton;
    private Button submitButton;
    private Button imageRecognitionBtn;
    private EditText askView;
    private TextView gptView;
    private GLSurfaceView live2DView;
    private Handler mHandler;
    private HandlerThread mHanderThread;
    private static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private ASR asr;
    //    private SD sd;
    private Dalle3 dalle3;
    private Vision vision;
    private static final int REQUEST_MICROPHONE_PERMISSION = 123; // 请求码
    private static final int REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION = 1001;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        live2DView = findViewById(R.id.live2dView);
        live2DView.setEGLContextClientVersion(2);
        live2DView.setRenderer(new GLRenderer());
        live2DView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        CommonUtils.copyAssetsData(this);

        askView = findViewById(R.id.askView);
        gptView = findViewById(R.id.GPTView);

        startButton = findViewById(R.id.button_start);
        cancelButton = findViewById(R.id.button_cancel);
        submitButton = findViewById(R.id.submitButton);

        setButtonState(startButton, true);
        setButtonState(cancelButton, false);
        setButtonState(submitButton, true);

//        changeBgBtn = findViewById(R.id.change_bg_btn);

//        sd = new SD(this);
//        sd.setBackgroundImageListener(LAppDelegate.getInstance());
//        sd.setupAutoImageSwitching();

        dalle3 = new Dalle3(this);
        dalle3.setBackgroundImageListener(LAppDelegate.getInstance());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 如果没有 "All Files Access" 权限，则请求它
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            } else {
                // 如果已有权限，执行其他操作
                dalle3.setupAutoImageSwitching();
            }
        }

        // 检查是否已经有蓝牙权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {

            // 没有蓝牙权限，向用户请求
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }

        Vision vision = new Vision(this);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
                    // 如果没有麦克风权限，请求权限
                    requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQUEST_MICROPHONE_PERMISSION);
                }else {
                    // 启动蓝牙SCO
                    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager != null) {
                        audioManager.startBluetoothSco();
                        audioManager.setBluetoothScoOn(true);
                    }
                }
                setButtonState(startButton, false);
                setButtonState(cancelButton, true);
                showText(askView, "");
                showText(gptView, "");
                asr.onStart(mHandler);
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setButtonState(startButton, true);
                setButtonState(cancelButton, false);
                asr.stopDialog(mHandler);
                asr.onStop();
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                asr.getGpt().onSentenceStop();
                String inputText = askView.getText().toString();
                asr.processInputText(inputText);
            }
        });

//        changeBgBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
////                sd.fetchAndSetNewBackgroundImage();
//            }
//        });

        imageRecognitionBtn = findViewById(R.id.image_recognition_btn);
        imageRecognitionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vision.openURLInChrome();
            }
        });

        Button imageGenerationBtn = findViewById(R.id.image_generation_btn);

        imageGenerationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String imageGeneratePrompt = dalle3.getImageGeneratePrompt();
                            // 切换回主线程来处理UI
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // 创建 EditText 并设置预先填充内容
                                    final EditText input = new EditText(UI.this);
                                    input.setText(imageGeneratePrompt);

                                    // 创建并显示 AlertDialog
                                    AlertDialog.Builder builder = new AlertDialog.Builder(UI.this);
                                    builder.setTitle("Generated Prompt")
                                            .setView(input) // 将 EditText 设置为对话框视图
                                            .setPositiveButton("提交", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    String modifiedPrompt = input.getText().toString();
                                                    // 在这里执行网络操作，使用 modifiedPrompt
                                                    // 注意：网络操作应在新的线程中进行
                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            // 执行网络操作
                                                            // ...
                                                            dalle3.callImageGenerateApi(modifiedPrompt);
                                                        }
                                                    }).start();
                                                }
                                            })
                                            .setNegativeButton("取消", null)
                                            .show();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }).start();
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
        LAppDelegate.getInstance().onStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LAppDelegate.getInstance().onStop();
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

    public EditText getAskView() {
        return askView;
    }

    public void setAskView(EditText askView) {
        this.askView = askView;
    }

    public TextView getGptView() {
        return gptView;
    }

    public void setGptView(TextView gptView) {
        this.gptView = gptView;
    }

    @Override
    protected void onResume() {
        super.onResume();

        live2DView.onResume();

        View decor = this.getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    protected void onPause() {
        super.onPause();

        live2DView.onPause();
        LAppDelegate.getInstance().onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LAppDelegate.getInstance().onDestroy();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float pointX = event.getX();
        float pointY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                LAppDelegate.getInstance().onTouchBegan(pointX, pointY);
                break;
            case MotionEvent.ACTION_UP:
                LAppDelegate.getInstance().onTouchEnd(pointX, pointY);
                break;
            case MotionEvent.ACTION_MOVE:
                LAppDelegate.getInstance().onTouchMoved(pointX, pointY);
                break;
        }
        return super.onTouchEvent(event);
    }

}