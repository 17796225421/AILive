/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.example.ailive.live2d;

import static com.example.ailive.live2d.LAppDefine.*;

import android.content.res.AssetManager;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * サンプルアプリケーションにおいてCubismModelを管理するクラス。
 * モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
 */
public class LAppLive2DManager {
    public static LAppLive2DManager getInstance() {
        if (s_instance == null) {
            s_instance = new LAppLive2DManager();
        }
        return s_instance;
    }

    public static void releaseInstance() {
        s_instance = null;
    }

    /**
     * 現在のシーンで保持している全てのモデルを解放する
     */
    public void releaseAllModel() {
        for (LAppModel model : models) {
            model.deleteModel();
        }
        models.clear();
    }

    /**
     * assets フォルダにあるモデルフォルダ名をセットする
     */
    public void setUpModel() {
        // assetsフォルダの中にあるフォルダ名を全てクロールし、モデルが存在するフォルダを定義する。
        // フォルダはあるが同名の.model3.jsonが見つからなかった場合はリストに含めない。
        modelDir.clear();

        final AssetManager assets = LAppDelegate.getInstance().getActivity().getResources().getAssets();
        try {
            String[] root = assets.list("");
            for (String subdir: root) {
                String[] files = assets.list(subdir);
                String target = subdir + ".model3.json";
                // フォルダと同名の.model3.jsonがあるか探索する
                for (String file : files) {
                    if (file.equals(target)) {
                        modelDir.add(subdir);
                        break;
                    }
                }
            }
            Collections.sort(modelDir);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // 执行模型的更新和绘制处理
    public void onUpdate() {
        // 获取窗口的宽度和高度
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();

        // 遍历所有模型
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);

            if (model.getModel() == null) {
                LAppPal.printLog("Failed to model.getModel().");
                continue;
            }

            // 重置投影矩阵
            projection.loadIdentity();

            // 如果模型的画布宽度大于1.0且窗口宽度小于高度（竖屏）
            if (model.getModel().getCanvasWidth() > 1.0f && width < height) {
                // 当要在竖屏上显示宽模型时，根据模型的宽度来计算缩放比例
                model.getModelMatrix().setWidth(2.0f);
                projection.scale(1.0f, (float) width / (float) height);

                // 将模型中心点向下移动
                float yOffset = -0.2f; // 根据您的需求调整此值
                model.getModelMatrix().translateY(yOffset);
            } else {
                // 根据窗口的宽高比来调整投影矩阵
                projection.scale((float) height / (float) width, 1.0f);
            }

            // 如果viewMatrix不为空，则将其乘以投影矩阵
            if (viewMatrix != null) {
                viewMatrix.multiplyByMatrix(projection);
            }

            // 模型绘制前的处理
            LAppDelegate.getInstance().getView().preModelDraw(model);

            // 更新模型
            model.update();

            // 绘制模型（注意，传入的投影矩阵可能会被修改）
            model.draw(projection);

            // 模型绘制后的处理
            LAppDelegate.getInstance().getView().postModelDraw(model);
        }
    }

