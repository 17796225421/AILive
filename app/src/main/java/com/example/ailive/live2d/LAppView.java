/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.example.ailive.live2d;

import static com.example.ailive.live2d.LAppDefine.*;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix;
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenSurfaceAndroid;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

public class LAppView {
    /**
     * LAppModelのレンダリング先
     */
    public enum RenderingTarget {
        NONE,   // デフォルトのフレームバッファにレンダリング
        MODEL_FRAME_BUFFER,     // LAppModelForSmallDemoが各自持つフレームバッファにレンダリング
        VIEW_FRAME_BUFFER  // LAppViewForSmallDemoが持つフレームバッファにレンダリング
    }

    public LAppView() {
        clearColor[0] = 1.0f;
        clearColor[1] = 1.0f;
        clearColor[2] = 1.0f;
        clearColor[3] = 0.0f;
    }

    // シェーダーを初期化する
    public void initializeShader() {
        programId = LAppDelegate.getInstance().createShader();
    }

    // 初始化视图
    public void initialize() {
        // 获取窗口的宽和高
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();

        // 根据宽和高计算宽高比
        float ratio = (float) width / (float) height;
        // 根据宽高比设定左右边界
        float left = -ratio;
        float right = ratio;
        // 设定上下边界
        float bottom = LogicalView.LEFT.getValue();
        float top = LogicalView.RIGHT.getValue();

        // 设定视图矩阵的屏幕矩形范围
        viewMatrix.setScreenRect(left, right, bottom, top);
        // 对视图矩阵进行默认缩放
        viewMatrix.scale(Scale.DEFAULT.getValue(), Scale.DEFAULT.getValue());

        // 重置设备到屏幕的矩阵为单位矩阵
        deviceToScreen.loadIdentity();

        // 根据窗口的形状，对设备到屏幕的矩阵进行缩放
        if (width > height) {
            float screenW = Math.abs(right - left);
            deviceToScreen.scaleRelative(screenW / width, -screenW / width);
        } else {
            float screenH = Math.abs(top - bottom);
            deviceToScreen.scaleRelative(screenH / height, -screenH / height);
        }
        // 将设备到屏幕的矩阵平移至屏幕中心
        deviceToScreen.translateRelative(-width * 0.5f, -height * 0.5f);

        // 设置视图矩阵的缩放范围
        viewMatrix.setMaxScale(Scale.MAX.getValue());   // 设置最大缩放率
        viewMatrix.setMinScale(Scale.MIN.getValue());   // 设置最小缩放率

        // 设置视图矩阵的最大显示范围
        viewMatrix.setMaxScreenRect(
                MaxLogicalView.LEFT.getValue(),
                MaxLogicalView.RIGHT.getValue(),
                MaxLogicalView.BOTTOM.getValue(),
                MaxLogicalView.TOP.getValue()
        );
    }

    // 画像を初期化する
    public void initializeSprite() {
        int windowWidth = LAppDelegate.getInstance().getWindowWidth();
        int windowHeight = LAppDelegate.getInstance().getWindowHeight();

        LAppTextureManager textureManager = LAppDelegate.getInstance().getTextureManager();

        // 背景画像の読み込み
        LAppTextureManager.TextureInfo backgroundTexture = textureManager.createTextureFromPngFile(ResourcePath.ROOT.getPath() + ResourcePath.BACK_IMAGE.getPath());


        // x,yは画像の中心座標
        float x = windowWidth * 0.5f;
        float y = windowHeight * 0.5f;
        float fWidth = windowWidth;
        float fHeight = windowHeight;

        if (backSprite == null) {
            backSprite = new LAppSprite(x, y, fWidth, fHeight, backgroundTexture.id, programId);
        } else {
            backSprite.resize(x, y, fWidth, fHeight);
        }

        // 画面全体を覆うサイズ
        x = windowWidth * 0.5f;
        y = windowHeight * 0.5f;

        if (renderingSprite == null) {
            renderingSprite = new LAppSprite(x, y, windowWidth, windowHeight, 0, programId);
        } else {
            renderingSprite.resize(x, y, windowWidth, windowHeight);
        }
    }

