package com.example.ailive;

import android.content.Context;
import android.graphics.Path;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OpenCV implements CameraBridgeViewBase.CvCameraViewListener2 {
    private CameraBridgeViewBase mOpenCvCameraView;
    private static OpenCV instance;
    private static CascadeClassifier eyeDetector;

    public float getAvgEyeX() {
        return avgEyeX;
    }

    public float getAvgEyeY() {
        return avgEyeY;
    }

    private float avgEyeX = 0;
    private float avgEyeY = 0;
    private OpenCV()  {

    }

    public static void init(UI ui) throws IOException {
        InputStream isEye = ui.getResources().openRawResource(R.raw.haarcascade_eye);
        File cascadeDirEye = ui.getDir("cascadeeye", Context.MODE_PRIVATE);
        File mCascadeFileEye = new File(cascadeDirEye, "haarcascade_eye.xml");
        FileOutputStream osEye = new FileOutputStream(mCascadeFileEye);

        byte[] bufferEye = new byte[4096];
        int bytesReadEye;
        while ((bytesReadEye = isEye.read(bufferEye)) != -1) {
            osEye.write(bufferEye, 0, bytesReadEye);
        }
        isEye.close();
        osEye.close();

        // initialize the eye detector with the cascade file
        eyeDetector = new CascadeClassifier(mCascadeFileEye.getAbsolutePath());

        cascadeDirEye.delete();

    }

    // 公共静态方法，返回单例对象
    public static OpenCV getInstance() {
        if (instance == null) {
            // 线程安全的懒汉式单例
            synchronized (OpenCV.class) {
                if (instance == null) {
                    instance = new OpenCV();
                }
            }
        }
        return instance;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // 获取帧
        Mat frame = inputFrame.rgba();
        Mat rotatedFrame = frame.clone();

        // 将图像逆时针旋转90度
        Core.rotate(frame, rotatedFrame, Core.ROTATE_90_COUNTERCLOCKWISE);

        // 创建灰度图像
        Mat grayscaleImage = new Mat();
        Imgproc.cvtColor(rotatedFrame, grayscaleImage, Imgproc.COLOR_RGBA2GRAY);

        MatOfRect eyes = new MatOfRect();

        if (eyeDetector != null)
            eyeDetector.detectMultiScale(grayscaleImage, eyes, 1.1, 10, 2,
                    new Size() , new Size());

        Rect[] eyesArray = eyes.toArray();
        float totalEyeX = 0;
        float totalEyeY = 0;
        for (Rect rect : eyesArray) {
            Imgproc.rectangle(rotatedFrame, rect.tl(), rect.br(), new Scalar(255, 0, 0, 255), 3);


            // 计算并打印每个眼睛的中心
            int eyeX = rect.x + rect.width / 2;
            int eyeY = rect.y + rect.height / 2;

            // 获取当前时间的毫秒数
            long timeMillis = System.currentTimeMillis();

            // 创建一个日期格式化对象
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

            // 将毫秒数格式化为日期对象
            Date resultDate = new Date(timeMillis);
            totalEyeX += eyeX;
            totalEyeY += eyeY;
            Log.i("Eye ", "Eye center at (" + eyeX + ", " + eyeY + "), time: " + sdf.format(resultDate));
        }
        if (eyesArray.length != 0) {
            avgEyeX = totalEyeX / eyesArray.length;
            avgEyeY = totalEyeY / eyesArray.length;

            // map the averages to range [-1, 1]
            avgEyeX = mapToRange(avgEyeX, 0f, 2000f, -1f, 1f);
            avgEyeY = mapToRange(avgEyeY, 0f, 2000f, -1f, 1f);
        }
        Log.i("平均 ", "Eye center at (" + avgEyeX + ", " + avgEyeY );


        // 将图像顺时针旋转90度，恢复原始尺寸
        Core.rotate(rotatedFrame, frame, Core.ROTATE_90_CLOCKWISE);

        return frame;
    }
    private float mapToRange(float value, float inputMin, float inputMax, float outputMin, float outputMax) {
        return (value - inputMin) / (inputMax - inputMin) * (outputMax - outputMin) + outputMin;
    }
}
