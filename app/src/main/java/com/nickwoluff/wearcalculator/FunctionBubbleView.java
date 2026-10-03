package com.nickwoluff.wearcalculator;

import android.content.Context;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * 通用「长按选择」气泡。
 *
 * <p>长按某个科学函数键时弹出来，**浮在按键区上方那片空白里、水平居中**，
 * 里面并排若干选项，全部同时完整显示，点哪个选哪个，选中的高亮。
 * 目前用在两处：
 * <ul>
 *     <li>对数键：ln / lg / log(底数, 真数)</li>
 *     <li>sin、cos、tan 三个键：例如 sin / asin / sinh / asinh</li>
 * </ul>
 *
 * <p>气泡挂在 Activity 的根布局上（全屏大小、背景透明），点空白处也能收起。
 * 承载层平时是 GONE —— 里面的遮罩是全屏可点的，一直显示会把整个界面的触摸都吃掉。
 */
public class FunctionBubbleView extends LinearLayout {

    /** 一个可选项：按钮上显示的短名 + 真正插进算式的文本。 */
    public static final class Choice {
        final String display;
        final String insert;
        final String description;

        public Choice(String display, String insert, String description) {
            this.display = display;
            this.insert = insert;
            this.description = description;
        }
    }

    /** 选中回调。 */
    public interface OnChoicePickedListener {
        void onChoicePicked(String display, String insert);
    }

    /**
     * 气泡显示/隐藏回调。
     * 外层靠它把全屏遮罩一起显示或隐藏 —— 遮罩必须只在气泡可见时存在，
     * 否则会把整个界面的触摸全部吃掉。
     */
    public interface OnBubbleVisibilityListener {
        void onBubbleVisibilityChanged(boolean shown);
    }

    private static final long ANIM_DURATION = 220L;
    /** 气泡底边与按键区之间留的空隙（dp）。 */
    private static final int ANCHOR_GAP_DP = 6;
    /** 圆屏可见区域约占屏宽的比例（内接正方形的一半再留点余量）。 */
    private static final float ROUND_SAFE_RATIO = 0.16f;

    private final LinearLayout optionsRow;
    private final int itemWidth;
    private final int itemHeight;
    private final int anchorGap;
    private final float textSizeSp;

    private OnChoicePickedListener listener;
    private OnBubbleVisibilityListener visibilityListener;

    private int currentIndex = 0;
    private boolean visible;
    /** 按键区顶边（相对承载层），气泡不会越过它跑到按键上。 */
    private Integer keypadTop;

