/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.sdk.cubism.framework.math;

/**
 * This class provides face orientation control functionality.
 */
public class CubismTargetPoint {
    /**
     * 更新面部方向控制。
     *
     * @param deltaTimeSeconds 间隔时间[秒]
     */
    public void update(float deltaTimeSeconds) {
        // 加上间隔时间
        userTimeSeconds += deltaTimeSeconds;

        // 考虑到从中心摇摆到左右时的平均速度，设置最大速度。
        // 面部的摇摆从中心（0.0）设置到左右（+-1.0）。
        final float faceParamMaxV = 40.0f / 10.0f;
        // 每帧可改变的速度的上限。
        final float maxV = faceParamMaxV / FRAME_RATE;

        if (lastTimeSeconds == 0.0f) {
            lastTimeSeconds = userTimeSeconds;
            return;
        }

        final float deltaTimeWeight = (userTimeSeconds - lastTimeSeconds) * FRAME_RATE;
        lastTimeSeconds = userTimeSeconds;

        // 计算达到最大速度所需的时间。
        final float timeToMaxSpeed = 0.15f;
        final float frameToMaxSpeed = timeToMaxSpeed * FRAME_RATE; // 秒 * 帧/秒
        final float maxA = deltaTimeWeight * maxV / frameToMaxSpeed;

        final float dx = faceTargetX - faceX;
        final float dy = faceTargetY - faceY;

        // 如果没有变化。
        if (CubismMath.absF(dx) <= EPSILON && CubismMath.absF(dy) <= EPSILON) {
            return;
        }

        // 如果速度大于最大速度，则降低速度。
        final float d = CubismMath.sqrtF((dx * dx) + (dy * dy));

        // 行驶方向的最大速度矢量。
        final float vx = maxV * dx / d;
        final float vy = maxV * dy / d;

        // 从当前速度计算到新速度的变化（加速度）。
        float ax = vx - faceVX;
        float ay = vy - faceVY;

        float a = CubismMath.sqrtF((ax * ax) + (ay * ay));

        // 加速度处理。
        if (a < -maxA || a > maxA) {
            ax *= maxA / a;
            ay *= maxA / a;
        }

        // 将加速度添加到原始速度以获得新速度。
        faceVX += ax;
        faceVY += ay;

        // 当接近所需方向时，为了平滑减速的处理
        // 根据一个物体在设定的加速度下停止的距离和速度之间的关系，计算当前可用的最大速度，并在速度大于该值时减速。
        // 人类本质上更灵活，因为他们可以用肌肉力量调整力量（加速度），但这是一个简单的过程。
        {
            // 加速度、速度和距离之间的关系表达式。
            final float maxV2 = 0.5f * (CubismMath.sqrtF((maxA * maxA) + 16.0f * maxA * d - 8.0f * maxA * d) - maxA);
            final float curV = CubismMath.sqrtF((faceVX * faceVX) + (faceVY * faceVY));

            // 当前速度 > 最大速度时，减速到最大速度。
            if (curV > maxV2) {
                faceVX *= maxV2 / curV;
                faceVY *= maxV2 / curV;
            }
        }
        faceX += faceVX;
        faceY += faceVY;
    }


    /**
     * Get the face orientation value on the X-axis.
     *
     * @return X-axis face orientation value (-1.0 - 1.0)
     */
    public float getX() {
        return faceX;
    }

    /**
     * Get the face orientation value on the Y-axis.
     *
     * @return Y-axis face orientation value (-1.0 - 1.0)
     */
    public float getY() {
        return faceY;
    }

    /**
     * 设置面部方向的目标值。
     *
     * @param x X轴面部方向值（-1.0 - 1.0）
     * @param y Y轴面部方向值（-1.0 - 1.0）
     */
    public void set(float x, float y) {
        // 设置X轴面部方向的目标值
        faceTargetX = x;
        // 设置Y轴面部方向的目标值
        faceTargetY = y;
    }
    /**
     * Framerate per seconds[s]
     */
    private static final int FRAME_RATE = 30;
    /**
     * Epsilon value
     */
    private static final float EPSILON = 0.01f;

    /**
     * X target value for face orientation (getting closer to this value)
     */
    private float faceTargetX;
    /**
     * Y target value for face orientation (getting closer to this value)
     */
    private float faceTargetY;
    /**
     * face orientation X (-1.0 - 1.0)
     */
    private float faceX;
    /**
     * face orientation Y (-1.0 - 1.0)
     */
    private float faceY;
    /**
     * speed of change in face orientation X
     */
    private float faceVX;
    /**
     * speed of change in face orientation Y
     */
    private float faceVY;
    /**
     * last executed time[s]
     */
    private float lastTimeSeconds;
    /**
     * total elapsed time[s]
     */
    private float userTimeSeconds;
}
