/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.example.ailive.live2d;

import android.util.Log;

import com.live2d.sdk.cubism.framework.CubismDefaultParameterId.ParameterId;
import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.CubismModelSettingJson;
import com.live2d.sdk.cubism.framework.ICubismModelSetting;
import com.live2d.sdk.cubism.framework.effect.CubismBreath;
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.id.CubismIdManager;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.model.CubismMoc;
import com.live2d.sdk.cubism.framework.model.CubismUserModel;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion;
import com.live2d.sdk.cubism.framework.motion.CubismMotion;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;
import com.live2d.sdk.cubism.framework.rendering.CubismRenderer;
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenSurfaceAndroid;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid;
import com.live2d.sdk.cubism.framework.utils.CubismDebug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LAppModel extends CubismUserModel {

    private float mouthOpenTime = 0.0f; // 添加计时器变量
    private boolean isMouthOpen = false; // 表示嘴巴当前是否应该打开

    public LAppModel() {
        if (LAppDefine.MOC_CONSISTENCY_VALIDATION_ENABLE) {
            mocConsistency = true;
        }

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            debugMode = true;
        }

        CubismIdManager idManager = CubismFramework.getIdManager();

        idParamAngleX = idManager.getId(ParameterId.ANGLE_X.getId());
        idParamAngleY = idManager.getId(ParameterId.ANGLE_Y.getId());
        idParamAngleZ = idManager.getId(ParameterId.ANGLE_Z.getId());
        idParamBodyAngleX = idManager.getId(ParameterId.BODY_ANGLE_X.getId());
        idParamEyeBallX = idManager.getId(ParameterId.EYE_BALL_X.getId());
        idParamEyeBallY = idManager.getId(ParameterId.EYE_BALL_Y.getId());
    }

    public void loadAssets(final String dir, final String fileName) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("load model setting: " + fileName);
        }

        modelHomeDirectory = dir;
        String filePath = modelHomeDirectory + fileName;

        // json読み込み
        byte[] buffer = createBuffer(filePath);

        ICubismModelSetting setting = new CubismModelSettingJson(buffer);

        // Setup model
        setupModel(setting);

        if (model == null) {
            LAppPal.printLog("Failed to loadAssets().");
            return;
        }

        // Setup renderer.
        CubismRenderer renderer = CubismRendererAndroid.create();
        setupRenderer(renderer);

        setupTextures();
    }

    /**
     * Delete the model which LAppModel has.
     */
    public void deleteModel() {
        delete();
    }

    /**
     * 模型更新处理。根据模型参数决定绘制状态。
     */
    public void update() {
        final float deltaTimeSeconds = LAppPal.getDeltaTime();
        userTimeSeconds += deltaTimeSeconds;

        // 更新拖动管理器状态
        dragManager.update(deltaTimeSeconds);
        dragX = dragManager.getX();
        dragY = dragManager.getY();

        // 检查是否有由动作引起的参数更新
        boolean isMotionUpdated = false;

        // 加载上次保存的状态
        model.loadParameters();

        // 如果没有正在播放的动作，则随机播放一个待机动作
        if (motionManager.isFinished()) {
            startRandomMotion(LAppDefine.MotionGroup.IDLE.getId(), LAppDefine.Priority.IDLE.getPriority());
        } else {
            // 更新当前播放的动作
            isMotionUpdated = motionManager.updateMotion(model, deltaTimeSeconds);
        }

        // 保存模型的当前状态
        model.saveParameters();

        // 更新不透明度
        opacity = model.getModelOpacity();

        // 如果没有其他动作更新，则执行眨眼动作
        if (!isMotionUpdated && eyeBlink != null) {
            eyeBlink.updateParameters(model, deltaTimeSeconds);
        }

        // 更新表情动作
        if (expressionManager != null) {
            expressionManager.updateMotion(model, deltaTimeSeconds);
        }

        // 更新拖动功能，调整模型的面部和身体方向
        model.addParameterValue(idParamAngleX, dragX * 30);
        model.addParameterValue(idParamAngleY, dragY * 30);
        model.addParameterValue(idParamAngleZ, dragX * dragY * (-30));
        model.addParameterValue(idParamBodyAngleX, dragX * 10);

        // 更新眼球跟踪参数
        model.addParameterValue(idParamEyeBallX, dragX);
        model.addParameterValue(idParamEyeBallY, dragY);

        // 更新呼吸功能参数
        if (breath != null) {
            breath.updateParameters(model, deltaTimeSeconds);
        }

        // 更新物理效果
        if (physics != null) {
            physics.evaluate(model, deltaTimeSeconds);
        }



// 在update方法中，更新口型同步设置部分：
        if (lipSync) {
            mouthOpenTime += deltaTimeSeconds; // 累积时间
            Log.d("LipSync", "Delta Time: " + deltaTimeSeconds + ", Mouth Open Time: " + mouthOpenTime);

            if (mouthOpenTime >= 1.0f) { // 检查是否达到一秒钟
                mouthOpenTime -= 1.0f; // 减去一秒，而不是重置为0, 以保持更精确的计时
                isMouthOpen = !isMouthOpen; // 改变嘴巴的状态
                Log.d("LipSync", "Mouth open state changed: " + isMouthOpen);
            }

            // 根据嘴巴的状态设置参数的值
            float mouthFormValue = isMouthOpen ? 1.0f : 0.0f; // 形成嘴型的参数值
            float mouthOpenYValue = isMouthOpen ? 1.0f : 0.0f; // 嘴巴张开的参数值

            Log.d("LipSync", "Mouth Form Value: " + mouthFormValue + ", Mouth Open Y Value: " + mouthOpenYValue);

            // 获取参数ID
            CubismId paramMouthFormId = CubismFramework.getIdManager().getId("ParamMouthForm");
            CubismId paramMouthOpenYId = CubismFramework.getIdManager().getId("ParamMouthOpenY");

            // 打印参数ID是否获取成功
            Log.d("LipSync", "ParamMouthFormId: " + paramMouthFormId + ", ParamMouthOpenYId: " + paramMouthOpenYId);

            // 更新模型参数，控制嘴巴的开闭
            model.addParameterValue(paramMouthFormId, mouthFormValue);
            model.addParameterValue(paramMouthOpenYId, mouthOpenYValue);
        } else {
            Log.d("LipSync", "LipSync is not enabled.");
        }



        // 更新姿势参数
        if (pose != null) {
            pose.updateParameters(model, deltaTimeSeconds);
        }

        // 更新模型
        model.update();
    }


    /**
     * 引数で指定したモーションの再生を開始する。
     * コールバック関数が渡されなかった場合にそれをnullとして同メソッドを呼び出す。
     *
     * @param group モーショングループ名
     * @param number グループ内の番号
     * @param priority 優先度
     * @return 開始したモーションの識別番号を返す。個別のモーションが終了したか否かを判別するisFinished()の引数で使用する。開始できない時は「-1」
     */
    public int startMotion(final String group, int number, int priority) {
        return startMotion(group, number, priority, null);
    }

    /**
     * 引数で指定したモーションの再生を開始する。
     *
     * @param group モーショングループ名
     * @param number グループ内の番号
     * @param priority 優先度
     * @param onFinishedMotionHandler モーション再生終了時に呼び出されるコールバック関数。nullの場合は呼び出されない。
     * @return 開始したモーションの識別番号を返す。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は「-1」
     */
    public int startMotion(final String group,
                           int number,
                           int priority,
                           IFinishedMotionCallback onFinishedMotionHandler
    ) {
        if (priority == LAppDefine.Priority.FORCE.getPriority()) {
            motionManager.setReservationPriority(priority);
        } else if (!motionManager.reserveMotion(priority)) {
            if (debugMode) {
                LAppPal.printLog("Cannot start motion.");
            }
            return -1;
        }

        // ex) idle_0
        String name = group + "_" + number;

        CubismMotion motion = (CubismMotion) motions.get(name);

        if (motion == null) {
            String fileName = modelSetting.getMotionFileName(group, number);
            if (!fileName.equals("")) {
                String path = modelHomeDirectory + fileName;

                byte[] buffer;
                buffer = createBuffer(path);

                motion = loadMotion(buffer, onFinishedMotionHandler);
                if (motion != null) {
                    final float fadeInTime = modelSetting.getMotionFadeInTimeValue(group, number);

                    if (fadeInTime != -1.0f) {
                        motion.setFadeInTime(fadeInTime);
                    }

                    final float fadeOutTime = modelSetting.getMotionFadeOutTimeValue(group, number);
                    if (fadeOutTime != -1.0f) {
                        motion.setFadeOutTime(fadeOutTime);
                    }

                    motion.setEffectIds(eyeBlinkIds, lipSyncIds);
                }
            }
        } else {
            motion.setFinishedMotionHandler(onFinishedMotionHandler);
        }

        // load sound files
        String voice = modelSetting.getMotionSoundFileName(group, number);
        if (!voice.equals("")) {
            String path = modelHomeDirectory + voice;

            // 別スレッドで音声再生
            LAppWavFileHandler voicePlayer = new LAppWavFileHandler(path);
            voicePlayer.start();
        }

        if (debugMode) {
            LAppPal.printLog("start motion: " + group + "_" + number);
        }
        return motionManager.startMotionPriority(motion, priority);
    }

    /**
     * ランダムに選ばれたモーションの再生を開始する。
     * コールバック関数が渡されなかった場合にそれをnullとして同メソッドを呼び出す。
     *
     * @param group モーショングループ名
     * @param priority 優先度
     * @return 開始したモーションの識別番号。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は「-1」
     */
    public int startRandomMotion(final String group, int priority) {
        return startRandomMotion(group, priority, null);
    }

    /**
     * ランダムに選ばれたモーションの再生を開始する。
     *
     * @param group モーショングループ名
     * @param priority 優先度
     * @param onFinishedMotionHandler モーション再生終了時に呼び出されるコールバック関数。nullの場合は呼び出されない。
     * @return 開始したモーションの識別番号を返す。個別のモーションが終了したか否かを判定するisFinished()の引数で使用する。開始できない時は-1
     */
    public int startRandomMotion(final String group, int priority, IFinishedMotionCallback onFinishedMotionHandler) {
        if (modelSetting.getMotionCount(group) == 0) {
            return -1;
        }

        Random random = new Random();
        int number = random.nextInt(Integer.MAX_VALUE) % modelSetting.getMotionCount(group);

        return startMotion(group, number, priority, onFinishedMotionHandler);
    }

    public void draw(CubismMatrix44 matrix) {
        if (model == null) {
            return;
        }

        // キャッシュ変数の定義を避けるために、multiplyByMatrix()ではなく、multiply()を使用する。
        CubismMatrix44.multiply(
            modelMatrix.getArray(),
            matrix.getArray(),
            matrix.getArray()
        );

        this.<CubismRendererAndroid>getRenderer().setMvpMatrix(matrix);
        this.<CubismRendererAndroid>getRenderer().drawModel();
    }

    /**
     * 当たり判定テスト
     * 指定IDの頂点リストから矩形を計算し、座標が矩形範囲内か判定する
     *
     * @param hitAreaName 当たり判定をテストする対象のID
     * @param x 判定を行うx座標
     * @param y 判定を行うy座標
     * @return 当たっているならtrue
     */
    public boolean hitTest(final String hitAreaName, float x, float y) {
        // 透明時は当たり判定なし
        if (opacity < 1) {
            return false;
        }

        final int count = modelSetting.getHitAreasCount();
        for (int i = 0; i < count; i++) {
            if (modelSetting.getHitAreaName(i).equals(hitAreaName)) {
                final CubismId drawID = modelSetting.getHitAreaId(i);

                return isHit(drawID, x, y);
            }
        }
        // 存在しない場合はfalse
        return false;
    }

    /**
     * 引数で指定した表情モーションを設定する
     *
     * @param expressionID 表情モーションのID
     */
    public void setExpression(final String expressionID) {
        ACubismMotion motion = expressions.get(expressionID);

        if (debugMode) {
            LAppPal.printLog("expression: " + expressionID);
        }

        if (motion != null) {
            expressionManager.startMotionPriority(motion, LAppDefine.Priority.FORCE.getPriority());
        } else {
            if (debugMode) {
                LAppPal.printLog("expression " + expressionID + "is null");
            }
        }
    }

    /**
     * ランダムに選ばれた表情モーションを設定する
     */
    public void setRandomExpression() {
        if (expressions.size() == 0) {
            return;
        }

        Random random = new Random();
        int number = random.nextInt(Integer.MAX_VALUE) % expressions.size();

        int i = 0;
        for (String key : expressions.keySet()) {
            if (i == number) {
                setExpression(key);
                return;
            }
            i++;
        }
    }

    public CubismOffscreenSurfaceAndroid getRenderingBuffer() {
        return renderingBuffer;
    }

    /**
     * .moc3ファイルの整合性をチェックする。
     *
     * @param mocFileName MOC3ファイル名
     * @return MOC3に整合性があるかどうか。整合性があればtrue。
     */
    public boolean hasMocConsistencyFromFile(String mocFileName) {
        assert mocFileName != null && !mocFileName.isEmpty();

        String path = mocFileName;
        path = modelHomeDirectory + path;

        byte[] buffer = createBuffer(path);
        boolean consistency = CubismMoc.hasMocConsistency(buffer);

        if (!consistency) {
            CubismDebug.cubismLogInfo("Inconsistent MOC3.");
        } else {
            CubismDebug.cubismLogInfo("Consistent MOC3.");
        }

        return consistency;
    }

    private static byte[] createBuffer(final String path) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("create buffer: " + path);
        }
        return LAppPal.loadFileAsBytes(path);
    }
    // 从model3.json创建模型
    private void setupModel(ICubismModelSetting setting) {
        modelSetting = setting;

        isUpdated = true;
        isInitialized = false;

        // 加载Cubism模型
        {
            String fileName = modelSetting.getModelFileName();
            if (!fileName.equals("")) {
                String path = modelHomeDirectory + fileName;

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("创建模型: " + modelSetting.getModelFileName());
                }

                byte[] buffer = createBuffer(path);
                loadModel(buffer, mocConsistency);
            }
        }

        // 加载表达文件(.exp3.json)
        {
            if (modelSetting.getExpressionCount() > 0) {
                final int count = modelSetting.getExpressionCount();

                for (int i = 0; i < count; i++) {
                    String name = modelSetting.getExpressionName(i);
                    String path = modelSetting.getExpressionFileName(i);
                    path = modelHomeDirectory + path;

                    byte[] buffer = createBuffer(path);
                    CubismExpressionMotion motion = loadExpression(buffer);

                    expressions.put(name, motion);
                }
            }
        }

        // 物理效果
        {
            String path = modelSetting.getPhysicsFileName();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;
                byte[] buffer = createBuffer(modelPath);

                loadPhysics(buffer);
            }
        }

        // 姿势
        {
            String path = modelSetting.getPoseFileName();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;
                byte[] buffer = createBuffer(modelPath);
                loadPose(buffer);
            }
        }

        // 加载眨眼数据
        if (modelSetting.getEyeBlinkParameterCount() > 0) {
            eyeBlink = CubismEyeBlink.create(modelSetting);
        }

        // 设置眨眼间隔（单位：秒）
        eyeBlink.setBlinkingInterval(3.0f); // 例如，每4秒眨一次眼

