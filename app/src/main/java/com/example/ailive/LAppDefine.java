/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.example.ailive;

import com.live2d.sdk.cubism.framework.CubismFrameworkConfig.LogLevel;

/**
 * Constants used in this sample app.
 */
public class LAppDefine {
    /**
     * Scaling rate.
     */
    public enum Scale {
        /**
         * Default scaling rate
         */
        DEFAULT(1.0f),
        /**
         * Maximum scaling rate
         */
        MAX(2.0f),
        /**
         * Minimum scaling rate
         */
        MIN(0.8f);

        private final float value;

        Scale(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }

    /**
     * Logical view coordinate system.
     */
    public enum LogicalView {
        /**
         * Left end
         */
        LEFT(-1.0f),
        /**
         * Right end
         */
        RIGHT(1.0f),
        /**
         * Bottom end
         */
        BOTTOM(-1.0f),
        /**
         * Top end
         */
        TOP(1.0f);

        private final float value;

        LogicalView(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }

    /**
     * Maximum logical view coordinate system.
     */
    public enum MaxLogicalView {
        /**
         * Maximum left end
         */
        LEFT(-2.0f),
        /**
         * Maximum right end
         */
        RIGHT(2.0f),
        /**
         * Maximum bottom end
         */
        BOTTOM(-2.0f),
        /**
         * Maximum top end
         */
        TOP(2.0f);

        private final float value;

        MaxLogicalView(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }

    /**
     * Path of image materials.
     */
    public enum ResourcePath {
        /**
         * Relative path of the material directory
         */
        ROOT(""),
        /**
         * Background image file
         */
        BACK_IMAGE("back_class_normal.png");
        private final String path;

        ResourcePath(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    /**
     * Motion group
     */
    public enum MotionGroup {
        /**
         * ID of the motion to be played at idling.
         */
        IDLE("Idle"),
        /**
         * ID of the motion to be played at tapping body.
         */
        TAP_BODY("TapBody");

        private final String id;

        MotionGroup(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * [Head] tag for hit detection.
     * (Match with external definition file(json))
     */
    public enum HitAreaName {
        HEAD("Head"),
        BODY("Body");

        private final String id;

        HitAreaName(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * Motion priority
     */
    public enum Priority {
        NONE(0),
        IDLE(1),
        NORMAL(2),
        FORCE(3);

        private final int priority;

        Priority(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * 是否验证MOC3的整合性。如果要验证则为true。
     */
    public static final boolean MOC_CONSISTENCY_VALIDATION_ENABLE = true;

    /**
     * 启用/禁用调试日志。
     */
    public static final boolean DEBUG_LOG_ENABLE = true;
    /**
     * 启用/禁用触摸信息处理的调试日志。
     */
    public static final boolean DEBUG_TOUCH_LOG_ENABLE = true;
    /**
     * 设置Framework的日志输出级别。
     */
    public static final LogLevel cubismLoggingLevel = LogLevel.VERBOSE;
    /**
     * 启用/禁用预乘alpha。
     */
    public static final boolean PREMULTIPLIED_ALPHA_ENABLE = true;

    /**
     * 标记是否绘制到LAppView持有的目标。(如果USE_RENDER_TARGET
     * 和USE_MODEL_RENDER_TARGET都为true，那么此变量的优先级高于USE_MODEL_RENDER_TARGET。)
     */
    public static final boolean USE_RENDER_TARGET = false;
    /**
     * 标记是否绘制到每个LAppModel拥有的目标。
     */
    public static final boolean USE_MODEL_RENDER_TARGET = false;
}
