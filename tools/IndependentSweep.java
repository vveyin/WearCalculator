package com.nickwoluff.wearcalculator;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 第二份验证程序：把 IndependentTest 发现的几类错误「扫」出精确边界。
 *
 * <p>参考值复用 IndependentTest 里那套独立实现（120 位 Machin π + 泰勒级数 + 二分反解），
 * 不依赖 MathEngine 的输出。
 *
 * <p>编译：javac -encoding UTF-8 -d build\indep app\src\main\java\com\nickwoluff\wearcalculator\MathEngine.java tools\IndependentTest.java tools\IndependentSweep.java
 * <br>运行：java -Dfile.encoding=UTF-8 -cp build\indep com.nickwoluff.wearcalculator.IndependentSweep
 */
public final class IndependentSweep {

    static final MathContext C60 = IndependentTest.C60;

    static int badCount = 0;
    static int caseCount = 0;

    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        } catch (Exception ignored) {
        }
        IndependentTest.initConstants();

        System.out.println("################ MathEngine 边界扫描（IndependentSweep） ################");
        sweepCosDegrees();
        sweepCosRadians();
        sweepSinRadians();
        sweepSinDegrees();
        sweepTanFromCosBug();
        sweepTinyValues();
        sweepFactorialOverflow();
        sweepPercentBeforeParen();
        sweepFunctionNames();
        probeClampMechanism();
        System.out.println();
        System.out.println("──────────────────────────────────────────────────────────────");
        System.out.println("扫描完毕：共 " + caseCount + " 个用例，其中屏幕可见错误 " + badCount + " 个。");
    }

    // ==================================================================
    // cos 的角度扫描（分象限看哪一段错）
    // ==================================================================

    static void sweepCosDegrees() {
        System.out.println();
        System.out.println("=== A. cos 扫描（度，步长 5°）===");
        int[] badFrom = {-1, -1};
        StringBuilder badList = new StringBuilder();
        for (int deg = 0; deg <= 360; deg += 5) {
            BigDecimal ref = IndependentTest.refCos(IndependentTest.refRadFromDeg(new BigDecimal(deg)));
            String arg = String.valueOf(deg);
            BigDecimal err = check("cos(" + arg + ")", MathEngine.DEG, ref);
            if (err == null) continue;
            boolean visible = err.compareTo(new BigDecimal("5e-9")) > 0;
            if (visible) {
                if (badList.length() > 0) badList.append(' ');
                badList.append(deg).append('°');
            }
        }
        System.out.println("  → 8 位小数可见误差的角度：" + (badList.length() == 0 ? "无" : badList.toString()));
    }

    static void sweepCosRadians() {
        System.out.println();
        System.out.println("=== B. cos 弧度扫描（步长 π/12）===");
        StringBuilder badList = new StringBuilder();
        for (int k = 0; k <= 24; k++) {
            BigDecimal x = IndependentTest.PI.multiply(new BigDecimal(k), IndependentTest.C110)
                    .divide(new BigDecimal("12"), IndependentTest.C110);
            BigDecimal ref = IndependentTest.refCos(x);
            String label = k + "pi/12";
            BigDecimal err = checkRaw("cos(" + label + ")", MathEngine.RAD, ref);
            if (err != null && err.compareTo(new BigDecimal("5e-9")) > 0) {
                if (badList.length() > 0) badList.append(' ');
                badList.append(label);
            }
        }
        System.out.println("  → 8 位小数可见误差：" + (badList.length() == 0 ? "无" : badList.toString()));
    }

    // ==================================================================
    // sin 的大参数扫描
    // ==================================================================

    static void sweepSinRadians() {
        System.out.println();
        System.out.println("=== C. sin 弧度大参数扫描 ===");
        String[] xs = {"5", "10", "15", "20", "25", "30", "40", "50", "60", "75", "100", "150",
                "200", "300", "400", "500", "700", "1000", "1500", "2000", "2500", "3000",
                "3141", "3141.6", "3142", "4000", "10000", "100000"};
        for (String s : xs) {
            BigDecimal x = new BigDecimal(s);
            BigDecimal ref = IndependentTest.refSin(x);
            check("sin(" + s + ")", MathEngine.RAD, ref);
        }
    }

    static void sweepSinDegrees() {
        System.out.println();
        System.out.println("=== D. sin 角度大参数扫描 ===");
        String[] ds = {"200", "360", "500", "1000", "2000", "5000", "10000", "30000", "50000",
                "100000", "123456.789", "150000", "179000", "179999", "180000", "180001", "200000", "1000000"};
        for (String s : ds) {
            BigDecimal ref = IndependentTest.refSin(IndependentTest.refRadFromDeg(new BigDecimal(s)));
            check("sin(" + s + ")", MathEngine.DEG, ref);
        }
    }

    static void sweepTanFromCosBug() {
        System.out.println();
        System.out.println("=== E. tan 受 cos 缺陷影响的角度（度）===");
        String[] ds = {"100", "120", "135", "150", "170", "180", "200", "225", "240", "250", "260", "300"};
        for (String s : ds) {
            BigDecimal rad = IndependentTest.refRadFromDeg(new BigDecimal(s));
            BigDecimal ref = IndependentTest.refTan(rad);
            if (ref == null) continue;
            check("tan(" + s + ")", MathEngine.DEG, ref);
        }
    }

    // ==================================================================
    // 极小值被抹成 0
    // ==================================================================

    static void sweepTinyValues() {
        System.out.println();
        System.out.println("=== F. 极小值（evaluate 末尾 setScale(53) 的影响）===");
        for (int k = 50; k <= 60; k++) {
            tinyCase("10^-" + k, BigDecimal.ONE.divide(new BigDecimal(BigInteger.TEN.pow(k)), C60));
        }
        tinyCase("10^-100", BigDecimal.ONE.divide(new BigDecimal(BigInteger.TEN.pow(100)), C60));
        tinyCase("10^-308", BigDecimal.ONE.divide(new BigDecimal(BigInteger.TEN.pow(308)), C60));
        tinyCase("2^-1074", BigDecimal.ONE.divide(new BigDecimal(BigInteger.TWO.pow(1074)), C60));
        tinyCase("2^-200", BigDecimal.ONE.divide(new BigDecimal(BigInteger.TWO.pow(200)), C60));
        tinyCase("0.5^180", BigDecimal.ONE.divide(new BigDecimal(BigInteger.TWO.pow(180)), C60));
        tinyCase("0.5^176", BigDecimal.ONE.divide(new BigDecimal(BigInteger.TWO.pow(176)), C60));
        tinyCase("1÷(3^200)", BigDecimal.ONE.divide(new BigDecimal(BigInteger.valueOf(3).pow(200)), C60));
        tinyCase("0.0000000000000000000000000000000000000000000000000001+0",
                new BigDecimal("0.0000000000000000000000000000000000000000000000000001"));
        tinyCase("0.000000000000000000000000000000000000000000000000000001+0",
                new BigDecimal("0.000000000000000000000000000000000000000000000000000001"));
    }

    static void tinyCase(String expr, BigDecimal ref) {
        caseCount++;
        BigDecimal v = null;
        String err = null;
        try {
            v = MathEngine.evaluate(expr, MathEngine.DEG);
        } catch (MathEngine.MathException e) {
            err = e.getMessage();
        } catch (Throwable t) {
            err = t.toString();
        }
        String display;
        try {
            display = MathEngine.calculate(expr, MathEngine.DEG);
        } catch (Throwable t) {
            display = "(" + (err == null ? t.toString() : err) + ")";
        }
        if (err != null || v == null) {
            System.out.println(String.format("%-46s 屏幕=%-24s 说明=报错 %s", expr, display, err));
            return;
        }
        BigDecimal diff = v.subtract(ref).abs();
        BigDecimal rel = ref.signum() == 0 ? diff : diff.divide(ref.abs(), C60);
        boolean wiped = v.signum() == 0 && ref.signum() != 0;
        if (wiped) badCount++;
        System.out.println(String.format("%-46s 屏幕=%-24s 真值=%-12s 相对误差=%-10s %s",
                expr, display, IndependentTest.brief(ref), IndependentTest.brief(rel),
                wiped ? "★ 被抹成 0" : "正常"));
    }

    // ==================================================================
    // 阶乘的 intValue 溢出
    // ==================================================================

    static void sweepFactorialOverflow() {
        System.out.println();
        System.out.println("=== G. 阶乘参数的 intValue() 溢出 ===");
        String[] args = {"4294967296", "4294967297", "4294968296", "8589934592", "12884901888",
                "2147483648", "2147483649", "6442450944", "10000000000", "999999999999999999999"};
        for (String a : args) {
            caseCount++;
            String expr = a + "!";
            String out;
            try {
                out = MathEngine.calculate(expr, MathEngine.DEG);
            } catch (MathEngine.MathException e) {
                out = "MathException: " + e.getMessage();
            } catch (Throwable t) {
                out = t.getClass().getName() + ": " + t.getMessage();
            }
            long intVal;
            try {
                intVal = new BigDecimal(a).intValue();
            } catch (Throwable t) {
                intVal = 0;
            }
            String verdict;
            if (out.equals("MathException: 阶乘数字太大（上限 2000）")) {
                verdict = "正确报错";
            } else if (out.equals("1")) {
                verdict = "★ 错误：直接返回 1（应为超上限报错）";
                badCount++;
            } else if (out.equals("1000!")) {
                verdict = "★ 错误：返回了别的数";
                badCount++;
            } else {
                long n = intVal;
                if (n >= 0 && n <= 2000) {
                    BigDecimal expected;
                    try {
                        expected = new BigDecimal(IndependentTest.bigFactorial((int) n));
                    } catch (Throwable t) {
                        expected = null;
                    }
                    String disp = expected == null ? "?" : IndependentTest.refFormat(expected);
                    verdict = "★ 错误：被当成 " + n + "!（应显示 " + disp + "）";
                    badCount++;
                } else {
                    verdict = "恰好报错（intValue=" + n + "，纯属侥幸）";
                }
            }
            System.out.println(String.format("%-24s intValue()=%-12d 引擎=%-42s %s",
                    expr, intVal, shorten(out, 42), verdict));
        }
    }

    // ==================================================================
    // % 后面跟括号 / 函数
    // ==================================================================

    static void sweepPercentBeforeParen() {
        System.out.println();
        System.out.println("=== H. 「%」后面跟括号或函数时被当成百分号 ===");
        String[][] cases = {
                {"10%3", "1"}, {"10%(3)", "1"}, {"10%3.0", "1"},
                {"7%(0-2)", "1"}, {"10%pi", null}, {"10%sin(30)", null}, {"10%e", null},
                {"(10)%(3)", "1"}, {"1+10%(3)", "2"}, {"10% (3)", "1"}, {"10%\t(3)", "1"},
        };
        for (String[] c : cases) {
            caseCount++;
            String expr = c[0];
            String out;
            try {
                out = MathEngine.calculate(expr, MathEngine.DEG);
            } catch (MathEngine.MathException e) {
                out = "MathException: " + e.getMessage();
            } catch (Throwable t) {
                out = t.getClass().getName();
            }
            String expected = c[1];
            String verdict;
            if (expected == null) {
                verdict = "（无固定期望，仅记录）";
            } else {
                boolean ok = false;
                try {
                    ok = new BigDecimal(out).compareTo(new BigDecimal(expected)) == 0;
                } catch (NumberFormatException ignored) {
                }
                verdict = ok ? "正确" : ("★ 错误：按取模应为 " + expected);
                if (!ok) badCount++;
            }
            System.out.println(String.format("%-16s 引擎=%-30s %s", expr, out, verdict));
        }
    }

    // ==================================================================
    // 文档里列出的函数名哪些真的能调用
    // ==================================================================

    static void sweepFunctionNames() {
        System.out.println();
        System.out.println("=== I. 文档列出的函数/常量名是否可用 ===");
        String[][] funcs = {
                {"sin(30)", "0.5"}, {"cos(60)", "0.5"}, {"tan(45)", "1"},
                {"asin(0.5)", "30"}, {"acos(0.5)", "60"}, {"atan(1)", "45"},
                {"sinh(0)", "0"}, {"cosh(0)", "1"}, {"tanh(0)", "0"},
                {"ln(1)", "0"}, {"lg(100)", "2"}, {"log(2,8)", "3"},
                {"sqrt(9)", "3"}, {"cbrt(27)", "3"}, {"root(3,27)", "3"},
                {"inv(2)", "0.5"}, {"abs(0-3)", "3"}, {"exp(0)", "1"},
                {"pow(2,3)", "8"}, {"sqr(3)", "9"}, {"deg(30)", null}, {"rad(30)", null},
                {"arcsin(0.5)", "30"}, {"arccos(0.5)", "60"}, {"arctan(1)", "45"},
                {"sqrt(2)", null}, {"pi", null}, {"e", null}, {"ans", null},
        };
        for (String[] f : funcs) {
            caseCount++;
            String expr = f[0];
            String out;
            String kind = "OK";
            try {
                out = MathEngine.calculate(expr, MathEngine.DEG);
            } catch (MathEngine.MathException e) {
                out = "MathException: " + e.getMessage();
                kind = "报错";
            } catch (Throwable t) {
                out = t.getClass().getName() + ": " + t.getMessage();
                kind = "原生异常";
            }
            String verdict = "";
            if (f[1] != null) {
                boolean ok = false;
                try {
                    ok = new BigDecimal(out).compareTo(new BigDecimal(f[1])) == 0;
                } catch (NumberFormatException ignored) {
                }
                if (!ok) {
                    verdict = "★ 期望 " + f[1];
                    badCount++;
                } else {
                    verdict = "结果正确";
                }
            } else if (kind.equals("报错") && (expr.startsWith("exp") || expr.startsWith("deg"))) {
                verdict = "★ 文档里列出的函数无法调用（名字里的 e 被 normalize 换成了常量 e）";
                badCount++;
            }
            System.out.println(String.format("%-14s 引擎=%-44s %s", expr, shorten(out, 44), verdict));
        }
    }

    // ==================================================================
    // 定位「极小值变 0」发生在哪一步
    // ==================================================================

    static void probeClampMechanism() {
        System.out.println();
        System.out.println("=== J. 「极小值变 0」的机制定位 ===");
        // 1) format 本身能不能显示 1E-308 / 1E-54？
        String[] lits = {"1E-308", "1E-54", "1E-53", "4.9406564584124654E-324", "1E-100"};
        for (String lit : lits) {
            System.out.println(String.format("  format(new BigDecimal(\"%s\")) = %s", lit,
                    MathEngine.format(new BigDecimal(lit))));
        }
        // 2) evaluate 出来是什么？
        String[] exprs = {"10^-53", "10^-54", "10^-308", "2^-1074"};
        for (String e : exprs) {
            try {
                BigDecimal v = MathEngine.evaluate(e, MathEngine.DEG);
                System.out.println(String.format("  evaluate(\"%s\") = %s   (precision=%d, scale=%d)",
                        e, v.toPlainString().length() > 40 ? v.toString() : v.toPlainString(), v.precision(), v.scale()));
            } catch (Throwable t) {
                System.out.println("  evaluate(\"" + e + "\") 抛异常 " + t);
            }
        }
        // 3) 复现 evaluate 末尾那句 setScale(53) 的效果：幂运算的真实中间结果
        MathContext mc45 = new MathContext(45, RoundingMode.HALF_UP);
        for (int k : new int[]{53, 54, 55, 100, 308}) {
            BigDecimal pow = new BigDecimal(BigInteger.TEN.pow(k));
            BigDecimal raw = BigDecimal.ONE.divide(pow, mc45);
            BigDecimal clamped = raw.scale() > 53 ? raw.setScale(53, RoundingMode.HALF_UP) : raw;
            System.out.println(String.format("  10^-%-4d 中间结果 scale=%-4d 值=%-16s → 按 scale>53 收缩后 = %s",
                    k, raw.scale(), raw.toString(), clamped.toPlainString().length() > 30 ? clamped.toString() : clamped.toPlainString()));
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 比较一次求值（角度制参数已经换算好），返回绝对误差；出错返回 null。 */
    static BigDecimal check(String expr, int mode, BigDecimal ref) {
        return checkRaw(expr, mode, ref);
    }

    static BigDecimal checkRaw(String expr, int mode, BigDecimal ref) {
        caseCount++;
        BigDecimal v;
        try {
            v = MathEngine.evaluate(expr, mode);
        } catch (MathEngine.MathException e) {
            System.out.println(String.format("%-28s %-3s 报错：%s", expr, mode == 0 ? "DEG" : "RAD", e.getMessage()));
            return null;
        } catch (Throwable t) {
            System.out.println(String.format("%-28s %-3s ★ 原生异常 %s", expr, mode == 0 ? "DEG" : "RAD", t));
            badCount++;
            return null;
        }
        BigDecimal err = v.subtract(ref).abs();
        boolean visible = err.compareTo(new BigDecimal("5e-9")) > 0;
        if (visible) badCount++;
        System.out.println(String.format("%-28s %-3s 引擎=%-24s 真值=%-14s 绝对误差=%-12s %s",
                expr, mode == 0 ? "DEG" : "RAD", IndependentTest.brief(v), IndependentTest.brief(ref),
                IndependentTest.brief(err), visible ? "★ 屏幕可见错误" : "正常（误差 < 5e-9）"));
        return err;
    }

    static String shorten(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