// 设置眨眼动作的持续时间（单位：秒）
        eyeBlink.setBlinkingSettings(0.1f, 0.05f, 0.15f); // 关闭、闭眼保持、打开眼睛的时间

        // 加载呼吸数据
        breath = CubismBreath.create();
        List<CubismBreath.BreathParameterData> breathParameters = new ArrayList<CubismBreath.BreathParameterData>();

        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.getId()), 0.5f, 0.5f, 3.2345f, 0.5f));

        breath.setParameters(breathParameters);

        // 加载用户数据
        {
            String path = modelSetting.getUserDataFile();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;
                byte[] buffer = createBuffer(modelPath);
                loadUserData(buffer);
            }
        }

        // EyeBlinkIds
        int eyeBlinkIdCount = modelSetting.getEyeBlinkParameterCount();
        for (int i = 0; i < eyeBlinkIdCount; i++) {
            eyeBlinkIds.add(modelSetting.getEyeBlinkParameterId(i));
        }

        // LipSyncIds
        int lipSyncIdCount = modelSetting.getLipSyncParameterCount();
        for (int i = 0; i < lipSyncIdCount; i++) {
            lipSyncIds.add(modelSetting.getLipSyncParameterId(i));
        }

        if (modelSetting == null || modelMatrix == null) {
            LAppPal.printLog("模型设置失败。");
            return;
        }

        // 设置布局
        Map<String, Float> layout = new HashMap<String, Float>();

        // 如果存在布局信息，从该信息设置模型矩阵
        if (modelSetting.getLayoutMap(layout)) {
            layout.put("y", 10.0f); // 10.0f 是您希望移动的距离
            modelMatrix.setupFromLayout(layout);
        }

        model.saveParameters();

        // 加载动作
        for (int i = 0; i < modelSetting.getMotionGroupCount(); i++) {
            String group = modelSetting.getMotionGroupName(i);
            preLoadMotionGroup(group);
        }

        motionManager.stopAllMotions();

        isUpdated = false;
        isInitialized = true;
    }

    /**
     * モーションデータをグループ名から一括でロードする。
     * モーションデータの名前はModelSettingから取得する。
     *
     * @param group モーションデータのグループ名
     **/
    private void preLoadMotionGroup(final String group) {
        final int count = modelSetting.getMotionCount(group);

        for (int i = 0; i < count; i++) {
            // ex) idle_0
            String name = group + "_" + i;

            String path = modelSetting.getMotionFileName(group, i);
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;

                if (debugMode) {
                    LAppPal.printLog("load motion: " + path + "==>[" + group + "_" + i + "]");
                }

                byte[] buffer;
                buffer = createBuffer(modelPath);

                // If a motion cannot be loaded, a process is skipped.
                CubismMotion tmp = loadMotion(buffer);
                if (tmp == null) {
                    continue;
                }

                final float fadeInTime = modelSetting.getMotionFadeInTimeValue(group, i);

                if (fadeInTime != -1.0f) {
                    tmp.setFadeInTime(fadeInTime);
                }

                final float fadeOutTime = modelSetting.getMotionFadeOutTimeValue(group, i);

                if (fadeOutTime != -1.0f) {
                    tmp.setFadeOutTime(fadeOutTime);
                }

                tmp.setEffectIds(eyeBlinkIds, lipSyncIds);
                motions.put(name, tmp);
            }
        }
    }

    /**
     * OpenGLのテクスチャユニットにテクスチャをロードする
     */
    private void setupTextures() {
        for (int modelTextureNumber = 0; modelTextureNumber < modelSetting.getTextureCount(); modelTextureNumber++) {
            // テクスチャ名が空文字だった場合はロード・バインド処理をスキップ
            if (modelSetting.getTextureFileName(modelTextureNumber).equals("")) {
                continue;
            }

            // OpenGL ESのテクスチャユニットにテクスチャをロードする
            String texturePath = modelSetting.getTextureFileName(modelTextureNumber);
            texturePath = modelHomeDirectory + texturePath;

            LAppTextureManager.TextureInfo texture =
                LAppDelegate.getInstance()
                            .getTextureManager()
                            .createTextureFromPngFile(texturePath);
            final int glTextureNumber = texture.id;

            this.<CubismRendererAndroid>getRenderer().bindTexture(modelTextureNumber, glTextureNumber);

            if (LAppDefine.PREMULTIPLIED_ALPHA_ENABLE) {
                this.<CubismRendererAndroid>getRenderer().isPremultipliedAlpha(true);
            } else {
                this.<CubismRendererAndroid>getRenderer().isPremultipliedAlpha(false);
            }
        }
    }

    private ICubismModelSetting modelSetting;
    /**
     * モデルのホームディレクトリ
     */
    private String modelHomeDirectory;
    /**
     * デルタ時間の積算値[秒]
     */
    private float userTimeSeconds;

    /**
     * モデルに設定されたまばたき機能用パラメーターID
     */
    private final List<CubismId> eyeBlinkIds = new ArrayList<CubismId>();
    /**
     * モデルに設定されたリップシンク機能用パラメーターID
     */
    private final List<CubismId> lipSyncIds = new ArrayList<CubismId>();
    /**
     * 読み込まれているモーションのマップ
     */
    private final Map<String, ACubismMotion> motions = new HashMap<String, ACubismMotion>();
    /**
     * 読み込まれている表情のマップ
     */
    private final Map<String, ACubismMotion> expressions = new HashMap<String, ACubismMotion>();

    /**
     * パラメーターID: ParamAngleX
     */
    private final CubismId idParamAngleX;
    /**
     * パラメーターID: ParamAngleY
     */
    private final CubismId idParamAngleY;
    /**
     * パラメーターID: ParamAngleZ
     */
    private final CubismId idParamAngleZ;
    /**
     * パラメーターID: ParamBodyAngleX
     */
    private final CubismId idParamBodyAngleX;
    /**
     * パラメーターID: ParamEyeBallX
     */
    private final CubismId idParamEyeBallX;
    /**
     * パラメーターID: ParamEyeBallY
     */
    private final CubismId idParamEyeBallY;

    /**
     * フレームバッファ以外の描画先
     */
    private final CubismOffscreenSurfaceAndroid renderingBuffer = new CubismOffscreenSurfaceAndroid();
}
