package com.nickwoluff.wearcalculator;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.wear.widget.CurvedTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 嗷呜计算器主界面。
 *
 * <p>按键一共两套，点顶部的时间显示区域切换：
 * <ul>
 *     <li><b>普通</b>：原来的 + − × ÷ 计算器；</li>
 *     <li><b>科学</b>：7→( 8→) 9→! ÷→^ 4→sin 5→cos 6→tan −→e ×→π 1→对数 2→% 
 *         3→Ans +→± .→his，0 键显示并切换角度单位。</li>
 * </ul>
 *
 * <p>几个交互：
 * <ul>
 *     <li>点 0 键（科学模式）：在 DEG / RAD 之间切换；</li>
 *     <li>长按 sin / cos / tan / 对数键：弹出气泡，浮在按键区上方的空白里，点选具体函数；</li>
 *     <li>长按乘方键（÷ 的位置）：在 "^" 和 "," 之间换，算式里的逗号只能这么打；</li>
 *     <li>±：把算式里最右边那个数的正负号翻过来；</li>
 *     <li>his：打开历史记录页面（记录开关 + 列表 + 返回）；</li>
 *     <li>顶部算式条和历史列表都支持表冠滚动。</li>
 * </ul>
 */
public class MainActivity extends Activity {

    /** 顶部时间点击切换的两套按键。 */
    private static final int MODE_BASIC = 0;
    private static final int MODE_SCIENTIFIC = 1;

    /** 显示区上可能出现的两个角度单位标记。 */
    private static final String CHIP_DEG = "DEG";
    private static final String CHIP_RAD = "RAD";

    /** 科学模式下的字号：长标签用小一号，短标签（± his Ans）用中号。 */
    private static final float SCI_FONT_SHORT = 15f;
    private static final float SCI_FONT_FUNCTION = 13f;
    /** 0 键显示 DEG / RAD 时的字号。 */
    private static final float CHIP_FONT = 13f;

    /** 临时提示（角度单位、报错原因）显示多久后自动清空。 */
    private static final long HINT_CLEAR_DELAY_MS = 2000L;
    /** 圆屏可见区域约占屏宽的比例（内接正方形的一半再留点余量）。 */
    private static final float ROUND_SAFE_RATIO = 0.16f;
    /** 历史记录最多留多少条。 */
    private static final int MAX_HISTORY = 100;

    private static final String PREFS = "wearcalc";
    /** 「记录历史」开关的状态。默认 true = 照常记录。 */
    private static final String KEY_RECORD_HISTORY = "record_history";

    // 算式区的函数名（插进算式用的文本）与按钮上显示的短名
    private static final String[] FUNC_KEYS = {"sin", "cos", "tan", "log", "ln", "lg"};

    private TextView tvDisplay, tvHistory;
    private HorizontalScrollView hsvDisplay, hsvHistory;
    private View timeView;
    private Button btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9;
    private Button btnDiv, btnMul, btnSub, btnAdd, btnDot;

    // 历史页面
    private View historyPage;
    private ScrollView svHistory;
    private LinearLayout llHistoryItems;
    private Button btnHistoryRecordToggle;
    /** 浮在最上层的固定 his 按钮（不属于历史浮窗，也不属于计算器本体）。 */
    private Button btnHistoryOverlay;

    /** 气泡承载层（含全屏遮罩），平时 GONE，只在弹气泡时出现。 */
    private FrameLayout bubbleOverlay;
    private FunctionBubbleView functionBubble;

    private final StringBuilder currentText = new StringBuilder("0");

    /** 当前选中的函数（插进算式用的完整文本，例如 "sin("、"asinh("），按键和气泡共用。 */
    private final Map<String, String> selectedFunction = new HashMap<>();

    /** 乘方键（÷ 的位置）现在插的是哪个符号：默认 "^"，长按可以换成 ","。 */
    private String caretChar = "^";

    /** 历史记录：算式 + 结果。 */
    private final List<String[]> historyEntries = new ArrayList<>();
    private SharedPreferences prefs;

    /** 上一步是不是刚刚按了 "="。 */
    private boolean lastActionWasEqual = false;

    private int keyMode = MODE_BASIC;
    private int angleMode = MathEngine.DEG;

    /** 各键在布局里的原始字号（像素），切回普通模式时用来还原。 */
    private final HashMap<Integer, Float> defaultTextSizes = new HashMap<>();
    private float defaultZeroFontSize = 0f;

    /** 临时提示的自动清空任务。 */
    private final Handler hintHandler = new Handler(Looper.getMainLooper());
    private final Runnable clearHint = () -> tvHistory.setText("");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        tvDisplay = findViewById(R.id.tvDisplay);
        tvHistory = findViewById(R.id.tvHistory);
        hsvDisplay = findViewById(R.id.hsvDisplay);
        hsvHistory = findViewById(R.id.hsvHistory);
        timeView = findViewById(R.id.curvedTime);

