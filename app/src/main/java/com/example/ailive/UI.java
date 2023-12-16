package com.example.ailive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.idst.nui.CommonUtils;
import com.example.ailive.live2d.GLRenderer;
import com.example.ailive.live2d.LAppDelegate;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
    private static final int REQUEST_MICROPHONE_PERMISSION = 123; // 请求码
    private static final int REQUEST_MANAGE_ALL_FILES_ACCESS_PERMISSION = 1001;
    private ImageAdapter imageAdapter;

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

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
                    // 如果没有麦克风权限，请求权限
                    requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQUEST_MICROPHONE_PERMISSION);
                } else {
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
                asr.getGpt().onStop();
                asr.getGpt().onStart();
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


        Button imageGenerationBtn = findViewById(R.id.image_generation_btn);

        imageGenerationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 直接获取生成图像的提示
                            String imageGeneratePrompt = asr.getGpt().getImageGeneratePrompt();

                            // 调用 API 生成图像
                            String imageUrl = dalle3.callImageGenerateApi(imageGeneratePrompt);

                            // 下载生成的图像
                            Bitmap bitmap = downloadImage(imageUrl);

                            // 在主线程中显示图像和保存选项
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showImageDialog(bitmap);
                                }
                            });

                        } catch (IOException | JSONException e) {
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


        Button backgroundBtn = findViewById(R.id.background_btn);
        backgroundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LinearLayout gridViewContainer = findViewById(R.id.grid_view_container);
                GridView gridView = findViewById(R.id.grid_view);
                List<String> imagePaths = loadImagePaths(); // 获取图片路径列表
                imageAdapter = new ImageAdapter(UI.this, imagePaths);
                gridView.setAdapter(imageAdapter);
                gridViewContainer.setVisibility(View.VISIBLE); // 显示 GridView 和按钮
            }
        });

        // 查看对话按钮的点击事件
        findViewById(R.id.viewConversationButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String content = readFileFromInternalStorage("/prompt/RoleConversation.txt");

                    // 创建一个EditText
                    EditText editText = new EditText(UI.this);
                    editText.setText(content);

                    // 创建一个ScrollView，并将EditText添加到其中
                    ScrollView scrollView = new ScrollView(UI.this);
                    scrollView.addView(editText);

                    // 创建并显示AlertDialog，将ScrollView设置为它的视图
                    new AlertDialog.Builder(UI.this)
                            .setTitle("编辑文件")
                            .setView(scrollView)  // 设置ScrollView为对话框内容
                            .setPositiveButton("保存", (dialog, which) -> {
                                try {
                                    writeFileToInternalStorage("/prompt/RoleConversation.txt", editText.getText().toString());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    // 处理写入错误
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } catch (IOException e) {
                    e.printStackTrace();
                    // 处理读取错误
                }
            }
        });


        // 查看角色按钮的点击事件
        findViewById(R.id.viewRoleButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String content = readFileFromInternalStorage("/prompt/RoleDesc.txt");

                    // 创建一个EditText
                    EditText editText = new EditText(UI.this);
                    editText.setText(content);

                    // 创建一个ScrollView，并将EditText添加到其中
                    ScrollView scrollView = new ScrollView(UI.this);
                    scrollView.addView(editText);

                    // 创建并显示AlertDialog，将ScrollView设置为它的视图
                    new AlertDialog.Builder(UI.this)
                            .setTitle("编辑文件")
                            .setView(scrollView)  // 设置ScrollView为对话框内容
                            .setPositiveButton("保存", (dialog, which) -> {
                                try {
                                    writeFileToInternalStorage("/prompt/RoleDesc.txt", editText.getText().toString());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    // 处理写入错误
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } catch (IOException e) {
                    e.printStackTrace();
                    // 处理读取错误
                }
            }
        });

        findViewById(R.id.reGenerateButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                asr.getGpt().onSentenceStop();

                // 获取当前文本
                String currentText = asr.getGpt().getLastAsrText();

                // 创建一个 EditText 用于编辑文本
                final EditText editText = new EditText(UI.this);
                editText.setText(currentText);

                // 创建并显示 AlertDialog
                new AlertDialog.Builder(UI.this)
                        .setTitle("编辑文本")
                        .setView(editText)
                        .setPositiveButton("提交", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 获取修改后的文本
                                String modifiedText = editText.getText().toString();
                                // 设置修改后的文本
                                asr.getGpt().setAsrText(modifiedText);

                                // 在新线程中调用 GPT API
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            asr.getGpt().callGptApi();
                                        } catch (IOException e) {
                                            // 异常处理
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        Button autonomousModeButton = findViewById(R.id.button_autonomous_mode);
        autonomousModeButton.getBackground().setAlpha(128); // 半透明
        autonomousModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });


        // 获取包含 GridView 和控制按钮的容器
        LinearLayout gridViewContainer = findViewById(R.id.grid_view_container);
        // 取消按钮的点击事件
        Button cancelButton = findViewById(R.id.background_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 隐藏整个容器，包括 GridView 和所有控制按钮
                gridViewContainer.setVisibility(View.GONE);
            }
        });
        Button deleteButton = findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageAdapter.deleteSelectedItems();
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

    private Bitmap downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.connect();
        InputStream input = connection.getInputStream();
        return BitmapFactory.decodeStream(input);
    }

    private void showImageDialog(Bitmap bitmap) {
        AlertDialog.Builder imageDialog = new AlertDialog.Builder(UI.this);
        ImageView imageView = new ImageView(UI.this);
        imageView.setImageBitmap(bitmap);
        imageDialog.setView(imageView);

        imageDialog.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 保存图片到设备的下载文件夹
                saveImageToInternalStorage(bitmap);
            }
        });

        imageDialog.setNegativeButton("取消", null);
        imageDialog.show();
    }

    private void saveImageToInternalStorage(Bitmap bitmap) {
        String imageFileName = "JPEG_" + System.currentTimeMillis() + ".jpg";

        // 获取内部存储的background目录
        File storageDir = new File(getFilesDir(), "background");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        // 创建图片文件
        File imageFile = new File(storageDir, imageFileName);
        try {
            OutputStream fOut = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class ImageAdapter extends BaseAdapter {
        private Context context;
        private List<String> imagePaths;
        private SparseBooleanArray selectedItems = new SparseBooleanArray();

        public ImageAdapter(Context context, List<String> imagePaths) {
            this.context = context;
            Collections.sort(imagePaths, new Comparator<String>() {
                @Override
                public int compare(String imagePath1, String imagePath2) {
                    return Integer.compare(getImageWeight(imagePath2),
                            getImageWeight(imagePath1));
                }
            });
            this.imagePaths = imagePaths;
        }

        @Override
        public int getCount() {
            return imagePaths.size();
        }

        @Override
        public Object getItem(int position) {
            return imagePaths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {
                // 如果视图未被重用，创建一个新的 ImageView
                imageView = new ImageView(context);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setLayoutParams(new GridView.LayoutParams(450, 450));
            } else {
                // 否则重用旧的视图
                imageView = (ImageView) convertView;
            }

            // 从路径中加载图片
            Bitmap bitmap = BitmapFactory.decodeFile(imagePaths.get(position));
            imageView.setImageBitmap(bitmap);

            imageView.setAlpha(selectedItems.get(position) ? 0.5f : 1f); // 根据是否选中来改变透明度

            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isSelected = !selectedItems.get(position);
                    selectedItems.put(position, isSelected);
                    imageView.setAlpha(isSelected ? 0.5f : 1f); // 根据是否选中来改变透明度
                }
            });

            return imageView;
        }

        public SparseBooleanArray getSelectedItems() {
            return selectedItems;
        }

        public void deleteSelectedItems() {
            // 遍历 selectedItems 来找到并删除选中的图片
            for (int i = selectedItems.size() - 1; i >= 0; i--) {
                if (selectedItems.valueAt(i)) {
                    int position = selectedItems.keyAt(i);
                    String imagePath = imagePaths.get(position);

                    File file = new File(imagePath);
                    if (file.delete()) {
                        // 如果文件删除成功，则从列表中移除
                        imagePaths.remove(position);
                    }

                    // 清除选中状态
                    selectedItems.delete(position);
                }
            }

            // 通知数据集改变
            notifyDataSetChanged();
        }

        public int getImageWeight(String imagePath) {
            return dalle3.getImageWeights().getOrDefault(imagePath, 1); // 默认权重为 1
        }
    }

    private List<String> loadImagePaths() {
        List<String> imagePaths = new ArrayList<>();
        File playgroundDir = new File(getFilesDir(), "background");

        // 确保目录存在
        if (playgroundDir.exists() && playgroundDir.isDirectory()) {
            File[] files = playgroundDir.listFiles();

            if (files != null) {
                for (File file : files) {
                    // 假设您只想加载JPG或PNG格式的图片
                    if (file.isFile() && (file.getName().endsWith(".jpg") || file.getName().endsWith(".png"))) {
                        imagePaths.add(file.getAbsolutePath());
                    }
                }
            }
        }

        return imagePaths;
    }

    private String readFileFromInternalStorage(String fileName) throws IOException {
        File file = new File(getFilesDir(), fileName);
        int length = (int) file.length();
        byte[] buffer = new byte[length];

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(buffer);
        }

        return new String(buffer, StandardCharsets.UTF_8);
    }

    private void writeFileToInternalStorage(String filePath, String content) throws IOException {
        File file = new File(getFilesDir(), filePath);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }


}