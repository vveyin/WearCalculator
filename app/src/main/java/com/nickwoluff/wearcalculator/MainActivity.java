package com.nickwoluff.wearcalculator;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.wear.widget.CurvedTextView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "WearCalc";
    private TextView tvDisplay, tvHistory;
    private HorizontalScrollView hsvDisplay, hsvHistory;
    private final StringBuilder currentText = new StringBuilder("0");

    // 🌟 核心调优：震动累加器
    private float rotaryAccumulator = 0f;

    // 🌟 修复 Bug 核心：标记上一步是不是刚刚按了 "="
    private boolean lastActionWasEqual = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDisplay = findViewById(R.id.tvDisplay);
        tvHistory = findViewById(R.id.tvHistory);
        hsvDisplay = findViewById(R.id.hsvDisplay);
        hsvHistory = findViewById(R.id.hsvHistory);
        final View timeView = findViewById(R.id.curvedTime);

        // 按钮逻辑
        View.OnClickListener listener = v -> {
            String btnText = ((Button) v).getText().toString();

            // 错误状态恢复
            if (currentText.toString().equals("Error") && !btnText.equals("C")) {
                currentText.setLength(0); currentText.append("0");
            }

            // 🌟 核心状态机修复：处理计算完成后的后续输入
            if (lastActionWasEqual && !btnText.equals("C") && !btnText.equals("=")) {
                if (btnText.matches("[+\\-×÷]")) {
                    // 如果刚算完就按运算符：保留结果，但必须斩断 "..." 尾巴
                    if (currentText.toString().endsWith("...")) {
                        currentText.setLength(currentText.length() - 3);
                    }
                } else if (btnText.matches("[0-9]")) {
                    // 如果刚算完就按数字：清空屏幕，开始全新计算
                    currentText.setLength(0);
                } else if (btnText.equals(".")) {
                    // 如果刚算完按小数点：以 "0." 开始全新计算
                    currentText.setLength(0);
                    currentText.append("0");
                }
                lastActionWasEqual = false; // 状态解除
            }

            switch (btnText) {
                case "C":
                    currentText.setLength(0); currentText.append("0"); tvHistory.setText("");
                    lastActionWasEqual = false;
                    break;
                case "⌫":
                    if (lastActionWasEqual) {
                        // 刚算完按退格，直接清零重来最符合直觉
                        currentText.setLength(0); currentText.append("0");
                        lastActionWasEqual = false;
                    } else {
                        if (currentText.length() > 1) currentText.setLength(currentText.length() - 1);
                        else { currentText.setLength(0); currentText.append("0"); }
                    }
                    break;
                case "=":
                    if (!lastActionWasEqual) { // 防止疯狂连按等号
                        handleCalculation();
                        lastActionWasEqual = true; // 标记计算完成
                    }
                    break;
                case ".":
                    handleDot();
                    break;
                case ">_":
                    // 🌟 终端按键占位保护：当前版本点它什么都不会发生，绝对安全
                    break;
                default:
                    handleNumberAndOperator(btnText);
                    break;
            }
            tvDisplay.setText(currentText.toString());
            hsvDisplay.post(() -> hsvDisplay.fullScroll(View.FOCUS_RIGHT));
        };

        // 注册所有按钮
        int[] ids = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnAdd, R.id.btnSub, R.id.btnMul,
                R.id.btnDiv, R.id.btnDot, R.id.btnEq, R.id.btnAc, R.id.btnDel, R.id.btnMore}; // 加入了 btnMore
        for (int id : ids) {
            Button b = findViewById(id);
            if (b != null) b.setOnClickListener(listener);
        }

        // 时钟逻辑
        Handler handler = new Handler(Looper.getMainLooper());
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm", Locale.getDefault());
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

        // 表冠旋转逻辑
        hsvDisplay.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {

                int axis = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                        MotionEvent.AXIS_SCROLL : MotionEvent.AXIS_VSCROLL;

                float delta = -event.getAxisValue(axis);

                if (delta != 0) {
                    v.scrollBy(Math.round(delta * 80), 0);
                    rotaryAccumulator += delta;

                    if (Math.abs(rotaryAccumulator) >= 0.6f) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            v.performHapticFeedback(18);
                        } else {
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        rotaryAccumulator = 0f;
                    }
                }
                return true;
            }
            return false;
        });

        hsvDisplay.setFocusable(true);
        hsvDisplay.requestFocus();
    }

    private void handleCalculation() {
        try {
            String expr = currentText.toString().replace("×", "*").replace("÷", "/");
            BigDecimal res = eval(expr);
            tvHistory.setText(String.format("%s=", currentText.toString()));
            hsvHistory.post(() -> hsvHistory.fullScroll(View.FOCUS_RIGHT));
            currentText.setLength(0); currentText.append(formatResult(res));
        } catch (Exception e) {
            currentText.setLength(0); currentText.append("Error");
        }
    }

    private void handleDot() {
        String text = currentText.toString();
        if (text.length() > 0 && !isOperator(text.substring(text.length() - 1))) {
            int lastOp = -1;
            for (String op : new String[]{"+", "-", "×", "÷"}) lastOp = Math.max(lastOp, text.lastIndexOf(op));
            if (!text.substring(lastOp + 1).contains(".")) currentText.append(".");
        }
    }

    private void handleNumberAndOperator(String btn) {
        if (currentText.toString().equals("0") && !isOperator(btn)) currentText.setLength(0);
        if (isOperator(btn) && currentText.length() > 0) {
            char last = currentText.charAt(currentText.length() - 1);
            if (isOperator(String.valueOf(last)) || last == '.') currentText.setLength(currentText.length() - 1);
        }
        currentText.append(btn);
    }

    private boolean isOperator(String s) { return s.matches("[+\\-×÷]"); }

    private BigDecimal eval(String str) {
        return new Object() {
            int pos = -1, ch;
            void next() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int c) { while (ch == ' ') next(); if (ch == c) { next(); return true; } return false; }
            BigDecimal parse() { next(); return expr(); }
            BigDecimal expr() {
                BigDecimal x = term();
                for (;;) { if (eat('+')) x = x.add(term()); else if (eat('-')) x = x.subtract(term()); else return x; }
            }
            BigDecimal term() {
                BigDecimal x = factor();
                for (;;) { if (eat('*')) x = x.multiply(factor()); else if (eat('/')) x = x.divide(factor(), 16, RoundingMode.HALF_UP); else return x; }
            }
            BigDecimal factor() {
                if (eat('+')) return factor(); if (eat('-')) return factor().negate();
                int start = pos;
                if ((ch >= '0' && ch <= '9') || ch == '.') { while ((ch >= '0' && ch <= '9') || ch == '.') next(); return new BigDecimal(str.substring(start, pos)); }
                throw new RuntimeException();
            }
        }.parse();
    }

    private String formatResult(BigDecimal bd) {
        bd = bd.stripTrailingZeros();
        return bd.scale() > 8 ? bd.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "..." : bd.toPlainString();
    }
}