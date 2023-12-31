/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.sdk.cubism.framework.math;

import android.util.Log;

import java.util.Map;

/**
 * 4x4 matrix class for setting model coordinates.
 */
public class CubismModelMatrix extends CubismMatrix44 {
    /**
     * 使用传入的宽度和高度创建一个新的CubismModelMatrix实例。
     *
     * @param w 宽度
     * @param h 高度
     * @return 带有指定宽度和高度的CubismModelMatrix实例
     * @throws IllegalArgumentException 如果参数等于0或小于0
     */
    public static CubismModelMatrix create(float w, float h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("宽度或高度等于0或小于0。");
        }
        return new CubismModelMatrix(w, h);
    }


    /**
     * Create the new CubismModelMatrix instance from the CubismModelMatrix instance.
     * It works the same way as a copy constructor.
     *
     * @param modelMatrix CubismModelMatrix instance to be copied
     * @return Copied CubismModelMatrix instance
     */
    public static CubismModelMatrix create(CubismModelMatrix modelMatrix) {
        return new CubismModelMatrix(modelMatrix);
    }

    /**
     * Set the width.
     *
     * @param w width
     */
    public void setWidth(float w) {
        final float scaleX = w / width;
        scale(scaleX, scaleX);
    }

    /**
     * Set the height.
     *
     * @param h height
     */
    public void setHeight(float h) {
        final float scaleX = h / height;
        scale(scaleX, scaleX);
    }

    /**
     * 设置位置。
     *
     * @param x X轴的位置
     * @param y Y轴的位置
     */
    public void setPosition(float x, float y) {
        translate(x, y);
    }

    /**
     * 设置中心位置。
     * 在使用此方法之前，请确保设置了宽度或高度。
     *
     * @param x X轴的中心位置
     * @param y Y轴的中心位置
     */
    public void setCenterPosition(float x, float y) {
        centerX(x);
        centerY(y);
    }

    /**
     * Set the position of the upper edge.
     *
     * @param y the position of the upper edge
     */
    public void top(float y) {
        setY(y);
    }

    /**
     * Set the position of the bottom edge.
     *
     * @param y the position of the bottom edge
     */
    public void bottom(float y) {
        final float h = height * getScaleY();
        translateY(y - h);
    }

    /**
     * Set the position of the left edge.
     *
     * @param x the position of the left edge
     */
    public void left(float x) {
        setX(x);
    }

    /**
     * Set the position of the right edge.
     *
     * @param x the position of the right edge
     */
    public void right(float x) {
        final float w = width * getScaleX();
        translateX(x - w);
    }

    /**
     * 设置X轴的中心位置。
     *
     * @param x X轴的中心位置
     */
    public void centerX(float x) {
        final float w = width * getScaleX();
        translateX(x - (w / 2.0f));
    }

    /**
     * Set the position of X-axis.
     *
     * @param x position of X-axis
     */
    public void setX(float x) {
        translateX(x);
    }

    /**
     * Set the center position of Y-axis.
     *
     * @param y center position of Y-axis
     */
    public void centerY(float y) {
        final float h = height * getScaleY();
        translateY(y - (h / 2.0f));
    }

    /**
     * Set the position of Y-axis.
     *
     * @param y position of Y-axis
     */
    public void setY(float y) {
        translateY(y);
    }

    /**
     * 根据布局信息设置位置。
     *
     * @param layout 布局信息
     */
    public void setupFromLayout(Map<String, Float> layout) {
        final String keyWidth = "width";
        final String keyHeight = "height";
        final String keyX = "x";
        final String keyY = "y";
        final String keyCenterX = "center_x";
        final String keyCenterY = "center_y";
        final String keyTop = "top";
        final String keyBottom = "bottom";
        final String keyLeft = "left";
        final String keyRight = "right";

        for (Map.Entry<String, Float> entry : layout.entrySet()) {
            String key = entry.getKey();
            if (key.equals(keyWidth)) {
                setWidth(entry.getValue());
            } else if (key.equals(keyHeight)) {
                setHeight(entry.getValue());
            }
        }

        for (Map.Entry<String, Float> entry : layout.entrySet()) {
            String key = entry.getKey();
            float value = entry.getValue();
            Log.i("zhouzihong", key + ":" + value);
            if (key.equals(keyX)) {
                setX(value);
            } else if (key.equals(keyY)) {
                setY(value);
            } else if (key.equals(keyCenterX)) {
                centerX(value);
            } else if (key.equals(keyCenterY)) {
                centerY(value);
            } else if (key.equals(keyTop)) {
                top(value);
            } else if (key.equals(keyBottom)) {
                bottom(value);
            } else if (key.equals(keyLeft)) {
                left(value);
            } else if (key.equals(keyRight)) {
                right(value);
            }
        }
    }

    /**
     * Constructor
     */
    private CubismModelMatrix(float w, float h) {
        super();
        width = w;
        height = h;

        setHeight(2.0f);
    }

    /**
     * Copy constructor
     *
     * @param modelMatrix model matrix to be copied
     */
    private CubismModelMatrix(CubismModelMatrix modelMatrix) {
        super();
        System.arraycopy(modelMatrix.tr, 0, this.tr, 0, 16);
        width = modelMatrix.width;
        height = modelMatrix.height;
    }

    /**
     * width
     */
    private final float width;
    /**
     * height
     */
    private final float height;
}