    // 描画する
    public void render() {

        backSprite.render();

        // モデルの描画
        LAppLive2DManager live2dManager = LAppLive2DManager.getInstance();
        live2dManager.onUpdate();

        // 各モデルが持つ描画ターゲットをテクスチャとする場合
        if (renderingTarget == RenderingTarget.MODEL_FRAME_BUFFER && renderingSprite != null) {
            final float[] uvVertex = {
                    1.0f, 1.0f,
                    0.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 0.0f
            };

            for (int i = 0; i < live2dManager.getModelNum(); i++) {
                LAppModel model = live2dManager.getModel(i);
                float alpha = i < 1 ? 1.0f : model.getOpacity();    // 片方のみ不透明度を取得できるようにする。

                renderingSprite.setColor(1.0f, 1.0f, 1.0f, alpha);

                if (model != null) {
                    renderingSprite.renderImmediate(model.getRenderingBuffer().getColorBuffer()[0], uvVertex);
                }
            }
        }
    }

    /**
     * モデル1体を描画する直前にコールされる
     *
     * @param refModel モデルデータ
     */
    public void preModelDraw(LAppModel refModel) {
        // 別のレンダリングターゲットへ向けて描画する場合の使用するオフスクリーンサーフェス
        CubismOffscreenSurfaceAndroid useTarget;

        // 別のレンダリングターゲットへ向けて描画する場合
        if (renderingTarget != RenderingTarget.NONE) {

            // 使用するターゲット
            useTarget = (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER)
                    ? renderingBuffer
                    : refModel.getRenderingBuffer();

            // 描画ターゲット内部未作成の場合はここで作成
            if (!useTarget.isValid()) {
                int width = LAppDelegate.getInstance().getWindowWidth();
                int height = LAppDelegate.getInstance().getWindowHeight();

                // モデル描画キャンバス
                useTarget.createOffscreenFrame((int) width, (int) height, null);
            }
            // レンダリング開始
            useTarget.beginDraw(null);
            useTarget.clear(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);   // 背景クリアカラー
        }
    }

    /**
     * モデル1体を描画した直後にコールされる
     *
     * @param refModel モデルデータ
     */
    public void postModelDraw(LAppModel refModel) {
        CubismOffscreenSurfaceAndroid useTarget = null;

        // 別のレンダリングターゲットへ向けて描画する場合
        if (renderingTarget != RenderingTarget.NONE) {
            // 使用するターゲット
            useTarget = (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER)
                    ? renderingBuffer
                    : refModel.getRenderingBuffer();

            // レンダリング終了
            useTarget.endDraw();

            // LAppViewの持つフレームバッファを使うなら、スプライトへの描画はこことなる
            if (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER && renderingSprite != null) {
                final float[] uvVertex = {
                        1.0f, 1.0f,
                        0.0f, 1.0f,
                        0.0f, 0.0f,
                        1.0f, 0.0f
                };
                renderingSprite.setColor(1.0f, 1.0f, 1.0f, getSpriteAlpha(0));
                renderingSprite.renderImmediate(useTarget.getColorBuffer()[0], uvVertex);
            }
        }
    }

    /**
     * レンダリング先を切り替える
     *
     * @param targetType レンダリング先
     */
    public void switchRenderingTarget(RenderingTarget targetType) {
        renderingTarget = targetType;
    }

    /**
     * タッチされたときに呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    public void onTouchesBegan(float pointX, float pointY) {
        touchManager.touchesBegan(pointX, pointY);
    }

    /**
     * 当触摸点在屏幕上移动时调用此方法
     *
     * @param pointX 屏幕上的X坐标
     * @param pointY 屏幕上的Y坐标
     */
    public void onTouchesMoved(float pointX, float pointY) {
        // 获取上一个触摸点的转换后的X坐标
        float viewX = transformViewX(touchManager.getLastX());
        // 获取上一个触摸点的转换后的Y坐标
        float viewY = transformViewY(touchManager.getLastY());

        // 更新触摸管理器中的触摸点位置
        touchManager.touchesMoved(pointX, pointY);

        // 通知Live2D管理器进行拖拽操作
        LAppLive2DManager.getInstance().onDrag(viewX, viewY);
    }

    /**
     * タッチが終了したら呼ばれる
     *
     * @param pointX スクリーンX座標
     * @param pointY スクリーンY座標
     */
    public void onTouchesEnded(float pointX, float pointY) {
        // タッチ終了
        LAppLive2DManager live2DManager = LAppLive2DManager.getInstance();
        live2DManager.onDrag(0.0f, 0.0f);

        // シングルタップ
        // 論理座標変換した座標を取得
        float x = deviceToScreen.transformX(touchManager.getLastX());
        // 論理座標変換した座標を取得
        float y = deviceToScreen.transformY(touchManager.getLastY());

        if (DEBUG_TOUCH_LOG_ENABLE) {
            LAppPal.printLog("Touches ended x: " + x + ", y:" + y);
        }

        live2DManager.onTap(x, y);

    }