        btn0 = findViewById(R.id.btn0);
        btn1 = findViewById(R.id.btn1);
        btn2 = findViewById(R.id.btn2);
        btn3 = findViewById(R.id.btn3);
        btn4 = findViewById(R.id.btn4);
        btn5 = findViewById(R.id.btn5);
        btn6 = findViewById(R.id.btn6);
        btn7 = findViewById(R.id.btn7);
        btn8 = findViewById(R.id.btn8);
        btn9 = findViewById(R.id.btn9);
        btnDiv = findViewById(R.id.btnDiv);
        btnMul = findViewById(R.id.btnMul);
        btnSub = findViewById(R.id.btnSub);
        btnAdd = findViewById(R.id.btnAdd);
        btnDot = findViewById(R.id.btnDot);

        // 默认选中的函数
        selectedFunction.put("sin", "sin(");
        selectedFunction.put("cos", "cos(");
        selectedFunction.put("tan", "tan(");
        selectedFunction.put("log", "log(");

        View.OnClickListener listener = v -> onKeyPressed(((Button) v).getText().toString());
        int[] ids = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnAdd, R.id.btnSub, R.id.btnMul,
                R.id.btnDiv, R.id.btnDot, R.id.btnEq, R.id.btnAc, R.id.btnDel};
        for (int id : ids) {
            Button b = findViewById(id);
            if (b != null) b.setOnClickListener(listener);
        }

        // 先把布局里的原始字号记下来，切回普通模式时要靠它还原
        captureDefaultFontSizes();

        attachOverlays();

        // 点时间区域 → 换一套按键
        if (timeView != null) {
            timeView.setOnClickListener(v -> {
                keyMode = keyMode == MODE_BASIC ? MODE_SCIENTIFIC : MODE_BASIC;
                applyKeyMode(true);
            });
        }

        applyKeyMode(false);
        updateDisplay();

        // 时钟
        Handler handler = new Handler(Looper.getMainLooper());
        final SimpleDateFormat sdf = new SimpleDateFormat("h:mm", Locale.getDefault());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (timeView != null) {
                    String timeStr = sdf.format(new Date());
                    if (timeView instanceof CurvedTextView) ((CurvedTextView) timeView).setText(timeStr);
                    else if (timeView instanceof TextView) ((TextView) timeView).setText(timeStr);
                }
                handler.postDelayed(this, 10000);
            }
        });

        // 表冠 / 滚轮滚动顶部算式条：不判 source，累积够一整像素才滚，一格 1dp
        CrownScroll.horizontal(hsvDisplay).attach();

        hsvDisplay.setFocusable(true);
        hsvDisplay.requestFocus();

        // 后台预热计算引擎，消掉「第一次按 = 卡顿」
        warmUpMathEngine();
    }

    /**
     * 在后台线程预热计算引擎。
     *
     * <p>「第一次按 = 卡顿」的根因是冷启动：第一次用到 MathEngine 时，
     * ART 要加载并验证里面那一大堆 BigInteger/BigDecimal 代码，还要 JIT 编译热路径，
     * 手表 CPU 慢，几百毫秒就出来了。这里在启动后立刻用低优先级线程跑几条代表性算式，
     * 把类加载、JIT 和常量（π、e）都提前做掉，用户真正按 = 时就是热的。
     *
     * <p>后台线程只调用纯计算的静态方法，不碰任何 UI，所以是安全的；
     * 线程是守护线程且最低优先级，不会和界面抢资源。
     */
    private void warmUpMathEngine() {
        Thread thread = new Thread(() -> {
            // 覆盖面尽量广，把各条热路径都过一遍
            String[] samples = {"2+3", "1.5*2.5", "1/3", "2^0.5", "sin(30)", "cos(60)", "tan(45)",
                    "asin(0.5)", "ln(2)", "lg(1000)", "log(2,8)", "sqrt(2)", "5!", "7%2", "1000!"};
            for (String sample : samples) {
                try {
                    MathEngine.calculate(sample, angleMode);
                } catch (Throwable ignored) {
                    // 预热失败无所谓，绝不能影响正常使用
                }
            }
        }, "MathEngineWarmUp");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    // 三层覆盖：计算器本体 → 历史浮窗 → 固定的 his 按钮
    // ------------------------------------------------------------------

    /**
     * 叠三层覆盖：气泡、历史浮窗、以及**最上层的 his 按钮**。
     *
     * <p>层级由下到上：计算器本体（activity_main 里的 calculatorContent）
     * → 历史浮窗 → his 按钮。
     * his 按钮是独立的一层、永远浮在最上面，位置和大小完全固定 ——
     * 历史页出现或消失都不会影响它，所以再也不用为历史页单独适配按钮的尺寸和位置。
     * 这比原来「在历史页里再摆一份同样的按钮」稳得多。
     */
    private void attachOverlays() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) content;

        // 1) 气泡承载层 + 全屏遮罩（平时 GONE，否则会把所有触摸都吃掉）
        bubbleOverlay = new FrameLayout(this);
        bubbleOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bubbleOverlay.setVisibility(View.GONE);

        View backdrop = new View(this);
        backdrop.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        backdrop.setBackgroundColor(0x00000000);
        backdrop.setClickable(true);
        backdrop.setOnClickListener(v -> {
            if (functionBubble != null) functionBubble.hide();
        });

        functionBubble = new FunctionBubbleView(this, 36, 30, 13f);
        functionBubble.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        functionBubble.setOnChoicePickedListener((display, insert) -> {
            // 乘方键的候选项是单个符号（^ / ,），不进函数表
            if ("^".equals(insert) || ",".equals(insert)) {
                caretChar = insert;
                applyKeyMode(false);
                sciHint(",".equals(insert) ? "符号：逗号 ," : "符号：乘方 ^");
                return;
            }
            // 记下当前函数（按键显示短名，插进算式的是带括号的完整形式）
            String key = baseFunctionOf(insert);
            selectedFunction.put(key, insert);
            applyKeyMode(false);
            sciHint("函数：" + insert);
        });
        functionBubble.setOnBubbleVisibilityListener(shown ->
                bubbleOverlay.setVisibility(shown ? View.VISIBLE : View.GONE));

        bubbleOverlay.addView(backdrop);
        bubbleOverlay.addView(functionBubble);
        root.addView(bubbleOverlay);

        // 2) 历史浮窗（列表 + 记录开关），盖在计算器上面
        historyPage = LayoutInflater.from(this).inflate(R.layout.view_history, root, false);
        historyPage.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        historyPage.setVisibility(View.GONE);
        root.addView(historyPage);

        svHistory = historyPage.findViewById(R.id.svHistory);
        llHistoryItems = historyPage.findViewById(R.id.llHistoryItems);

        // 圆屏安全区：屏幕四角会被表盘裁掉，历史条目（右对齐的结果）容易被切掉，
        // 所以给列表加一圈内缩。
        applyRoundSafeArea();

        // 记录开关：用和按键同款的 Button 切换（Switch 的固有宽度会把权重分配挤偏）。
        // 默认开启（照常记录）；关掉之后新的运算不再写进历史。
        btnHistoryRecordToggle = historyPage.findViewById(R.id.btnHistoryRecordToggle);
        refreshRecordToggle();
        btnHistoryRecordToggle.setOnClickListener(v -> {
            boolean nowRecording = !prefs.getBoolean(KEY_RECORD_HISTORY, true);
            prefs.edit().putBoolean(KEY_RECORD_HISTORY, nowRecording).apply();
            refreshRecordToggle();
        });

        // 历史列表：表冠 / 滚轮也能滚（挂真实 View，不判 source）
        CrownScroll.vertical(svHistory).attach();

        // 3) 最上层：固定的 his 按钮
        attachHistoryButton(root);
    }

    /**
     * 创建浮在最上层的 his 按钮。
     *
     * <p>它的尺寸和位置**直接复制计算器上「.」键的几何**（同一个格子、同样大小），
     * 所以看着就是计算器上那个键；又因为它是独立的一层，
     * 历史页开与关都不需要动它 —— 这正是这次重构的目的。
     */
    private void attachHistoryButton(ViewGroup root) {
        btnHistoryOverlay = new Button(this);
        btnHistoryOverlay.setBackgroundResource(R.drawable.bg_btn_dark);
        btnHistoryOverlay.setText("his");
        btnHistoryOverlay.setTextColor(0xFFFFFFFF);
        btnHistoryOverlay.setAllCaps(false);
        btnHistoryOverlay.setIncludeFontPadding(false);
        btnHistoryOverlay.setPadding(0, 0, 0, 0);
        btnHistoryOverlay.setMinWidth(0);
        btnHistoryOverlay.setMinHeight(0);
        btnHistoryOverlay.setVisibility(View.GONE);
        if (btnDot != null && btnDot.getTextSize() > 0f) {
            btnHistoryOverlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, btnDot.getTextSize());
        }
        btnHistoryOverlay.setOnClickListener(v -> {
            if (isHistoryOpen()) closeHistory();
            else openHistory();
        });
        // 长按 → 清空所有记录
        btnHistoryOverlay.setOnLongClickListener(v -> {
            clearHistory();
            return true;
        });

        btnHistoryOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(btnHistoryOverlay);

        // 等计算器本体布局完成后，照抄「.」键的矩形与位置
        btnDot.post(() -> {
            if (btnDot.getWidth() <= 0) return;
            ViewGroup.LayoutParams params = btnHistoryOverlay.getLayoutParams();
            params.width = btnDot.getWidth();
            params.height = btnDot.getHeight();
            btnHistoryOverlay.setLayoutParams(params);
            btnDot.post(() -> {
                View calculatorContent = findViewById(R.id.calculatorContent);
                if (calculatorContent == null) return;
                int[] dotLoc = new int[2];
                int[] contentLoc = new int[2];
                btnDot.getLocationInWindow(dotLoc);
                calculatorContent.getLocationInWindow(contentLoc);
                btnHistoryOverlay.setX(dotLoc[0] - contentLoc[0]);
                btnHistoryOverlay.setY(dotLoc[1] - contentLoc[1]);
            });
        });
    }

    /**
     * 刷新「记录历史」开关的外观。
     * 开关就是和按键同款的胶囊，打开时高亮成强调色，文字也跟着变，一眼能看出状态。
     */
    private void refreshRecordToggle() {
        if (btnHistoryRecordToggle == null) return;
        boolean recording = prefs.getBoolean(KEY_RECORD_HISTORY, true);
        btnHistoryRecordToggle.setSelected(recording);
        btnHistoryRecordToggle.setText(recording ? "记录历史" : "不记录历史");
    }

    /**
     * 给历史页面的列表加圆屏安全区内缩。
     *
     * <p>圆屏真正可见的只有屏幕中间那块内接区域，四角是被表盘裁掉的，
     * 而历史条目是右对齐的，结果数字很容易正好落在右上角被切掉。
     * 内缩量按屏宽的比例算（和气泡用同一个比例），方屏上为 0、不影响。
     * 加在列表内部的容器上（而不是 ScrollView 自己），这样滚动时内缩跟着内容走，
     * 第一条和最后一条都不会被裁到。
     *
     * <p>底部那一行不用额外处理 —— 它是照抄计算器最后一行做的，
     * 位置本来就落在计算器自己的安全范围内。
     */
    private void applyRoundSafeArea() {
        if (!getResources().getConfiguration().isScreenRound()) return;
        int inset = Math.round(getResources().getDisplayMetrics().widthPixels * ROUND_SAFE_RATIO);
        llHistoryItems.setPadding(inset, inset / 2, inset, inset / 2);
    }

    private void openHistory() {
        renderHistory();
        historyPage.setVisibility(View.VISIBLE);
        historyPage.setAlpha(0f);
        historyPage.animate().alpha(1f).setDuration(180).start();
        // 把焦点交给历史列表：这样表冠/滚轮的通用滚动也会落在它身上，
        // 而不是还留在顶部那条算式上（两者都挂了监听，抢焦点就会滚错对象）
        svHistory.setFocusableInTouchMode(true);
        svHistory.requestFocus();
        svHistory.post(() -> svHistory.fullScroll(View.FOCUS_DOWN));
        // his 按钮是独立的一层，只需要让它露出来即可 —— 位置和大小完全不用动
        if (btnHistoryOverlay != null) btnHistoryOverlay.setVisibility(View.VISIBLE);
    }

    private void closeHistory() {
        historyPage.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> historyPage.setVisibility(View.GONE))
                .start();
        // 焦点还给算式条
        hsvDisplay.setFocusable(true);
        hsvDisplay.requestFocus();
        if (btnHistoryOverlay != null) btnHistoryOverlay.setVisibility(View.GONE);
    }

    private boolean isHistoryOpen() {
        return historyPage != null && historyPage.getVisibility() == View.VISIBLE;
    }

    /** 把历史记录渲染成列表（最新的在最下面）。 */
    private void renderHistory() {
        llHistoryItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (historyEntries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有记录");
            empty.setTextColor(0x66FFFFFF);
            empty.setTextSize(12f);
            empty.setGravity(android.view.Gravity.CENTER);
            // 列表是 fillViewport 的，直接 addView 会贴在最上面；这里给它一个
            // 占满剩余高度的容器，让提示居中显示
            empty.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            llHistoryItems.addView(empty);
            return;
        }

        for (int i = 0; i < historyEntries.size(); i++) {
            final int index = i;
            String[] entry = historyEntries.get(i);
            View row = inflater.inflate(R.layout.item_history_entry, llHistoryItems, false);
            ((TextView) row.findViewById(R.id.tvHistoryExpression)).setText(entry[0] + "=");
            ((TextView) row.findViewById(R.id.tvHistoryResult)).setText(entry[1]);
            // 长按单条记录 → 删掉这一条
            row.setOnLongClickListener(v -> {
                deleteHistoryEntry(index);
                return true;
            });
            llHistoryItems.addView(row);
        }
    }

    /** 删掉某一条历史记录。 */
    private void deleteHistoryEntry(int index) {
        if (index < 0 || index >= historyEntries.size()) return;
        historyEntries.remove(index);
        renderHistory();
        sciHint("已删除该条记录");
        vibrate();
    }

    /** 清空全部历史记录。 */
    private void clearHistory() {
        if (historyEntries.isEmpty()) {
            sciHint("还没有记录可清");
            return;
        }
        int removed = historyEntries.size();
        historyEntries.clear();
        renderHistory();
        sciHint("已清空 " + removed + " 条记录");
        vibrate();
    }

    /** 记录一条历史（开关关掉时不记录）。 */
    private void addHistory(String expression, String result) {
        if (!prefs.getBoolean(KEY_RECORD_HISTORY, true)) return;
        historyEntries.add(new String[]{expression, result});
        while (historyEntries.size() > MAX_HISTORY) historyEntries.remove(0);
        if (isHistoryOpen()) renderHistory();
    }

    // ------------------------------------------------------------------
    // 两套按键
    // ------------------------------------------------------------------

    private void applyKeyMode(boolean animate) {
        if (keyMode == MODE_SCIENTIFIC) {
            updateButtonLabel(btn7, "(", SCI_FONT_FUNCTION);
            updateButtonLabel(btn8, ")", SCI_FONT_FUNCTION);
            updateButtonLabel(btn9, "!", SCI_FONT_FUNCTION);
            updateButtonLabel(btnDiv, caretChar, SCI_FONT_FUNCTION);
            updateButtonLabel(btnMul, "π", SCI_FONT_FUNCTION);
            updateButtonLabel(btnSub, "e", SCI_FONT_FUNCTION);
            updateButtonLabel(btn2, "%", SCI_FONT_FUNCTION);
            updateButtonLabel(btn3, "Ans", SCI_FONT_FUNCTION);
            updateButtonLabel(btnAdd, "±", SCI_FONT_SHORT);
            updateButtonLabel(btnDot, "his", SCI_FONT_SHORT);
            updateButtonLabel(btn4, shortNameOf("sin"), SCI_FONT_FUNCTION);
            updateButtonLabel(btn5, shortNameOf("cos"), SCI_FONT_FUNCTION);
            updateButtonLabel(btn6, shortNameOf("tan"), SCI_FONT_FUNCTION);
            updateButtonLabel(btn1, shortNameOf("log"), SCI_FONT_FUNCTION);
        } else {
            // 普通模式：先还原布局原始字号，再换回数字
            restoreDefaultFontSizes();
            updateButtonLabel(btn7, "7", 0f);
            updateButtonLabel(btn8, "8", 0f);
            updateButtonLabel(btn9, "9", 0f);
            updateButtonLabel(btnDiv, "÷", 0f);
            updateButtonLabel(btnMul, "×", 0f);
            updateButtonLabel(btnSub, "-", 0f);
            updateButtonLabel(btn2, "2", 0f);
            updateButtonLabel(btn3, "3", 0f);
            updateButtonLabel(btn4, "4", 0f);
            updateButtonLabel(btn5, "5", 0f);
            updateButtonLabel(btn6, "6", 0f);
            updateButtonLabel(btn1, "1", 0f);
            updateButtonLabel(btnAdd, "+", 0f);
            updateButtonLabel(btnDot, ".", 0f);
            if (functionBubble != null) functionBubble.hide();
        }
        setupFunctionLongPress();
        updateDisplay();
        if (animate && timeView != null) {
            timeView.setAlpha(0.35f);
            timeView.animate().alpha(1f).setDuration(220).start();
        }
    }

    /** 当前函数插进算式时的完整文本（例如 "asinh("）。 */
    private String currentFunction(String key) {
        String value = selectedFunction.get(key);
        return value != null ? value : key + "(";
    }

    /** 按钮上显示的短名：去掉插进算式时带的左括号。 */
    private String shortNameOf(String key) {
        String full = currentFunction(key);
        return full.endsWith("(") ? full.substring(0, full.length() - 1) : full;
    }

    /** 从 "asinh(" 反推出所属的键 sin/cos/tan/log。 */
    private String baseFunctionOf(String insert) {
        String name = insert.endsWith("(") ? insert.substring(0, insert.length() - 1) : insert;
        for (String key : new String[]{"sin", "cos", "tan"}) {
            if (name.contains(key)) return key;
        }
        return "log";
    }

    private void updateButtonLabel(Button button, String label) {
        updateButtonLabel(button, label, 0f);
    }

    private void updateButtonLabel(Button button, String label, float textSizeSp) {
        if (button == null || label == null) return;
        if (textSizeSp > 0f) button.setTextSize(textSizeSp);
        if (label.contentEquals(button.getText())) return;
        button.setText(label);
        button.setScaleX(0.86f);
        button.setScaleY(0.86f);
        button.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    private void captureDefaultFontSizes() {
        defaultTextSizes.clear();
        int[] ids = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9};
        for (int id : ids) {
            Button b = findViewById(id);
            if (b != null) defaultTextSizes.put(id, b.getTextSize());
        }
        defaultZeroFontSize = btn0 != null ? btn0.getTextSize() : 0f;
    }

    private void restoreDefaultFontSizes() {
        for (Map.Entry<Integer, Float> entry : defaultTextSizes.entrySet()) {
            Button b = findViewById(entry.getKey());
            if (b != null) b.setTextSize(TypedValue.COMPLEX_UNIT_PX, entry.getValue());
        }
    }

    /** 长按 sin / cos / tan / 对数 / 乘方键 → 弹出可选项的气泡。 */
    private void setupFunctionLongPress() {
        if (keyMode != MODE_SCIENTIFIC || functionBubble == null) {
            if (btn1 != null) btn1.setOnLongClickListener(null);
            if (btnDiv != null) btnDiv.setOnLongClickListener(null);
            return;
        }
        bindLongPress(btn4, "sin");
        bindLongPress(btn5, "cos");
        bindLongPress(btn6, "tan");
        bindLongPress(btn1, "log");
        // 乘方键：长按在 "^" 和 "," 之间换（算式里要打逗号只能从这里来）
        bindLongPress(btnDiv, "^");
    }

    private void bindLongPress(final Button button, final String key) {
        if (button == null) return;
        button.setOnLongClickListener(v -> {
            // 每次弹出前按当前选择重建候选项，并把正在用的那个高亮
            functionBubble.setChoices(choicesFor(key), currentChoiceOf(key));
            functionBubble.show(v, keypadTopInOverlay());
            return true;
        });
    }

    /** 这个键当前用的是哪一项（气泡拿它来高亮）。 */
    private String currentChoiceOf(String key) {
        if ("^".equals(key)) return caretChar;
        return currentFunction(key);
    }

    /** 每个键可选哪些函数。 */
    private List<FunctionBubbleView.Choice> choicesFor(String key) {
        if ("^".equals(key)) {
            return Arrays.asList(
                    new FunctionBubbleView.Choice("^", "^", "乘方：2^3 = 8"),
                    new FunctionBubbleView.Choice(",", ",", "逗号：log(2,8) = 3"));
        }
        if ("log".equals(key)) {
            return Arrays.asList(
                    new FunctionBubbleView.Choice("ln", "ln(", "以 e 为底的自然对数"),
                    new FunctionBubbleView.Choice("lg", "lg(", "以 10 为底的常用对数"),
                    new FunctionBubbleView.Choice("log", "log(", "log(底数, 真数)"));
        }
        return Arrays.asList(
                new FunctionBubbleView.Choice(key, key + "(", key + "：三角函数"),
                new FunctionBubbleView.Choice("a" + key, "a" + key + "(", "a" + key + "：反三角函数"),
                new FunctionBubbleView.Choice(key + "h", key + "h(", key + "h：双曲函数"),
                new FunctionBubbleView.Choice("a" + key + "h", "a" + key + "h(", "a" + key + "h：反双曲函数"));
    }

    /**
     * 按键区顶边相对气泡承载层的 y 坐标。
     * 气泡靠它保证自己待在按键上方那片空白里，不会压住按键。
     */
    private Integer keypadTopInOverlay() {
        View keypad = findViewById(R.id.keypad);
        if (keypad == null || bubbleOverlay == null) return null;
        int[] keypadLoc = new int[2];
        int[] overlayLoc = new int[2];
        keypad.getLocationInWindow(keypadLoc);
        bubbleOverlay.getLocationInWindow(overlayLoc);
        return keypadLoc[1] - overlayLoc[1];
    }

    // ------------------------------------------------------------------
    // 按键分发
    // ------------------------------------------------------------------

    private void onKeyPressed(String btnText) {
        // 历史页面开着时，计算器的按键不该响应
        if (isHistoryOpen()) return;

        // 科学模式下的 0 键是角度单位键：显示 DEG / RAD，点一下切换
        if (isAngleKey(btnText)) {
            toggleAngleMode();
            return;
        }
        // ± ：翻转算式里最右边那个数的正负号
        if ("±".equals(btnText)) {
            toggleSignOfLastOperand();
            updateDisplay();
            return;
        }
        if ("his".equals(btnText)) {
            openHistory();
            return;
        }

        // 错误状态恢复
        if (currentText.toString().equals("Error") && !btnText.equals("C")) {
            currentText.setLength(0);
            currentText.append("0");
        }

        // 「刚算完」之后的后续输入
        if (lastActionWasEqual && !btnText.equals("C") && !btnText.equals("=")) {
            if (btnText.matches("[+\\-×÷^!%]")) {
                if (currentText.toString().endsWith("...")) {
                    currentText.setLength(currentText.length() - 3);
                }
            } else if (btnText.matches("[0-9]")) {
                currentText.setLength(0);
            } else if (btnText.equals(".")) {
                currentText.setLength(0);
                currentText.append("0");
            }
            lastActionWasEqual = false;
        }

        switch (btnText) {
            case "C":
                currentText.setLength(0);
                currentText.append("0");
                tvHistory.setText("");
                lastActionWasEqual = false;
                break;
            case "⌫":
                if (lastActionWasEqual) {
                    currentText.setLength(0);
                    currentText.append("0");
                    lastActionWasEqual = false;
                } else {
                    handleBackspace();
                }
                break;
            case "=":
                if (!lastActionWasEqual) {
                    handleCalculation();
                    lastActionWasEqual = true;
                }
                break;
            case ".":
                handleDot();
                break;
            default:
                handleInput(btnText);
                break;
        }
        updateDisplay();
    }

    /** 这个按键文字是不是 0 键上的角度单位（只在科学模式下会出现）。 */
    private boolean isAngleKey(String btnText) {
        return keyMode == MODE_SCIENTIFIC
                && (btnText.equals(CHIP_DEG) || btnText.equals(CHIP_RAD));
    }

    private void toggleAngleMode() {
        angleMode = angleMode == MathEngine.DEG ? MathEngine.RAD : MathEngine.DEG;
        updateZeroKey();
        sciHint(angleMode == MathEngine.DEG ? "角度单位：角度 DEG" : "角度单位：弧度 RAD");
    }

    /**
     * 把当前式子抄到屏幕上，并维护 0 键的显示。
     *
     * <p>0 键在科学模式下直接显示当前角度单位（DEG / RAD）。角度单位是独立的状态，
     * 不写进算式里，所以显示区永远是干净的算式。
     */
    private void updateDisplay() {
        tvDisplay.setText(currentText.toString());
        updateZeroKey();
        hsvDisplay.post(() -> hsvDisplay.fullScroll(View.FOCUS_RIGHT));
    }

    private void updateZeroKey() {
        if (btn0 == null) return;
        if (keyMode == MODE_SCIENTIFIC) {
            btn0.setText(angleMode == MathEngine.DEG ? CHIP_DEG : CHIP_RAD);
            btn0.setTextSize(CHIP_FONT);
        } else {
            btn0.setText("0");
            if (defaultZeroFontSize > 0f) {
                btn0.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultZeroFontSize);
            }
        }
    }

    /**
     * 屏幕上那个孤零零的 "0" 只是个占位符：一旦用户按下别的东西，就应该把它丢掉，
     * 而不是拼成 "0sin(" 这种算不通的式子。
     */
    private void clearBlankZero() {
        if (currentText.length() == 1 && currentText.charAt(0) == '0') {
            currentText.setLength(0);
        }
    }

    /** 除数字以外的输入都走这里。 */
    private void handleInput(String btn) {
        if (btn.equals("Ans")) {
            clearBlankZero();
            currentText.append("ans");
            return;
        }

        // 科学函数键：按键上是短名（sin、asinh…），按下时补上左括号插进算式
        String functionKey = functionKeyOf(btn);
        if (functionKey != null) {
            clearBlankZero();
            insertFunction(currentFunction(functionKey));
            return;
        }

        if (isDigitOrConstant(btn)) {
            if (btn.length() == 1 && Character.isDigit(btn.charAt(0))) {
                if (currentText.toString().equals("0")) currentText.setLength(0);
            } else {
                clearBlankZero();
            }
            currentText.append(btn);
            return;
        }

        if (btn.equals(")")) {
            if (openParenCount() > 0) currentText.append(")");
            return;
        }

        clearBlankZero();
        if (isOperator(btn) && currentText.length() > 0) {
            char last = currentText.charAt(currentText.length() - 1);
            if (isOperator(String.valueOf(last)) || last == '.') {
                currentText.setLength(currentText.length() - 1);
            }
        }
        currentText.append(btn);
    }

    /**
     * 按钮文字对应哪个函数键。
     *
     * <p>按钮上显示的短名可能是 asinh、lg 这种，和插进算式的文本（asinh(、lg(）
     * 只差一个左括号，所以直接比对「短名 + (」。
     */
    private String functionKeyOf(String btnText) {
        for (String key : FUNC_KEYS) {
            if (shortNameOf(key).equals(btnText)) return key;
        }
        return null;
    }

    /** 插入函数调用：先把末尾可能残留的函数名换掉，再补上左括号。 */
    private void insertFunction(String function) {
        replaceTrailingFunction();
        currentText.append(function);
    }

    private void replaceTrailingFunction() {
        int length = trailingFunctionLength();
        if (length > 0) currentText.setLength(currentText.length() - length);
    }

    /** 末尾是不是刚好一个完整的函数开头（或 Ans），是的话返回它的字符数。 */
    private int trailingFunctionLength() {
        String text = currentText.toString();
        String[] names = {"sin(", "cos(", "tan(", "asin(", "acos(", "atan(",
                "sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(",
                "log(", "ln(", "lg(", "ans"};
        // 先匹配长的，避免 asinh( 被 sinh( 抢先匹配掉
        int best = 0;
        for (String name : names) {
            if (text.endsWith(name) && name.length() > best) best = name.length();
        }
        return best;
    }

    private void handleBackspace() {
        int functionLength = trailingFunctionLength();
        if (functionLength > 0) {
            currentText.setLength(currentText.length() - functionLength);
            if (currentText.length() == 0) currentText.append("0");
            return;
        }
        if (currentText.length() > 1) currentText.setLength(currentText.length() - 1);
        else {
            currentText.setLength(0);
            currentText.append("0");
        }
    }

    /** 当前式子里还有几个左括号没闭合。 */
    private int openParenCount() {
        String text = currentText.toString();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        return depth;
    }

    /**
     * ± ：把算式里最右边那个数的正负号翻过来。
     *
     * <p>做法是在它前面插入/删除负号，这样比「算出来再取反」更贴近一般的计算器。
     * 分开处理三种情况：负数（移除负号）、紧跟运算符的正数（插入负号）、
     * 其它情况（插一个 -(…)，因为中间插负号说不通）。
     */
    private void toggleSignOfLastOperand() {
        String text = currentText.toString();
        if (text.isEmpty()) {
            currentText.append("(0-");
            return;
        }
        char last = text.charAt(text.length() - 1);
        if (isOperator(String.valueOf(last)) || last == '(' || last == '.') return;

        int numberStart = lastOperandStart(text);
        if (numberStart > 0 && text.charAt(numberStart - 1) == '-') {
            boolean unaryMinus = numberStart - 1 == 0
                    || text.charAt(numberStart - 2) == '('
                    || isOperator(String.valueOf(text.charAt(numberStart - 2)));
            if (unaryMinus) {
                // 已经是负数：把负号去掉
                currentText.deleteCharAt(numberStart - 1);
                if (currentText.length() == 0) currentText.append("0");
                return;
            }
        }
        if (numberStart == 0 || text.charAt(numberStart - 1) == '('
                || isOperator(String.valueOf(text.charAt(numberStart - 1)))) {
            // 前面正是运算符或左括号：直接插一个负号
            currentText.insert(numberStart, '-');
            return;
        }
        // 前面是数字/右括号，中间插负号没有意义，改成 -(… )
        currentText.insert(0, "0-(").append(")");
    }

    /** 最右边那个数字（含小数点）在算式里的起始下标。 */
    private int lastOperandStart(String text) {
        int i = text.length();
        while (i > 0) {
            char c = text.charAt(i - 1);
            if ((c >= '0' && c <= '9') || c == '.') i--;
            else break;
        }
        return i;
    }

    private static boolean isDigitOrConstant(String s) {
        return s.length() == 1 && (Character.isDigit(s.charAt(0)) || s.equals("π") || s.equals("e"));
    }

    private static boolean isOperator(String s) {
        return s.matches("[+\\-×÷^!%]");
    }

    // ------------------------------------------------------------------
    // 计算
    // ------------------------------------------------------------------

    private void handleCalculation() {
        String expression = currentText.toString();
        try {
            String result = MathEngine.calculate(expression, angleMode);
            tvHistory.setText(expression + "=");
            hsvHistory.post(() -> hsvHistory.fullScroll(View.FOCUS_RIGHT));
            addHistory(expression, result);
            currentText.setLength(0);
            currentText.append(result);
        } catch (MathEngine.MathException e) {
            tvHistory.setText(expression + "=");
            hsvHistory.post(() -> hsvHistory.fullScroll(View.FOCUS_RIGHT));
            currentText.setLength(0);
            currentText.append("Error");
            sciHint(e.getMessage());
        } catch (Exception e) {
            currentText.setLength(0);
            currentText.append("Error");
        }
    }

    /**
     * 小数点。
     *
     * <p>刚按完 sin( 再按小数点，原来会因为「末尾是运算符」直接不响应；
     * 现在补成 "sin(0." 更符合直觉。
     */
    private void handleDot() {
        if (currentText.length() == 0) {
            currentText.append("0.");
            return;
        }
        String text = currentText.toString();
        char last = text.charAt(text.length() - 1);
        if (isOperator(String.valueOf(last)) || last == '(') {
            currentText.append("0.");
            return;
        }
        if (last == '.') return;

        int lastOp = -1;
        for (String op : new String[]{"+", "-", "×", "÷", "^", "!", "%", "("}) {
            lastOp = Math.max(lastOp, text.lastIndexOf(op));
        }
        if (!text.substring(lastOp + 1).contains(".")) currentText.append(".");
    }

    /** 在历史行里显示一条临时说明，两秒后自动清空。 */
    private void sciHint(String message) {
        if (message == null || message.isEmpty()) return;
        hintHandler.removeCallbacks(clearHint);
        tvHistory.setText(message);
        hsvHistory.post(() -> hsvHistory.fullScroll(View.FOCUS_RIGHT));
        hintHandler.postDelayed(clearHint, HINT_CLEAR_DELAY_MS);
    }

    /** 删除/清空这种不可撤销的操作，给一下触觉反馈。 */
    private void vibrate() {
        View target = historyPage != null ? historyPage : tvHistory;
        if (target == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            target.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } else {
            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }
}
