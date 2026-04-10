package com.nickwoluff.wearcalculator;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MainActivity extends Activity {

    private TextView tvDisplay;
    private TextView tvHistory;
    private StringBuilder currentText = new StringBuilder("0");
    // 【核心修复】：变量定义必须在类的内部！
    private android.widget.HorizontalScrollView hsvDisplay;
    private android.widget.HorizontalScrollView hsvHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDisplay = findViewById(R.id.tvDisplay);
        tvHistory = findViewById(R.id.tvHistory);
        hsvDisplay = findViewById(R.id.hsvDisplay);
        hsvHistory = findViewById(R.id.hsvHistory);

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button b = (Button) v;
                String buttonText = b.getText().toString();

                if (currentText.toString().equals("Error") && !buttonText.equals("C")) {
                    currentText.setLength(0);
                    currentText.append("0");
                }

                if (buttonText.equals("C")) {
                    currentText.setLength(0);
                    currentText.append("0");
                    tvHistory.setText("");
                } else if (buttonText.equals("⌫")) {
                    if (currentText.length() > 1) {
                        currentText.setLength(currentText.length() - 1);
                    } else {
                        currentText.setLength(0);
                        currentText.append("0");
                    }
                } else if (buttonText.equals("=")) {
                    try {
                        String originalExpression = currentText.toString();
                        String expression = originalExpression.replace("×", "*").replace("÷", "/");
                        BigDecimal result = eval(expression);

                        tvHistory.setText(originalExpression + "=");

                        hsvHistory.post(new Runnable() {
                            @Override
                            public void run() {
                                hsvHistory.fullScroll(View.FOCUS_RIGHT);
                            }
                        });

                        currentText.setLength(0);
                        currentText.append(formatResult(result));

                    } catch (Exception e) {
                        currentText.setLength(0);
                        currentText.append("Error");
                        tvHistory.setText("");
                    }
                } else if (buttonText.equals(".")) {
                    String text = currentText.toString();
                    char lastChar = text.charAt(text.length() - 1);

                    if (!isOperator(String.valueOf(lastChar))) {
                        int lastOp = -1;
                        char[] ops = {'+', '-', '×', '÷'};
                        for (char op : ops) {
                            int index = text.lastIndexOf(op);
                            if (index > lastOp) lastOp = index;
                        }
                        String lastNumberPart = text.substring(lastOp + 1);
                        if (!lastNumberPart.contains(".")) {
                            currentText.append(".");
                        }
                    }
                } else {
                    if (currentText.toString().equals("0")) {
                        if (!isOperator(buttonText)) {
                            currentText.setLength(0);
                        }
                    }

                    if (isOperator(buttonText) && currentText.length() > 0) {
                        char lastChar = currentText.charAt(currentText.length() - 1);
                        if (isOperator(String.valueOf(lastChar)) || lastChar == '.') {
                            currentText.setLength(currentText.length() - 1);
                        }
                    }
                    currentText.append(buttonText);
                }

                // 更新显示
                tvDisplay.setText(currentText.toString());

                // 强制显示屏滚动到最右侧
                hsvDisplay.post(new Runnable() {
                    @Override
                    public void run() {
                        hsvDisplay.fullScroll(View.FOCUS_RIGHT);
                    }
                });
            }
        };

        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
                R.id.btnAdd, R.id.btnSub, R.id.btnMul, R.id.btnDiv,
                R.id.btnDot, R.id.btnEq, R.id.btnAc, R.id.btnDel
        };

        for (int id : buttonIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(listener);
            }
        }
    }

    private boolean isOperator(String s) {
        return s.equals("+") || s.equals("-") || s.equals("×") || s.equals("÷");
    }

    // 纯血高精度数学引擎
    private BigDecimal eval(final String str) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            BigDecimal parse() {
                nextChar();
                BigDecimal x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
                return x;
            }
            BigDecimal parseExpression() {
                BigDecimal x = parseTerm();
                for (;;) {
                    if      (eat('+')) x = x.add(parseTerm());
                    else if (eat('-')) x = x.subtract(parseTerm());
                    else return x;
                }
            }
            BigDecimal parseTerm() {
                BigDecimal x = parseFactor();
                for (;;) {
                    if      (eat('*')) x = x.multiply(parseFactor());
                    else if (eat('/')) x = x.divide(parseFactor(), 16, RoundingMode.HALF_UP); // 运算时保留极高精度防溢出
                    else return x;
                }
            }
            BigDecimal parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return parseFactor().negate();
                BigDecimal x;
                int startPos = this.pos;
                if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = new BigDecimal(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected: " + (char)ch);
                }
                return x;
            }
        }.parse();
    }

    private String formatResult(BigDecimal bd) {
        if (bd == null) return "Error";
        try {
            bd = bd.stripTrailingZeros();
            if (bd.scale() > 8) {
                // 超过8位小数截断加省略号
                return bd.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "...";
            } else {
                return bd.toPlainString();
            }
        } catch (Exception e) {
            return "Error";
        }
    }
}