    /**
     * 画面をドラッグした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    public void onDrag(float x, float y) {
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = getModel(i);
            model.setDragging(x, y);
        }
    }

    /**
     * 画面をタップした時の処理
     *
     * @param x 画面のx座標
     * @param y 画面のy座標
     */
    public void onTap(float x, float y) {
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("tap point: {" + x + ", y: " + y);
        }

        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);

            // 頭をタップした場合表情をランダムで再生する
            if (model.hitTest(HitAreaName.HEAD.getId(), x, y)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: " + HitAreaName.HEAD.getId());
                }
                model.setRandomExpression();
            }
            // 体をタップした場合ランダムモーションを開始する
            else if (model.hitTest(HitAreaName.BODY.getId(), x, y)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: " + HitAreaName.HEAD.getId());
                }

                model.startRandomMotion(MotionGroup.TAP_BODY.getId(), Priority.NORMAL.getPriority(), finishedMotion);
            }
        }
    }

    /**
     * 次のシーンに切り替える
     * サンプルアプリケーションではモデルセットの切り替えを行う
     */
    public void nextScene() {
        final int number = (currentModel + 1) % modelDir.size();

        changeScene(number);
    }

    /**
     * 切换场景。
     *
     * @param index 要切换的场景索引
     */
    public void changeScene(int index) {
        currentModel = index;
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("模型索引: " + currentModel);
        }

        String modelDirName = modelDir.get(index);

        String modelPath = ResourcePath.ROOT.getPath() + modelDirName + "/";
        String modelJsonName = modelDirName + ".model3.json";

        releaseAllModel();

        models.add(new LAppModel());
        models.get(0).loadAssets(modelPath, modelJsonName);

        /*
         * 提供将模型显示为半透明的示例。
         * 在这里，如果定义了USE_RENDER_TARGET和USE_MODEL_RENDER_TARGET，
         * 它将在另一个渲染目标上绘制模型，并将绘图结果作为纹理粘贴到另一个精灵上。
         */
        LAppView.RenderingTarget useRenderingTarget;
        if (USE_RENDER_TARGET) {
            // 如果要在LAppView所持有的目标上进行绘制，则选择此选项
            useRenderingTarget = LAppView.RenderingTarget.VIEW_FRAME_BUFFER;
        } else if (USE_MODEL_RENDER_TARGET) {
            // 如果要在每个LAppModel所持有的目标上进行绘制，则选择此选项
            useRenderingTarget = LAppView.RenderingTarget.MODEL_FRAME_BUFFER;
        } else {
            // 渲染到默认的主帧缓冲区（常规）
            useRenderingTarget = LAppView.RenderingTarget.NONE;
        }

        if (USE_RENDER_TARGET || USE_MODEL_RENDER_TARGET) {
            // 作为为模型单独添加α的示例，创建另一个模型并稍微移动其位置。
            models.add(new LAppModel());
            models.get(1).loadAssets(modelPath, modelJsonName);
            models.get(1).getModelMatrix().translateX(0.2f);
        }

        // 切换渲染目标
        LAppDelegate.getInstance().getView().switchRenderingTarget(useRenderingTarget);

        // 选择了其他渲染目标时的背景清除色
        float[] clearColor = {1.0f, 1.0f, 1.0f};
        LAppDelegate.getInstance().getView().setRenderingTargetClearColor(clearColor[0], clearColor[1], clearColor[2]);
    }


    /**
     * 現在のシーンで保持しているモデルを返す
     *
     * @param number モデルリストのインデックス値
     * @return モデルのインスタンスを返す。インデックス値が範囲外の場合はnullを返す
     */
    public LAppModel getModel(int number) {
        if (number < models.size()) {
            return models.get(number);
        }
        return null;
    }

    /**
     * シーンインデックスを返す
     *
     * @return シーンインデックス
     */
    public int getCurrentModel() {
        return currentModel;
    }

    /**
     * Return the number of models in this LAppLive2DManager instance has.
     *
     * @return number fo models in this LAppLive2DManager instance has. If models list is null, return 0.
     */
    public int getModelNum() {
        if (models == null) {
            return 0;
        }
        return models.size();
    }

    /**
     * モーション終了時に実行されるコールバック関数
     */
    private static class FinishedMotion implements IFinishedMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("Motion Finished: " + motion);
        }
    }

    private static final FinishedMotion finishedMotion = new FinishedMotion();

    /**
     * シングルトンインスタンス
     */
    private static LAppLive2DManager s_instance;

    private LAppLive2DManager() {
        setUpModel();
        changeScene(0);
    }

    private final List<LAppModel> models = new ArrayList<>();

    /**
     * 表示するシーンのインデックス値
     */
    private int currentModel;

    /**
     * モデルディレクトリ名
     */
    private final List<String> modelDir = new ArrayList<>();

    // onUpdateメソッドで使用されるキャッシュ変数
    private final CubismMatrix44 viewMatrix = CubismMatrix44.create();
    private final CubismMatrix44 projection = CubismMatrix44.create();
}