    /**
     * 将X坐标转换为View坐标
     *
     * @param deviceX 设备的X坐标
     * @return View的X坐标
     */
    public float transformViewX(float deviceX) {
        // 获取逻辑坐标转换后的坐标
        float screenX = deviceToScreen.transformX(deviceX);
        // 获取经过缩放、移动之后的值
        return viewMatrix.invertTransformX(screenX);
    }

    /**
     * 将Y坐标转换为View坐标
     *
     * @param deviceY 设备的Y坐标
     * @return View的Y坐标
     */
    public float transformViewY(float deviceY) {
        // 获取逻辑坐标转换后的坐标
        float screenY = deviceToScreen.transformY(deviceY);
        // 获取经过缩放、移动之后的值
        return viewMatrix.invertTransformX(screenY);
    }

    /**
     * 将X坐标转换为屏幕坐标
     *
     * @param deviceX 设备的X坐标
     * @return 屏幕的X坐标
     */
    public float transformScreenX(float deviceX) {
        return deviceToScreen.transformX(deviceX);
    }

    /**
     * 将Y坐标转换为屏幕坐标
     *
     * @param deviceY 设备的Y坐标
     * @return 屏幕的Y坐标
     */
    public float transformScreenY(float deviceY) {
        return deviceToScreen.transformY(deviceY);
    }

    /**
     * レンダリング先をデフォルト以外に切り替えた際の背景クリア色設定
     *
     * @param r 赤(0.0~1.0)
     * @param g 緑(0.0~1.0)
     * @param b 青(0.0~1.0)
     */
    public void setRenderingTargetClearColor(float r, float g, float b) {
        clearColor[0] = r;
        clearColor[1] = g;
        clearColor[2] = b;
    }

    /**
     * 別レンダリングターゲットにモデルを描画するサンプルで描画時のαを決定する
     *
     * @param assign
     * @return
     */
    public float getSpriteAlpha(int assign) {
        // assignの数値に応じて適当な差をつける
        float alpha = 0.25f + (float) assign * 0.5f;

        // サンプルとしてαに適当な差をつける
        if (alpha > 1.0f) {
            alpha = 1.0f;
        }
        if (alpha < 0.1f) {
            alpha = 0.1f;
        }
        return alpha;
    }

    /**
     * Return rendering target enum instance.
     *
     * @return rendering target
     */
    public RenderingTarget getRenderingTarget() {
        return renderingTarget;
    }

    public void updateBackSpriteFromBitmap(Bitmap bitmap) {
        updateTextureFromBitmap(1, bitmap);

        Log.i("zhouzihong", "1");
    }

    private void updateTextureFromBitmap(int existingTextureId, Bitmap bitmap) {
        // 激活纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, existingTextureId);

        // 使用新的Bitmap更新纹理内容
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // 生成新的mipmap
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        // 纹理过滤参数，如之前设置的那样
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        bitmap.recycle();
        bitmap = null;
    }

    public void setNewBackgroundImage(Bitmap image) {
        if (backSprite != null) {
            backSprite.setNewBackgroundImage(image);
        }
    }


    private final CubismMatrix44 deviceToScreen = CubismMatrix44.create(); // 将设备坐标转换为屏幕坐标的矩阵
    private final CubismViewMatrix viewMatrix = new CubismViewMatrix();   // 用于执行屏幕显示的缩放和移动转换的矩阵
    private int programId;
    private int windowWidth;
    private int windowHeight;

    /**
     * レンダリング先の選択肢
     */
    private RenderingTarget renderingTarget = RenderingTarget.NONE;
    /**
     * レンダリングターゲットのクリアカラー
     */
    private final float[] clearColor = new float[4];

    private CubismOffscreenSurfaceAndroid renderingBuffer = new CubismOffscreenSurfaceAndroid();

    public LAppSprite getBackSprite() {
        return backSprite;
    }

    private LAppSprite backSprite;
    private LAppSprite renderingSprite;

    /**
     * モデルの切り替えフラグ
     */

    private final TouchManager touchManager = new TouchManager();
}
