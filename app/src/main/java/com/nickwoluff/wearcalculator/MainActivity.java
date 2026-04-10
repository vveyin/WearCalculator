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

    // 🌟 核心：旋转累加器，用来消除“漏电感”
    private float rotaryAccumulator = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 初始化
        tvDisplay = findViewById(R.id.tvDisplay);
        tvHistory = findViewById(R.id.tvHistory);
        hsvDisplay = findViewById(R.id.hsvDisplay);
        hsvHistory = findViewById(R.id.hsvHistory);
        CurvedTextView curvedTime = findViewById(R.id.curvedTime);

        // 2. 按钮逻辑
        View.OnClickListener listener = v -> {
            String btnText = ((Button) v).getText().toString();
            if (currentText.toString().equals("Error") && !btnText.equals("C")) {
                currentText.setLength(0); currentText.append("0");
            }

            switch (btnText) {
                case "C": currentText.setLength(0); currentText.append("0"); tvHistory.setText(""); break;
                case "⌫":
                    if (currentText.length() > 1) currentText.setLength(currentText.length() - 1);
                    else { currentText.setLength(0); currentText.append("0"); }
                    break;
                case "=": handleCalculation(); break;
                case ".": handleDot(); break;
                default: handleNumberAndOperator(btnText); break;
            }
            tvDisplay.setText(currentText.toString());
            hsvDisplay.post(() -> hsvDisplay.fullScroll(View.FOCUS_RIGHT));
        };

        int[] ids = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnAdd, R.id.btnSub, R.id.btnMul,
                R.id.btnDiv, R.id.btnDot, R.id.btnEq, R.id.btnAc, R.id.btnDel};
        for (int id : ids) {
            Button b = findViewById(id);
            if (b != null) b.setOnClickListener(listener);
        }

        // 3. 时钟逻辑（12 小时制）
        Handler handler = new Handler(Looper.getMainLooper());
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm", Locale.getDefault());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (curvedTime != null) curvedTime.setText(sdf.format(new Date()));
                handler.postDelayed(this, 10000);
            }
        });

        // 4. 表冠旋转（超轻触感版）
        hsvDisplay.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {

                int axis = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                        MotionEvent.AXIS_SCROLL : MotionEvent.AXIS_VSCROLL;

                float delta = -event.getAxisValue(axis);

                if (delta != 0) {
                    v.scrollBy(Math.round(delta * 80), 0);

                    rotaryAccumulator += delta;

                    // 🌟 调优 1：适当调大阈值到 1.2f 或 1.5f，让震动更有“颗粒感”，而不是糊成一团
                    if (Math.abs(rotaryAccumulator) >= 0.8f) {

                        // 🌟 调优 2：选择最轻盈的触感常量
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
                            // 这是专为旋转设计的轻微滴答感
                            v.performHapticFeedback(18); // 18 是 ROTARY_SCROLL_TICK
                        } else {
                            // 经典的短促滴答，比 KEYBOARD_TAP 轻很多
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                        }

                        rotaryAccumulator = 0f;
                    }
                }
                return true;
            }
            return false;
        });

        // 必须请求焦点，表冠才能第一时间响应
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