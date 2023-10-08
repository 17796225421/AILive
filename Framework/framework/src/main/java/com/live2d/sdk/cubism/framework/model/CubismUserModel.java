/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.sdk.cubism.framework.model;

import static com.live2d.sdk.cubism.framework.CubismFramework.VERTEX_OFFSET;
import static com.live2d.sdk.cubism.framework.CubismFramework.VERTEX_STEP;
import static com.live2d.sdk.cubism.framework.utils.CubismDebug.cubismLogError;
import static com.live2d.sdk.cubism.framework.utils.CubismDebug.cubismLogInfo;

import com.live2d.sdk.cubism.framework.effect.CubismBreath;
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink;
import com.live2d.sdk.cubism.framework.effect.CubismPose;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.math.CubismModelMatrix;
import com.live2d.sdk.cubism.framework.math.CubismTargetPoint;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion;
import com.live2d.sdk.cubism.framework.motion.CubismMotion;
import com.live2d.sdk.cubism.framework.motion.CubismMotionManager;
import com.live2d.sdk.cubism.framework.motion.CubismMotionQueueManager;
import com.live2d.sdk.cubism.framework.motion.ICubismMotionEventFunction;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;
import com.live2d.sdk.cubism.framework.physics.CubismPhysics;
import com.live2d.sdk.cubism.framework.rendering.CubismRenderer;

/**
 * This is the base class of the model that the user actually utilizes. The user defined model class inherits this class.
 */
public abstract class CubismUserModel {
    /**
     * A callback for registering with CubismMotionQueueManager for an event.
     * Call the EventFired which is inherited from CubismUserModel.
     *
     * @param eventValue the string data of the fired event
     * @param model      an instance inherited with CubismUserModel
     */
    public static void cubismDefaultMotionEventCallback(String eventValue, CubismUserModel model) {
        if (model != null) {
            model.motionEventFired(eventValue);
        }
    }

    /**
     * Get the collision detection.
     * <p>
     * Get whether the Drawable has been hit at the specified position.
     *
     * @param drawableId Drawable ID which will be verified.
     * @param pointX     X-position
     * @param pointY     Y-position
     * @return true      If it is hit, return true.
     */
    public boolean isHit(CubismId drawableId, float pointX, float pointY) {
        final int drawIndex = model.getDrawableIndex(drawableId);

        // If there are no hit Drawable, return false
        if (drawIndex < 0) {
            return false;
        }

        final int count = model.getDrawableVertexCount(drawIndex);
        final float[] vertices = model.getDrawableVertices(drawIndex);

        float left = vertices[0];
        float right = vertices[0];
        float top = vertices[1];
        float bottom = vertices[1];


        for (int i = 1; i < count; ++i) {
            float x = vertices[VERTEX_OFFSET + i * VERTEX_STEP];
            float y = vertices[VERTEX_OFFSET + i * VERTEX_STEP + 1];

            if (x < left) {
                // Min x
                left = x;
            }

            if (x > right) {
                // Max x
                right = x;
            }

            if (y < top) {
                // Min y
                top = y;
            }

            if (y > bottom) {
                // Max y
                bottom = y;
            }
        }

        final float tx = modelMatrix.invertTransformX(pointX);
        final float ty = modelMatrix.invertTransformY(pointY);

        return (left <= tx) && (tx <= right) && (top <= ty) && (ty <= bottom);
    }

    /**
     * 接收生成的渲染器并进行初始化。<br>
     * 使用此方法时，用于绘制剪切遮罩的缓冲区的默认数量为1。
     *
     * @param renderer 继承自CubismRenderer的渲染器类的实例
     * @note 如果给定的参数是null，则会抛出`NullPointerException`。
     */
    public void setupRenderer(CubismRenderer renderer) {
        setupRenderer(renderer, 1);
    }

    /**
     * 接收生成的渲染器并进行初始化。<br>
     * 如果你想增加用于绘制剪切遮罩的缓冲区的数量超过默认的1张，可以使用此方法。
     *
     * @param renderer        继承自CubismRenderer的渲染器类的实例
     * @param maskBufferCount 想要生成的遮罩缓冲区的数量
     * @note 如果第1个参数是null，则会抛出`NullPointerException`。
     */
    public void setupRenderer(CubismRenderer renderer, int maskBufferCount) {
        this.renderer = renderer;

        // 将渲染器与模型实例绑定
        this.renderer.initialize(model, maskBufferCount);
    }