    /**
     * @param itemWidthDp  每个选项的宽度
     * @param itemHeightDp 每个选项的高度
     * @param textSizeSp   选项文字大小
     */
    public FunctionBubbleView(Context context, int itemWidthDp, int itemHeightDp, float textSizeSp) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_log_bubble);
        setClickable(true);
        setVisibility(GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(dp(8));
        }

        this.itemWidth = dp(itemWidthDp);
        this.itemHeight = dp(itemHeightDp);
        this.textSizeSp = textSizeSp;
        anchorGap = dp(ANCHOR_GAP_DP);

        View content = LayoutInflater.from(context).inflate(R.layout.view_function_bubble, this, false);
        optionsRow = content.findViewById(R.id.bubbleOptions);
        addView(content);
    }

    /** 设置候选项。会重建里面的按钮，所以每次弹出前调用一次即可。 */
    public void setChoices(java.util.List<Choice> choices, String currentInsert) {
        optionsRow.removeAllViews();
        currentIndex = 0;
        for (int i = 0; i < choices.size(); i++) {
            final int index = i;
            final Choice choice = choices.get(i);

            Button option = (Button) LayoutInflater.from(getContext())
                    .inflate(R.layout.item_bubble_option, optionsRow, false);
            option.setText(choice.display);
            option.setContentDescription(choice.description);
            option.setTextSize(textSizeSp);
            option.setLayoutParams(new LinearLayout.LayoutParams(itemWidth, itemHeight));
            option.setOnClickListener(v -> {
                select(index);
                if (listener != null) listener.onChoicePicked(choice.display, choice.insert);
                hide();
            });
            optionsRow.addView(option);

            if (choice.insert.equals(currentInsert)) currentIndex = i;
        }
        select(currentIndex);
    }

    public void setOnChoicePickedListener(OnChoicePickedListener listener) {
        this.listener = listener;
    }

    public void setOnBubbleVisibilityListener(OnBubbleVisibilityListener listener) {
        this.visibilityListener = listener;
    }

    public boolean isBubbleVisible() {
        return visible;
    }

    /** 在按键上方那片空白里弹出气泡（水平居中）。 */
    public void show(final View anchor) {
        show(anchor, null);
    }

    /**
     * 弹出气泡。
     *
     * <p>不是贴在按键上，而是浮在**按键区上方那片空白**里、水平居中：
     * 贴着按键往上放会顶到圆屏被裁掉的角落，贴在键上又会挡住旁边的键。
     *
     * @param anchor    触发它的按键（决定动画从哪边"长"出来）
     * @param keypadTop 按键区顶边的 y 坐标（相对承载层）；气泡不会被摆到它下面
     */
    public void show(final View anchor, final Integer keypadTop) {
        this.keypadTop = keypadTop;
        if (visible) {
            hide();
            return;
        }
        visible = true;
        if (visibilityListener != null) visibilityListener.onBubbleVisibilityChanged(true);
        setVisibility(VISIBLE);
        setAlpha(0f);
        setScaleX(0.8f);
        setScaleY(0.8f);

        post(() -> {
            positionAround(anchor);
            setPivotX(getWidth() / 2f);
            setPivotY(anchor.getTop() >= getY() ? 0f : getHeight());
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(ANIM_DURATION)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
        });
        tapHaptic();
    }

    /** 气泡收起。 */
    public void hide() {
        if (!visible) return;
        visible = false;
        animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    setVisibility(GONE);
                    if (visibilityListener != null) visibilityListener.onBubbleVisibilityChanged(false);
                })
                .start();
    }

    /**
     * 把气泡摆在「按键上方的那片空白」里，水平居中。
     *
     * <p>三个约束：水平取屏幕正中；底边不超过按键区顶边；圆屏还要避开被表盘裁掉的四角。
     */
    private void positionAround(View anchor) {
        View parentView = (View) getParent();
        int parentWidth = parentView != null ? parentView.getWidth() : 0;
        int parentHeight = parentView != null ? parentView.getHeight() : 0;
        if (parentWidth == 0 || parentHeight == 0) {
            parentWidth = getResources().getDisplayMetrics().widthPixels;
            parentHeight = getResources().getDisplayMetrics().heightPixels;
        }

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        int safeTop = 0;
        int safeBottom = parentHeight;
        if (isRoundScreen()) {
            int inset = Math.round(parentWidth * ROUND_SAFE_RATIO);
            safeTop = inset;
            safeBottom = parentHeight - inset;
        }
        int limitBottom = safeBottom;
        if (keypadTop != null) limitBottom = Math.min(limitBottom, keypadTop);

        int left = (parentWidth - width) / 2;
        left = Math.max(0, Math.min(left, Math.max(0, parentWidth - width)));

        int top = limitBottom - height - anchorGap;
        if (top < safeTop) top = safeTop;

        setX(left);
        setY(top);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED));
    }

    /** 更新选中态：所有选项并排显示，选中的那个高亮。 */
    private void select(int index) {
        currentIndex = index;
        for (int i = 0; i < optionsRow.getChildCount(); i++) {
            optionsRow.getChildAt(i).setSelected(i == index);
        }
    }

    private void tapHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private boolean isRoundScreen() {
        return getResources().getConfiguration().isScreenRound();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
