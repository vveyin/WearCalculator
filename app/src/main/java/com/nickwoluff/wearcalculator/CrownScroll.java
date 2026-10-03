package com.nickwoluff.wearcalculator;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/**
 * 表冠 / 滚轮滚动适配。
 *
 * <p>按需求刻意做得「不挑来源」：
 * <ul>
 *     <li>只认 {@link MotionEvent#ACTION_SCROLL} 且轴值非 0，<b>完全不判断 source</b>，
 *         所以旋钮、滚轮、触控板、鼠标滚轮都能用；</li>
 *     <li>累积起来不满一整像素就先存着（{@link #pending}），够一整像素才真的滚，
 *         避免细小抖动被吃掉；</li>
 *     <li>比例是「一格（一个轴单位）滚 1dp」，换算成像素就是 density。</li>
 * </ul>
 * 监听挂在真实的 View 上（{@code setOnGenericMotionListener}）。
 */
public final class CrownScroll {

    private final View target;
    private final boolean vertical;
    private final float pixelsPerDetent;

    /** 不足一整像素的余量，先攒着。 */
    private float pending;

    private CrownScroll(View target, boolean vertical) {
        this.target = target;
        this.vertical = vertical;
        this.pixelsPerDetent = target.getResources().getDisplayMetrics().density;
    }

    /** 竖直滚动（历史列表用）。 */
    public static CrownScroll vertical(View target) {
        return new CrownScroll(target, true);
    }

    /** 水平滚动（顶部算式那一条用）。 */
    public static CrownScroll horizontal(View target) {
        return new CrownScroll(target, false);
    }

    /** 把监听挂到 View 上。 */
    public void attach() {
        target.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_SCROLL) return false;
            // 依次尝试常见滚动轴，取第一个非 0 的
            float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
            if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if (delta == 0f) return false;

            // 表冠顺时针 = 正值，按习惯内容往上/右走
            accumulateAndScroll(-delta);
            return true;
        });
    }

    /** 累积到一整像素再滚。 */
    private void accumulateAndScroll(float detents) {
        pending += detents * pixelsPerDetent;
        int step = (int) pending;
        if (step == 0) return;
        pending -= step;

        if (vertical) target.scrollBy(0, step);
        else target.scrollBy(step, 0);
    }

    /** 仅供调试：当前攒了多少像素。 */
    float pendingPixels() {
        return pending;
    }

    /** 兼容旧写法：判断事件是否来自表冠（本实现刻意不用它，保留给别处参考）。 */
    public static boolean isRotary(MotionEvent event) {
        return event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER);
    }
}