    /**
     * Do a standard process at firing the event.
     * <p>
     * This method deals with the case where an Event occurs during the playback process.
     * It is basically overrided by inherited class.
     * If it is not overrided, output log.
     *
     * @param eventValue the string data of the fired event
     */
    public void motionEventFired(String eventValue) {
        cubismLogInfo(eventValue);
    }

    /**
     * Get initializing status.
     *
     * @return If this class is initialized, return true.
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Set an initializing setting.
     *
     * @param isInitialized initializing status
     */
    public void isInitialized(boolean isInitialized) {
        this.isInitialized = isInitialized;
    }

    /**
     * Get the updating status.
     *
     * @return If this class is updated, return true.
     */
    public boolean isUpdated() {
        return isUpdated;
    }

    /**
     * Set an updating status.
     *
     * @param isUpdated updating status
     */
    public void isUpdated(boolean isUpdated) {
        this.isUpdated = isUpdated;
    }

    /**
     * 设置鼠标拖动的信息。
     *
     * @param x 正在拖动的光标的X位置
     * @param y 正在拖动的光标的Y位置
     */
    public void setDragging(float x, float y) {
        // 设置拖动管理器中的x和y坐标
        dragManager.set(x, y);
    }

    /**
     * Set an acceleration information.
     *
     * @param x Acceleration in X-axis direction
     * @param y Acceleration in Y-axis direction
     * @param z Acceleration in Z-axis direction
     */
    public void setAcceleration(float x, float y, float z) {
        accelerationX = x;
        accelerationY = y;
        accelerationZ = z;
    }

    /**
     * Get the model matrix.
     * This method returns a copy of this _modelMatrix.
     *
     * @return the model matrix
     */
    public CubismModelMatrix getModelMatrix() {
        return modelMatrix;
    }

    /**
     * Get the opacity.
     *
     * @return the opacity
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * Set an opacity.
     *
     * @param opacity an opacity
     */
    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    /*
     * Get the model.
     *
     * @return the model
     */
    public CubismModel getModel() {
        return model;
    }

    /**
     * Get the renderer. This method is the generics. The user have to give the renderer type that they would like to use.
     * <p>
     * (example of use) this{@literal .<CubismRendererAndroid>getRenderer();}
     * </p>
     *
     * @param <T> renderer type to use
     * @return renderer instance
     */
    public <T> T getRenderer() {
        return (T) renderer;
    }

    /**
     * Constructor
     */
    protected CubismUserModel() {
        // Because this class inherits MotionQueueManager, the usage is the same.
        motionManager.setEventCallback(cubismDefaultMotionEventCallback, this);
    }

    /**
     * モデルデータを読み込む。
     * NOTE: デフォルトではMOC3の整合性をチェックしない。
     *
     * @param buffer MOC3ファイルが読み込まれているバイト配列バッファ
     */
    protected void loadModel(final byte[] buffer) {
        loadModel(buffer, false);
    }

    /**
     * モデルデータを読み込む。
     *
     * @param buffer MOC3ファイルが読み込まれているバイト配列バッファ
     */
    protected void loadModel(final byte[] buffer, boolean shouldCheckMocConsistency) {
        final CubismMoc moc = CubismMoc.create(buffer, shouldCheckMocConsistency);

        if (moc == null) {
            cubismLogError("Failed to create CubismMoc instance.");
            return;
        }

        this.moc = moc;
        final CubismModel model = this.moc.createModel();

        if (model == null) {
            cubismLogError("Failed to create the model.");
            return;
        }

        this.model = model;

        this.model.saveParameters();
        modelMatrix = CubismModelMatrix.create(2.67f, 2.78f);
        modelMatrix.setPosition(0.3f, -0.5f);
    }

    /**
     * Delete Moc and Model instances.
     */
    protected void delete() {
        if (moc == null || model == null) {
            return;
        }
        moc.deleteModel(model);

        moc.delete();
        model.close();
        renderer.close();

        moc = null;
        model = null;
        renderer = null;
    }

    /**
     * Load a motion data.
     *
     * @param buffer                  a buffer where motion3.json file is loaded.
     * @param onFinishedMotionHandler the callback method called at finishing motion play. If it is null, callbacking methods is not conducting.
     * @return motion class
     */
    protected CubismMotion loadMotion(
            byte[] buffer,
            IFinishedMotionCallback onFinishedMotionHandler
    ) {
        return CubismMotion.create(buffer, onFinishedMotionHandler);
    }

    /**
     * Load a motion data.
     *
     * @param buffer a buffer where motion3.json file is loaded.
     * @return motion class
     */
    protected CubismMotion loadMotion(byte[] buffer) {
        return CubismMotion.create(buffer, null);
    }

    /**
     * Load a expression data.
     *
     * @param buffer a buffer where exp3.json is loaded
     * @return motion class
     */
    protected CubismExpressionMotion loadExpression(final byte[] buffer) {
        return CubismExpressionMotion.create(buffer);
    }

    /**
     * Load pose data.
     *
     * @param buffer a buffer where pose3.json is loaded.
     */
    protected void loadPose(final byte[] buffer) {
        pose = CubismPose.create(buffer);
    }

    /**
     * Load physics data.
     *
     * @param buffer a buffer where physics3.json is loaded.
     */
    protected void loadPhysics(final byte[] buffer) {
        physics = CubismPhysics.create(buffer);
    }

    /**
     * Load a user data attached the model.
     *
     * @param buffer a buffer where userdata3.json is loaded.
     */
    protected void loadUserData(final byte[] buffer) {
        modelUserData = CubismModelUserData.create(buffer);
    }

    /**
     * Moc 数据
     */
    protected CubismMoc moc;

    /**
     * 模型实例
     */
    protected CubismModel model;

    /**
     * 动作管理器
     */
    protected CubismMotionManager motionManager = new CubismMotionManager();

    /**
     * 表情管理器
     */
    protected CubismMotionManager expressionManager = new CubismMotionManager();

    /**
     * 自动眨眼
     */
    protected CubismEyeBlink eyeBlink;

    /**
     * 呼吸
     */
    protected CubismBreath breath;

    /**
     * 模型矩阵
     */
    protected CubismModelMatrix modelMatrix;

    /**
     * 姿态管理器
     */
    protected CubismPose pose;

    /**
     * 鼠标拖动管理器
     */
    protected CubismTargetPoint dragManager = new CubismTargetPoint();

    /**
     * 物理
     */
    protected CubismPhysics physics;

    /**
     * 用户数据
     */
    protected CubismModelUserData modelUserData;

    /**
     * 初始化状态
     */
    protected boolean isInitialized;

    /**
     * 更新状态
     */
    protected boolean isUpdated;

    /**
     * 透明度
     */
    protected float opacity = 1.0f;

    /**
     * 唇同步状态
     */
    protected boolean lipSync = true;

    /**
     * 上次唇同步的控制值
     */
    protected float lastLipSyncValue;

    /**
     * 鼠标拖动的X位置
     */
    protected float dragX;

    /**
     * 鼠标拖动的Y位置
     */
    protected float dragY;

    /**
     * X轴方向上的加速度
     */
    protected float accelerationX;

    /**
     * Y轴方向上的加速度
     */
    protected float accelerationY;

    /**
     * Z轴方向上的加速度
     */
    protected float accelerationZ;

    /**
     * 是否验证MOC3的一致性。如果进行验证，则为true。
     */
    protected boolean mocConsistency;

    /**
     * 是否为调试模式
     */
    protected boolean debugMode;

    /**
     * CubismMotionEventFunction的实体。
     */
    private static final ICubismMotionEventFunction cubismDefaultMotionEventCallback = new ICubismMotionEventFunction() {
        @Override
        public void apply(
                CubismMotionQueueManager caller,
                String eventValue,
                Object customData
        ) {
            if (customData != null) {
                ((CubismUserModel) customData).motionEventFired(eventValue);
            }
        }
    };

    /**
     * 渲染器
     */
    private CubismRenderer renderer;
}
