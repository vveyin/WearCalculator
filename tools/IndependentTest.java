package com.nickwoluff.wearcalculator;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 独立验证程序（不属于 app 构建，只在 tools/ 下运行）。
 *
 * <p>刻意与 tools/MathEngineTest.java 走完全不同的路子：
 * <ul>
 *   <li>期望值全部由程序算出来 —— 本文件自带一份独立的 π（Machin + BigInteger，120 位）、
 *       e（级数，80 位）、sin/cos（先按 120 位 π 归约再做泰勒级数）、asin/atan（二分法反解），
 *       不依赖 MathEngine 的任何输出，也不依赖手算。</li>
 *   <li>再用恒等式（tan(atan x)=x、asin+acos=90°、sin²+cos²=1、Machin 的
 *       atan(1/2)+atan(1/3)=π/4 …）做不依赖外部参考的自洽检查。</li>
 *   <li>对照 BigInteger 的精确阶乘 / 精确幂验证大数结果。</li>
 *   <li>自带一个按文档重写的独立 format() 实现，用来对照 MathEngine.format 的字符串。</li>
 * </ul>
 *
 * <p>编译：javac -encoding UTF-8 -d build\indep app\src\main\java\com\nickwoluff\wearcalculator\MathEngine.java tools\IndependentTest.java
 * <br>运行：java -Dfile.encoding=UTF-8 -cp build\indep com.nickwoluff.wearcalculator.IndependentTest
 */
public final class IndependentTest {

    // ------------------------------------------------------------------
    // 独立参考实现用到的常量与上下文
    // ------------------------------------------------------------------

    static final MathContext C110 = new MathContext(110, RoundingMode.HALF_UP);
    static final MathContext C60 = new MathContext(60, RoundingMode.HALF_UP);
    static final MathContext C45 = new MathContext(45, RoundingMode.HALF_UP);
    static final MathContext C30 = new MathContext(30, RoundingMode.HALF_UP);

    static final BigDecimal TWO = new BigDecimal("2");
    static final BigDecimal ONE = BigDecimal.ONE;
    static final BigDecimal ZERO = BigDecimal.ZERO;
    static final BigDecimal HALF = new BigDecimal("0.5");
    static final BigDecimal SERIES_STOP = BigDecimal.ONE.scaleByPowerOfTen(-65);

    /** 本程序自己算的 π，120 位有效数字（Machin 公式 + BigInteger）。 */
    static BigDecimal PI;
    /** 本程序自己算的 e，80 位有效数字。 */
    static BigDecimal E;

    static long TIME_BUDGET_MS = 20000L;

    // ------------------------------------------------------------------
    // 结果收集
    // ------------------------------------------------------------------

    static final List<String[]> ROWS = new ArrayList<>();
    static int nPass, nFail, nSuspect;

    static int rtPass, rtFail, rtTruncSuspect, rtSciBad, rtSkipped;

    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
        } catch (Exception ignored) {
        }
        long allStart = System.currentTimeMillis();
        initConstants();

        System.out.println("################ MathEngine 独立验证 ################");
        System.out.println("本程序不修改 app/ 下任何文件，只读取 MathEngine 的公开 API。");
        System.out.println();

        refSanity();
        sec1();
        sec2();
        sec3();
        sec4();
        sec5();
        sec6();
        sec7();
        sec8();
        sec9();
        sec10();
        sec11();
        sec12();
        summary(System.currentTimeMillis() - allStart);
    }

    // ==================================================================
    // 独立的参考数学实现
    // ==================================================================

    /** 供本文件和 tools/IndependentSweep.java 共用：初始化独立参考常数。 */
    static void initConstants() {
        PI = machinPi(120);
        E = refE(80);
    }

    /** Machin：π = 16·atan(1/5) − 4·atan(1/239)，整数定点运算。 */    static BigDecimal machinPi(int digits) {
        int scale = digits + 20;
        BigInteger a = arctanInverse(5, scale);
        BigInteger b = arctanInverse(239, scale);
        BigInteger pi = a.multiply(BigInteger.valueOf(16)).subtract(b.multiply(BigInteger.valueOf(4)));
        return new BigDecimal(pi, scale).round(new MathContext(digits, RoundingMode.HALF_UP));
    }

    static BigInteger arctanInverse(int inverse, int scale) {
        BigInteger x = BigInteger.TEN.pow(scale).divide(BigInteger.valueOf(inverse));
        BigInteger xSquared = BigInteger.valueOf((long) inverse * inverse);
        BigInteger term = x;
        BigInteger sum = x;
        int n = 1;
        while (true) {
            term = term.divide(xSquared);
            if (term.signum() == 0) break;
            BigInteger addend = term.divide(BigInteger.valueOf(2L * n + 1));
            if (addend.signum() == 0) break;
            sum = (n % 2 == 1) ? sum.subtract(addend) : sum.add(addend);
            n++;
        }
        return sum;
    }

    /** e = Σ 1/k!，整数定点运算。 */
    static BigDecimal refE(int digits) {
        int scale = digits + 20;
        BigInteger unity = BigInteger.TEN.pow(scale);
        BigInteger sum = unity;
        BigInteger term = unity;
        for (int k = 1; k < 6000; k++) {
            term = term.divide(BigInteger.valueOf(k));
            if (term.signum() == 0) break;
            sum = sum.add(term);
        }
        return new BigDecimal(sum, scale).round(new MathContext(digits, RoundingMode.HALF_UP));
    }

    /** 用 120 位 π 把弧度归约到 [−π, π]。 */
    static BigDecimal refReduce(BigDecimal x) {
        BigDecimal twoPi = PI.multiply(TWO, C110);
        BigDecimal r = x.remainder(twoPi, C110);
        if (r.compareTo(PI) > 0) r = r.subtract(twoPi, C110);
        if (r.compareTo(PI.negate()) < 0) r = r.add(twoPi, C110);
        return r;
    }

    /** 参考 sin：归约到 [−π, π] 之后泰勒级数（60 位上下文）。 */
    static BigDecimal refSin(BigDecimal xRad) {
        BigDecimal x = refReduce(xRad);
        BigDecimal x2 = x.multiply(x, C60);
        BigDecimal term = x;
        BigDecimal sum = x;
        for (int n = 1; n < 300; n++) {
            term = term.multiply(x2, C60).divide(BigDecimal.valueOf((long) (2 * n) * (2 * n + 1)), C60);
            sum = (n % 2 == 1) ? sum.subtract(term, C60) : sum.add(term, C60);
            if (term.abs().compareTo(SERIES_STOP) < 0) break;
        }
        return sum;
    }

    /** 参考 cos。 */
    static BigDecimal refCos(BigDecimal xRad) {
        BigDecimal x = refReduce(xRad);
        BigDecimal x2 = x.multiply(x, C60);
        BigDecimal term = ONE;
        BigDecimal sum = ONE;
        for (int n = 1; n < 300; n++) {
            term = term.multiply(x2, C60).divide(BigDecimal.valueOf((long) (2 * n - 1) * (2 * n)), C60);
            sum = (n % 2 == 1) ? sum.subtract(term, C60) : sum.add(term, C60);
            if (term.abs().compareTo(SERIES_STOP) < 0) break;
        }
        return sum;
    }

    static BigDecimal refTan(BigDecimal xRad) {
        BigDecimal c = refCos(xRad);
        if (c.abs().compareTo(new BigDecimal("1e-20")) < 0) return null;
        return refSin(xRad).divide(c, C60);
    }

    /** 度 -> 弧度（用 120 位 π）。 */
    static BigDecimal refRadFromDeg(BigDecimal deg) {
        return deg.multiply(PI, C110).divide(BigDecimal.valueOf(180), C110);
    }

    /** 弧度 -> 度。 */
    static BigDecimal refDeg(BigDecimal rad) {
        return rad.multiply(BigDecimal.valueOf(180), C60).divide(PI, C60);
    }

    /** 参考 asin：在 [0, π/2] 上二分反解 sin。 */
    static BigDecimal refAsin(BigDecimal x) {
        int sign = x.signum();
        if (sign == 0) return ZERO;
        BigDecimal a = x.abs();
        if (a.compareTo(ONE) > 0) return null;
        BigDecimal lo = ZERO;
        BigDecimal hi = PI.divide(TWO, C60);
        if (a.compareTo(ONE) == 0) return sign > 0 ? hi : hi.negate();
        for (int i = 0; i < 320; i++) {
            BigDecimal mid = lo.add(hi).divide(TWO, C60);
            if (refSin(mid).compareTo(a) < 0) lo = mid;
            else hi = mid;
        }
        BigDecimal r = lo.add(hi).divide(TWO, C60);
        return sign > 0 ? r : r.negate();
    }

    static BigDecimal refAcos(BigDecimal x) {
        BigDecimal a = refAsin(x);
        if (a == null) return null;
        return PI.divide(TWO, C60).subtract(a, C60);
    }

    /** 参考 atan：|x| > 1 先反射，再在 [0, π/4] 上二分反解 tan。 */
    static BigDecimal refAtan(BigDecimal x) {
        int sign = x.signum();
        if (sign == 0) return ZERO;
        BigDecimal a = x.abs();
        BigDecimal r;
        if (a.compareTo(ONE) > 0) {
            r = PI.divide(TWO, C60).subtract(refAtan(ONE.divide(a, C60)), C60);
        } else {
            BigDecimal lo = ZERO;
            BigDecimal hi = PI.divide(BigDecimal.valueOf(4), C60);
            for (int i = 0; i < 320; i++) {
                BigDecimal mid = lo.add(hi).divide(TWO, C60);
                BigDecimal t = refSin(mid).divide(refCos(mid), C60);
                if (t.compareTo(a) < 0) lo = mid;
                else hi = mid;
            }
            r = lo.add(hi).divide(TWO, C60);
        }
        return sign > 0 ? r : r.negate();
    }

    static BigDecimal refLn2() {
        BigDecimal third = ONE.divide(new BigDecimal("3"), C60);
        BigDecimal z2 = third.multiply(third, C60);
        BigDecimal power = third;
        BigDecimal sum = third;
        for (int k = 1; k < 6000; k++) {
            power = power.multiply(z2, C60);
            BigDecimal addend = power.divide(BigDecimal.valueOf(2L * k + 1), C60);
            sum = sum.add(addend, C60);
            if (addend.abs().compareTo(SERIES_STOP) < 0) break;
        }
        return sum.multiply(TWO, C60);
    }

    static BigDecimal refLn(BigDecimal x) {
        if (x.signum() <= 0) return null;
        BigDecimal LN2 = refLn2();
        int k = 0;
        BigDecimal m = x;
        while (m.compareTo(new BigDecimal("1.5")) >= 0) {
            m = m.divide(TWO, C60);
            k++;
        }
        while (m.compareTo(new BigDecimal("0.75")) < 0) {
            m = m.multiply(TWO, C60);
            k--;
        }
        BigDecimal z = m.subtract(ONE, C60).divide(m.add(ONE, C60), C60);
        BigDecimal z2 = z.multiply(z, C60);
        BigDecimal power = z;
        BigDecimal sum = z;
        for (int n = 1; n < 6000; n++) {
            power = power.multiply(z2, C60);
            BigDecimal addend = power.divide(BigDecimal.valueOf(2L * n + 1), C60);
            sum = sum.add(addend, C60);
            if (addend.abs().compareTo(SERIES_STOP) < 0) break;
        }
        BigDecimal r = sum.multiply(TWO, C60);
        if (k != 0) r = r.add(new BigDecimal(k).multiply(LN2, C60), C60);
        return r;
    }

    static BigDecimal refExp(BigDecimal x) {
        BigDecimal k = new BigDecimal(x.toBigInteger());
        BigDecimal f = x.subtract(k, C60);
        BigDecimal term = ONE;
        BigDecimal sum = ONE;
        for (int n = 1; n < 400; n++) {
            term = term.multiply(f, C60).divide(BigDecimal.valueOf(n), C60);
            sum = sum.add(term, C60);
            if (term.abs().compareTo(SERIES_STOP) < 0) break;
        }
        int kk;
        try {
            kk = k.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
        if (kk == 0) return sum;
        BigDecimal p = E.pow(Math.abs(kk), new MathContext(80, RoundingMode.HALF_UP));
        return kk > 0 ? sum.multiply(p, C60) : sum.divide(p, C60);
    }

    static BigDecimal refSinh(BigDecimal x) {
        BigDecimal a = refExp(x), b = refExp(x.negate());
        if (a == null || b == null) return null;
        return a.subtract(b, C60).divide(TWO, C60);
    }

    static BigDecimal refCosh(BigDecimal x) {
        BigDecimal a = refExp(x), b = refExp(x.negate());
        if (a == null || b == null) return null;
        return a.add(b, C60).divide(TWO, C60);
    }

    static BigInteger bigFactorial(int n) {
        BigInteger acc = BigInteger.ONE;
        for (int i = 2; i <= n; i++) acc = acc.multiply(BigInteger.valueOf(i));
        return acc;
    }

    /** 按题面文档重写的独立 format()：8 位小数、去尾零、精度被砍时加 ...、整部超 19 位转科学计数法。 */
    static String refFormat(BigDecimal value) {
        if (value == null) return "0";
        if (value.signum() == 0) return "0";
        BigDecimal stripped = value.stripTrailingZeros();
        int integerDigits = stripped.precision() - stripped.scale();
        if (integerDigits > 19 || integerDigits < -6) {
            BigDecimal mantissa = stripped.round(new MathContext(10, RoundingMode.HALF_UP));
            int exponent = mantissa.precision() - mantissa.scale() - 1;
            mantissa = mantissa.movePointLeft(exponent).stripTrailingZeros();
            return mantissa.toPlainString() + "e" + exponent;
        }
        if (stripped.scale() > 8) {
            BigDecimal cut = stripped.setScale(8, RoundingMode.HALF_UP);
            BigDecimal error = cut.subtract(stripped).abs();
            BigDecimal tolerance = stripped.abs().multiply(new BigDecimal("1e-9"));
            if (error.compareTo(tolerance) > 0) return cut.stripTrailingZeros().toPlainString() + "...";
            return cut.stripTrailingZeros().toPlainString();
        }
        return stripped.stripTrailingZeros().toPlainString();
    }

    /** 屏幕上「应该」出现的干净值：0 / ±0.5 / ±1，其余按独立 format 规则展示。 */
    static String expectedDisplay(BigDecimal ref) {
        BigDecimal a = ref.abs();
        BigDecimal[] clean = {ZERO, HALF, ONE};
        for (BigDecimal c : clean) {
            if (a.subtract(c).abs().compareTo(new BigDecimal("1e-40")) < 0) {
                boolean neg = ref.signum() < 0 && c.signum() != 0;
                return neg ? c.negate().toPlainString() : c.toPlainString();
            }
        }
        return refFormat(ref);
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // ==================================================================
    // 参考实现自检（先证明参考值可信，再去测引擎）
    // ==================================================================

    static void refSanity() {
        header("0. 参考实现自检（证明本程序的参考值本身是对的）");
        String piKnown = "3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798";
        String eKnown = "2.71828182845904523536028747135266249775724709369995957496696762772407663035354759457138217852516642";
        literalCheck("本程序 machinPi(120)", PI, piKnown);
        literalCheck("本程序 refE(80)", E, eKnown);
        BigDecimal sinPi6 = refSin(PI.divide(new BigDecimal("6"), C110));
        literalCheck("refSin(pi/6) 应为 0.5", sinPi6, "0.5");
        BigDecimal atan1 = refAtan(ONE);
        literalCheck("refAtan(1) 应为 pi/4", atan1, PI.divide(BigDecimal.valueOf(4), C60).toPlainString());
        // 引擎公开的 pi 常量（通过表达式求值）与独立 120 位 π 的差
        BigDecimal enginePi;
        try {
            enginePi = MathEngine.evaluate("pi", MathEngine.RAD);
        } catch (Throwable t) {
            emit("引擎 pi 常量", "-", "-", "FAIL", "求值失败：" + t.getMessage());
            return;
        }
        BigDecimal piErr = enginePi.subtract(PI).abs().divide(PI, C60);
        String st = piErr.compareTo(new BigDecimal("1e-25")) <= 0 ? "PASS" : "FAIL";
        emit("引擎 pi 常量", "-", brief(enginePi), st, "与独立 120 位 π 的相对差 = " + brief(piErr));
    }

    static void literalCheck(String what, BigDecimal actual, String known) {
        BigDecimal ref = new BigDecimal(known);
        BigDecimal err = actual.subtract(ref).abs();
        String st = err.compareTo(new BigDecimal("1e-55")) <= 0 ? "PASS" : "FAIL";
        emit(what, "-", brief(actual), st, "与已知常数差 = " + brief(err));
    }

    // ==================================================================
    // 1. 反三角函数
    // ==================================================================

    static void sec1() {
        header("1. 反三角函数精度（参考值 = 本程序的 π + 二分反解，非手算）");
        cmp("asin(0.999999)", MathEngine.DEG, refDeg(refAsin(bd("0.999999"))), "定义域边缘");
        cmp("asin(-0.999999)", MathEngine.DEG, refDeg(refAsin(bd("-0.999999"))), "定义域边缘");
        cmp("asin(1)", MathEngine.DEG, bd("90"), "端点");
        cmp("asin(-1)", MathEngine.DEG, bd("-90"), "端点");
        cmp("asin(0.999999)", MathEngine.RAD, refAsin(bd("0.999999")), "定义域边缘（弧度）");
        cmp("asin(0.5)", MathEngine.DEG, bd("30"), "精确值");
        cmp("acos(-1)", MathEngine.DEG, bd("180"), "端点");
        cmp("acos(0.000001)", MathEngine.DEG, refDeg(refAcos(bd("0.000001"))), "靠近 π/2");
        cmp("acos(0.999999)", MathEngine.DEG, refDeg(refAcos(bd("0.999999"))), "靠近 0");
        cmp("acos(1)", MathEngine.DEG, bd("0"), "端点");
        cmp("acos(0)", MathEngine.DEG, bd("90"), "π/2");
        cmp("atan(0.001)", MathEngine.DEG, refDeg(refAtan(bd("0.001"))), "小参数");
        cmp("atan(1000)", MathEngine.DEG, refDeg(refAtan(bd("1000"))), "|x|>1 反射分支");
        cmp("atan(0.5)", MathEngine.DEG, refDeg(refAtan(bd("0.5"))), "级数阈值 0.5");
        cmp("atan(0.5000000001)", MathEngine.DEG, refDeg(refAtan(bd("0.5000000001"))), "刚过阈值");
        cmp("atan(0.4999999999)", MathEngine.DEG, refDeg(refAtan(bd("0.4999999999"))), "刚好在阈值内");
        cmp("atan(1)", MathEngine.DEG, bd("45"), "π/4");
        cmp("atan(1/sqrt(3))", MathEngine.DEG, bd("30"), "π/6");
        cmp("atan(1÷2)+atan(1÷3)", MathEngine.DEG, bd("45"), "Machin 恒等式 = π/4");
        cmp("atan(1)+atan(1÷2)+atan(1÷3)", MathEngine.DEG, bd("90"), "= π/2");
        cmp("asin(0.999999)+acos(0.999999)", MathEngine.DEG, bd("90"), "恒等式 asin+acos=90°");
        cmp("atan(1000)+atan(0.001)", MathEngine.DEG, bd("90"), "恒等式 atan x + atan(1/x)=90°");
        cmp("atan(3)", MathEngine.DEG, refDeg(refAtan(bd("3"))), "归约多级");
        cmp("atan(0.2)", MathEngine.DEG, refDeg(refAtan(bd("0.2"))), "级数");
        cmp("sin(asin(0.999999))", MathEngine.DEG, bd("0.999999"), "往返恒等式");
        cmp("tan(atan(1000))", MathEngine.DEG, bd("1000"), "往返恒等式");
        cmp("tan(atan(0.001))", MathEngine.DEG, bd("0.001"), "往返恒等式");
        cmp("sin(asin(0.3))", MathEngine.RAD, bd("0.3"), "往返恒等式（弧度）");
        cmp("atan(1÷2)", MathEngine.RAD, refAtan(bd("0.5")), "弧度输出");
    }

    // ==================================================================
    // 2. 大角度归约
    // ==================================================================

    static void sec2() {
        header("2. 大角度范围归约（参考值 = 120 位 π 归约 + 泰勒）");
        String[] degs = {"1000000", "1000000000", "123456.789", "1000000000000000000"};
        for (String a : degs) {
            BigDecimal rad = refRadFromDeg(bd(a));
            cmp("sin(" + a + ")", MathEngine.DEG, refSin(rad), "度");
            cmp("cos(" + a + ")", MathEngine.DEG, refCos(rad), "度");
            BigDecimal t = refTan(rad);
            if (t != null) cmp("tan(" + a + ")", MathEngine.DEG, t, "度");
            identity(a, MathEngine.DEG);
        }
        String[] rads = {"1000000", "1000000000", "123456.789", "1000", "7", "3000", "3141", "3142", "6.3", "100"};
        for (String a : rads) {
            BigDecimal r = bd(a);
            cmp("sin(" + a + ")", MathEngine.RAD, refSin(r), "弧度");
            cmp("cos(" + a + ")", MathEngine.RAD, refCos(r), "弧度");
            BigDecimal t = refTan(r);
            if (t != null) cmp("tan(" + a + ")", MathEngine.RAD, t, "弧度");
            identity(a, MathEngine.RAD);
        }
        // 极端角度：参考实现也快到头了，只做 sin²+cos² 自洽检查
        header("2b. 超大角度（参考实现精度不够，只查勾股恒等式）");
        String[] huge = {"1e20", "1000000000000000000000000000000000000000"};
        for (String a : huge) {
            identity(a, MathEngine.DEG);
            identity(a, MathEngine.RAD);
        }
    }

    static void identity(String a, int mode) {
        String expr = "sin(" + a + ")^2+cos(" + a + ")^2";
        R r = exec(expr, mode);
        if (r.raw != null) {
            emit(expr, ml(mode), r.raw.getClass().getSimpleName(), "FAIL", "原生异常 " + r.raw);
            return;
        }
        if (r.errMsg != null) {
            emit(expr, ml(mode), "MathException(" + r.errMsg + ")", "FAIL", "勾股恒等式无法求值");
            return;
        }
        BigDecimal err = r.value.subtract(ONE).abs();
        String st = err.compareTo(new BigDecimal("1e-30")) <= 0 ? "PASS"
                : err.compareTo(new BigDecimal("1e-12")) <= 0 ? "SUSPECT" : "FAIL";
        emit(expr, ml(mode), r.display, st, "sin²+cos²−1 = " + brief(err));
    }

    // ==================================================================
    // 3. 应该吸附成干净值的三角函数
    // ==================================================================

    static void sec3() {
        header("3. 三角函数吸附（屏幕上必须是干净的 0 / ±0.5 / ±1）");
        String[] sinDeg = {"0", "30", "90", "150", "180", "210", "270", "330", "360"};
        for (String a : sinDeg) snapCheck("sin(" + a + ")", MathEngine.DEG, refSin(refRadFromDeg(bd(a))), "度");
        String[] cosDeg = {"0", "60", "90", "120", "180", "240", "270", "300", "360"};
        for (String a : cosDeg) snapCheck("cos(" + a + ")", MathEngine.DEG, refCos(refRadFromDeg(bd(a))), "度");
        String[] tanDeg = {"0", "45", "135", "180", "225", "360"};
        for (String a : tanDeg) snapCheck("tan(" + a + ")", MathEngine.DEG, refTan(refRadFromDeg(bd(a))), "度");
        String[] sinRad = {"0", "pi÷6", "pi÷2", "5pi÷6", "pi", "7pi÷6", "3pi÷2", "11pi÷6", "2pi"};
        for (String a : sinRad) snapCheck("sin(" + a + ")", MathEngine.RAD, refSin(piTimes(a)), "弧度");
        String[] cosRad = {"0", "pi÷3", "pi÷2", "2pi÷3", "pi", "4pi÷3", "3pi÷2", "5pi÷3", "2pi"};
        for (String a : cosRad) snapCheck("cos(" + a + ")", MathEngine.RAD, refCos(piTimes(a)), "弧度");
        String[] tanRad = {"0", "pi÷4", "3pi÷4", "pi", "5pi÷4", "2pi"};
        for (String a : tanRad) snapCheck("tan(" + a + ")", MathEngine.RAD, refTan(piTimes(a)), "弧度");
    }

    /** 把 "pi÷6" / "5pi÷6" / "3pi÷2" / "0" 这类字面量换成参考值（弧度）。 */
    static BigDecimal piTimes(String a) {
        if (!a.contains("pi")) return bd(a);
        String num = a.substring(0, a.indexOf("pi"));
        BigDecimal k = num.isEmpty() ? ONE : bd(num);
        BigDecimal sign = ONE;
        String rest = a.substring(a.indexOf("pi") + 2);
        BigDecimal div = ONE;
        if (rest.startsWith("÷")) {
            String d = rest.substring(1);
            if (d.startsWith("-")) {
                sign = ONE.negate();
                d = d.substring(1);
            }
            div = bd(d);
        }
        return k.multiply(PI, C110).multiply(sign, C110).divide(div, C110);
    }

    static void snapCheck(String expr, int mode, BigDecimal ref, String note) {
        if (ref == null) {
            emit(expr, ml(mode), "-", "SUSPECT", "参考值不可用（tan 无定义？）");
            return;
        }
        Box b = runTimed(expr, mode);
        String m = ml(mode);
        if (b.timeout) {
            emit(expr, m, "<超时>", "FAIL", "超过 " + TIME_BUDGET_MS + "ms 未返回");
            return;
        }
        if (b.fatal != null) {
            emit(expr, m, b.fatal.getClass().getSimpleName(), "FAIL", "求值线程抛出 " + b.fatal);
            return;
        }
        R r = b.r;
        if (r.raw != null) {
            emit(expr, m, r.raw.getClass().getName(), "FAIL", "原生异常：" + r.raw);
            return;
        }
        if (r.errMsg != null) {
            emit(expr, m, "MathException(" + r.errMsg + ")", "FAIL", "本应有数值结果");
            return;
        }
        String expected = expectedDisplay(ref);
        BigDecimal err = r.value.subtract(ref).abs();
        boolean numOk = err.compareTo(new BigDecimal("1e-30")) <= 0;
        boolean strOk = expected.equals(r.display);
        String st = (numOk && strOk) ? "PASS" : (numOk || strOk) ? "SUSPECT" : "FAIL";
        emit(expr, m, r.display, st, "期望屏显=" + expected + " 数值误差=" + brief(err)
                + (note.isEmpty() ? "" : " [" + note + "]"));
    }

    // ==================================================================
    // 4. 阶乘
    // ==================================================================

    static void sec4() {
        header("4. 阶乘（对照 BigInteger 精确值）");
        int[] ns = {0, 1, 5, 20, 21, 170, 171, 1000, 2000};
        for (int n : ns) factorialCheck(n);
        errCase("2001!", MathEngine.DEG, "超过上限，应干净报错");
        errCase("3000!", MathEngine.DEG, "超过上限，应干净报错");
        probe("4294967296!", MathEngine.DEG, "intValue() 溢出探针：2^32");
        probe("4294967297!", MathEngine.DEG, "2^32+1");
        probe("8589934592!", MathEngine.DEG, "2^33");
        probe("10000000000!", MathEngine.DEG, "1e10");
        probe("100000000000000000000!", MathEngine.DEG, "1e20");
    }

    static void factorialCheck(int n) {
        BigInteger exact = bigFactorial(n);
        BigDecimal ref = new BigDecimal(exact);
        String expr = n + "!";
        R r = exec(expr, MathEngine.DEG);
        if (r.raw != null) {
            emit(expr, "DEG", r.raw.getClass().getName(), "FAIL", "原生异常 " + r.raw);
            return;
        }
        if (r.errMsg != null) {
            emit(expr, "DEG", "MathException(" + r.errMsg + ")", "FAIL", "本应算出 " + exact.toString().length() + " 位整数");
            return;
        }
        String expected = refFormat(ref);
        BigDecimal err = r.value.subtract(ref).abs();
        BigDecimal rel = err.divide(ref.abs(), C60);
        boolean dispOk = expected.equals(r.display);
        boolean numOk = rel.compareTo(new BigDecimal("1e-40")) <= 0;
        String st = (dispOk && numOk) ? "PASS" : "FAIL";
        emit(expr, "DEG", r.display, st, "位数=" + exact.toString().length() + " 期望屏显=" + expected
                + " 相对误差=" + brief(rel) + " 耗时=" + ms(r.calcNanos) + "ms");
    }

    // ==================================================================
    // 5. pow 边界
    // ==================================================================

    static void sec5() {
        header("5. pow / 幂运算边界");
        cmp("0^0", MathEngine.DEG, ONE, "约定 0^0=1");
        errCase("0^-1", MathEngine.DEG, "0 的负数次方应报错");
        errCase("0^-0.5", MathEngine.DEG, "同上");
        cmp("(-8)^(1/3)", MathEngine.DEG, bd("-2"), "负底数奇次开方");
        expectValue("(-8)^(2/3)", MathEngine.DEG, bd("4"), "数学上 = ((-8)^2)^(1/3) = 4");
        errCase("(-2)^0.5", MathEngine.DEG, "负数开偶次方应报错");
        cmp("(-2)^3", MathEngine.DEG, bd("-8"), "负底数整数幂");
        cmp("(-2)^-1", MathEngine.DEG, bd("-0.5"), "负底数负幂");
        cmp("(-2)^2", MathEngine.DEG, bd("4"), "");
        cmp("10^308", MathEngine.DEG, new BigDecimal(BigInteger.TEN.pow(308)), "超出 double");
        cmp("10^-308", MathEngine.DEG, ONE.divide(new BigDecimal(BigInteger.TEN.pow(308)), C60), "极小");
        cmp("2^-1074", MathEngine.DEG, ONE.divide(new BigDecimal(BigInteger.TWO.pow(1074)), C60), "double 最小次正规");
        cmp("1^999999999", MathEngine.DEG, ONE, "指数超过精确路线阈值");
        cmp("0.1^0.1", MathEngine.DEG, refExp(refLn(bd("0.1")).multiply(bd("0.1"), C60)), "小数指数");
        cmp("0.5^0.5", MathEngine.DEG, refExp(refLn(HALF).multiply(HALF, C60)), "小数指数");
        cmp("2^100000", MathEngine.DEG, new BigDecimal(BigInteger.TWO.pow(100000)).round(C45), "2^100000");
        cmp("2^100001", MathEngine.DEG, new BigDecimal(BigInteger.TWO.pow(100001)).round(C45), "刚好走小数路线");
        cmp("3^39", MathEngine.DEG, new BigDecimal(BigInteger.valueOf(3).pow(39)), "19 位整数幂");
        cmp("9^20", MathEngine.DEG, new BigDecimal(BigInteger.valueOf(9).pow(20)), "19 位整数幂");
        cmp("2^0.5", MathEngine.DEG, refExp(refLn(TWO).multiply(HALF, C60)), "平方根走 exp/ln");
        cmp("8^(1÷3)", MathEngine.DEG, TWO, "立方根");
        cmp("1.0000001^100000", MathEngine.DEG, refExp(refLn(bd("1.0000001")).multiply(bd("100000"), C60)), "接近 1 的大指数");
        probe("9^999999999", MathEngine.DEG, "exp() 里 intValue 溢出探针");
        probe("2^2000000000", MathEngine.DEG, "指数溢出探针");
        probe("10^1000000000", MathEngine.DEG, "10 的 10 亿次方");
    }

    // ==================================================================
    // 6. 格式化回读一致性（200 条随机表达式）
    // ==================================================================

    static void sec6() {
        header("6. format(evaluate(e)) 回读一致性（200 条随机表达式，判据 1e-8 相对）");
        Random rnd = new Random(20240607L);
        int done = 0;
        int guard = 0;
        while (done < 200 && guard < 40000) {
            guard++;
            String expr = genExpr(rnd, 3);
            R r = exec(expr, MathEngine.DEG);
            if (r.raw != null || r.errMsg != null || r.value == null) {
                rtSkipped++;
                continue;
            }
            done++;
            BigDecimal v = r.value;
            String text = r.display;
            String stripped = text.endsWith("...") ? text.substring(0, text.length() - 3) : text;
            BigDecimal back = null;
            String perr = null;
            try {
                back = MathEngine.evaluate(stripped, MathEngine.DEG);
            } catch (MathEngine.MathException e) {
                perr = e.getMessage();
            } catch (Throwable t) {
                perr = t.toString();
            }
            if (back == null) {
                rtFail++;
                emit(expr, "DEG", text, "FAIL", "回读失败（" + perr + "），串=\"" + stripped + "\"");
                continue;
            }
            BigDecimal diff = back.subtract(v).abs();
            BigDecimal rel = v.signum() == 0 ? diff : diff.divide(v.abs(), C60);
            if (rel.compareTo(new BigDecimal("1e-8")) <= 0) {
                rtPass++;
                continue;
            }
            boolean hasSci = text.contains("e");
            boolean truncationOnly = diff.compareTo(new BigDecimal("6e-9")) <= 0;
            if (hasSci) {
                rtSciBad++;
                emit(expr, "DEG", text, "FAIL", "科学计数法串回读被当成「×e」：原值=" + brief(v)
                        + " 回读=" + brief(back) + " 相对差=" + brief(rel));
            } else if (truncationOnly) {
                rtTruncSuspect++;
                emit(expr, "DEG", text, "SUSPECT", "8 位小数截断所致（绝对差 " + brief(diff)
                        + " ≤5e-9），不算数字损坏，但超出 1e-8 相对判据；原值=" + brief(v));
            } else {
                rtFail++;
                emit(expr, "DEG", text, "FAIL", "原值=" + brief(v) + " 回读=" + brief(back) + " 相对差=" + brief(rel));
            }
        }
        System.out.println("  ── 回读统计：一致 " + rtPass + "，截断型超差 " + rtTruncSuspect
                + "，科学计数法回读错误 " + rtSciBad + "，其它失败 " + rtFail + "，求值报错跳过 " + rtSkipped);
    }

    static String genExpr(Random rnd, int depth) {
        if (depth <= 0) return genLeaf(rnd);
        int k = rnd.nextInt(100);
        if (k < 55) {
            String op = new String[]{"+", "-", "*", "÷"}[rnd.nextInt(4)];
            return "(" + genExpr(rnd, depth - 1) + op + genExpr(rnd, depth - 1) + ")";
        } else if (k < 72) {
            return "(" + genExpr(rnd, depth - 1) + ")^" + rnd.nextInt(4);
        } else if (k < 82) {
            return "sqrt(abs(" + genExpr(rnd, depth - 1) + "))";
        } else if (k < 91) {
            return "sin(" + genLeaf(rnd) + ")";
        } else {
            return "cos(" + genLeaf(rnd) + ")";
        }
    }

    static String genLeaf(Random rnd) {
        int k = rnd.nextInt(100);
        if (k < 70) {
            int a = rnd.nextInt(9900) + 100;
            return new BigDecimal(a).movePointLeft(2).toPlainString();
        } else if (k < 80) {
            return "pi";
        } else if (k < 90) {
            return String.valueOf(rnd.nextInt(12) + 1);
        } else {
            int a = rnd.nextInt(990) + 10;
            return "(0-" + new BigDecimal(a).movePointLeft(2).toPlainString() + ")";
        }
    }

    // ==================================================================
    // 7. 百分号 / 取模
    // ==================================================================

    static void sec7() {
        header("7. 百分号与取模的歧义");
        pctCheck("7%2", bd("1"), "取模");
        pctCheck("7.5%2", bd("1.5"), "取模");
        pctCheck("50%", HALF, "后缀百分号");
        pctCheck("200*10%", bd("20"), "后缀百分号参与乘法");
        pctCheck("(7%2)%", bd("0.01"), "(7 mod 2) 再取百分号");
        pctCheck("1+7%2", bd("2"), "取模优先级");
        pctCheck("7%2+1", bd("2"), "取模优先级");
        pctCheck("(0-7)%2", bd("-1"), "被除数为负");
        pctCheck("7%(0-2)", bd("1"), "除数为负");
        pctCheck("0%", ZERO, "0 的百分号");
        pctCheck("50%%", bd("0.005"), "连续百分号");
        pctCheck("100-10%", bd("99.9"), "百分号是纯除法，不是「百分之十的……」");
        pctCheck("10%*10", bd("1"), "");
        errCase("5%0", MathEngine.DEG, "取模除数为 0");
        errCase("%5", MathEngine.DEG, "百分号出现在开头");
        // 7%2% 的歧义：两种讲法
        R r = exec("7%2%", MathEngine.DEG);
        String candA = "0";      // 7 mod (2%) = 7 mod 0.02 = 0
        String candB = "0.01";   // (7 mod 2)% = 1%
        if (r.raw != null) emit("7%2%", "DEG", r.raw.getClass().getSimpleName(), "FAIL", "原生异常");
        else if (r.errMsg != null) emit("7%2%", "DEG", "MathException(" + r.errMsg + ")", "SUSPECT", "报错，未给出结果");
        else emit("7%2%", "DEG", r.display, "SUSPECT",
                "歧义写法：引擎按 7 mod (2%) = " + candA + " 计算；若用户本意是 (7 mod 2)% 则应为 " + candB
                        + "。结果是 " + (r.display.equals(candA) ? "前者" : r.display.equals(candB) ? "后者" : "都不是"));
    }

    static void pctCheck(String expr, BigDecimal ref, String note) {
        cmp(expr, MathEngine.DEG, ref, note);
    }

    // ==================================================================
    // 8. 隐式乘法
    // ==================================================================

    static void sec8() {
        header("8. 隐式乘法");
        cmp("2pi", MathEngine.DEG, TWO.multiply(PI, C60), "2×π");
        cmp("2e", MathEngine.DEG, TWO.multiply(E, C60), "2×e");
        cmp("3(4)", MathEngine.DEG, bd("12"), "3×(4)");
        cmp("(1+1)(2+2)", MathEngine.DEG, bd("8"), "(1+1)×(2+2)");
        cmp("2(3)4", MathEngine.DEG, bd("24"), "2×3×4");
        cmp("2sin(30)", MathEngine.DEG, ONE, "2×sin(30°)");
        cmp("sin(30)2", MathEngine.DEG, ONE, "sin(30°)×2");
        cmp("pi2", MathEngine.DEG, TWO.multiply(PI, C60), "π×2");
        cmp("e2", MathEngine.DEG, TWO.multiply(E, C60), "e×2");
        cmp("pi2pi", MathEngine.DEG, TWO.multiply(PI, C60).multiply(PI, C60), "π×2×π");
        cmp("2(3+4)5", MathEngine.DEG, bd("70"), "2×7×5");
        cmp("(2)3", MathEngine.DEG, bd("6"), "(2)×3");
        cmp("3!2", MathEngine.DEG, bd("12"), "3!×2");
        cmp("2^2pi", MathEngine.DEG, new BigDecimal("4").multiply(PI, C60), "2^2×π（不是 2^(2π)）");
        cmp("sin30", MathEngine.DEG, HALF, "函数不带括号");
        cmp("sqrt4", MathEngine.DEG, TWO, "函数不带括号");
        cmp("2pi/2", MathEngine.DEG, PI, "2π÷2");
        cmp("pi^2", MathEngine.DEG, PI.multiply(PI, C60).round(C60), "π^2");
        cmp("2!3", MathEngine.DEG, bd("6"), "2!×3");
    }

    // ==================================================================
    // 9. 错误处理
    // ==================================================================

    static void sec9() {
        header("9. 错误处理（必须是干净的 MathException + 可读中文消息）");
        errCase("1/0", MathEngine.DEG, "除零");
        errCase("1÷0", MathEngine.DEG, "除零（Unicode ÷）");
        errCase("tan(90)", MathEngine.DEG, "角度制下无定义");
        errCase("tan(270)", MathEngine.DEG, "角度制下无定义");
        errCase("tan(pi÷2)", MathEngine.RAD, "弧度制下无定义");
        errCase("sqrt(-4)", MathEngine.DEG, "负数开平方");
        errCase("ln(0)", MathEngine.DEG, "对数取 0");
        errCase("ln(-1)", MathEngine.DEG, "对数取负数");
        errCase("log(1,5)", MathEngine.DEG, "底数为 1");
        errCase("log(0,5)", MathEngine.DEG, "底数为 0");
        errCase("(((", MathEngine.DEG, "只有左括号");
        errCase("1+)", MathEngine.DEG, "右括号多余");
        errCase("", MathEngine.DEG, "空表达式");
        errCase("0.5!", MathEngine.DEG, "小数阶乘");
        errCase("(-1)!", MathEngine.DEG, "负数阶乘");
        errCase("1..2", MathEngine.DEG, "重复小数点");
        errCase("sin()", MathEngine.DEG, "函数无参数");
        errCase("log()", MathEngine.DEG, "函数无参数");
        errCase("sqrt()", MathEngine.DEG, "函数无参数");
        errCase("root(2.5,4)", MathEngine.DEG, "根指数非整数");
        errCase("root(0,4)", MathEngine.DEG, "根指数为 0");
        errCase("sqrt(-4)+1", MathEngine.DEG, "复合表达式里的错误");
        errCase("asin(1.0000001)", MathEngine.DEG, "超出 asin 定义域");
        errCase("2^^3", MathEngine.DEG, "连续乘方");
        errCase("1,2", MathEngine.DEG, "裸逗号");
        errCase("sin(1,2)", MathEngine.DEG, "参数个数不对");
        errCase("√-4", MathEngine.DEG, "Unicode 根号 + 负数");
        errCase("(1+2", MathEngine.DEG, "括号没闭合");
        errCase("￥5", MathEngine.DEG, "无法识别的符号");

        header("9b. 不是「报错」但语义需要确认的输入");
        probe("5!", MathEngine.DEG, "正常结果");
        probe("1e999999", MathEngine.DEG, "被解释成 1×e×999999 还是 10^999999？");
        probe("1e5", MathEngine.DEG, "被解释成 1×e×5");
        probe("2E3", MathEngine.DEG, "大写 E");
        probe("1.5e3", MathEngine.DEG, "小数 + e");
        probe("log(2)", MathEngine.DEG, "log 单参数被当成 lg");
        probe("exp(1)", MathEngine.DEG, "文档里列出的 exp 函数");
        probe("deg(30)", MathEngine.DEG, "文档里列出的 deg 函数");
        probe("rad(30)", MathEngine.DEG, "文档里列出的 rad 函数");
        probe("sqr(5)", MathEngine.DEG, "sqr 函数");
        probe("pow(2,10)", MathEngine.DEG, "pow 函数");
        probe("cbrt(-27)", MathEngine.DEG, "cbrt 负参数");
        probe("inv(3)*3", MathEngine.DEG, "倒数往返");
        probe("abs(0-5)", MathEngine.DEG, "绝对值");
    }

    // ==================================================================
    // 10. 性能
    // ==================================================================

    static void sec10() {
        header("10. 性能（单次求值耗时，手表 UI 的卡顿风险）");
        perf("sin(999999999)", MathEngine.DEG);
        perf("sin(999999999)", MathEngine.RAD);
        perf("cos(999999999)", MathEngine.RAD);
        perf("((((1+2)*3)^4)!)", MathEngine.DEG);
        perf("((1+2)*3)^4", MathEngine.DEG);
        perf("1000!", MathEngine.DEG);
        perf("2000!", MathEngine.DEG);
        perf("2001!", MathEngine.DEG);
        perf("2^100000", MathEngine.DEG);
        perf("9^999999999", MathEngine.DEG);
        perf("asin(0.999999)", MathEngine.DEG);
        perf("atan(1000)", MathEngine.DEG);
        perf("tan(89.9999999)", MathEngine.DEG);
        perf("ln(10^100000)", MathEngine.DEG);
        perf("ln(2^100000)", MathEngine.DEG);
        perf("lg(10^100000)", MathEngine.DEG);
        perf("sqrt(2)", MathEngine.DEG);
        perf("root(999,2)", MathEngine.DEG);
    }

    static void perf(String expr, int mode) {
        Box b = runTimed(expr, mode);
        if (b.timeout) {
            emit(expr, ml(mode), "<超时>", "FAIL", "超过 " + TIME_BUDGET_MS + "ms 未返回 —— 手表界面会卡死");
            return;
        }
        if (b.fatal != null) {
            emit(expr, ml(mode), b.fatal.getClass().getSimpleName(), "FAIL", "抛出了 " + b.fatal);
            return;
        }
        String out;
        String note = "";
        if (b.r.raw != null) {
            out = b.r.raw.getClass().getSimpleName();
            note = "原生异常 " + b.r.raw + "；";
        } else if (b.r.errMsg != null) {
            out = "MathException(" + b.r.errMsg + ")";
        } else {
            out = b.r.display;
        }
        long best = b.r.calcNanos;
        for (int i = 0; i < 4; i++) {
            R r = exec(expr, mode);
            if (r.calcNanos < best) best = r.calcNanos;
        }
        String st = "PASS";
        if (best > 2_000_000_000L) st = "FAIL";
        else if (best > 300_000_000L) st = "SUSPECT";
        emit(expr, ml(mode), out, st, note + "首次 " + ms(b.r.calcNanos) + "ms，最快 " + ms(best) + "ms");
    }

    // ==================================================================
    // 11. 其它探针
    // ==================================================================

    static void sec11() {
        header("11. 其它数值探针");
        cmp("0.1+0.2", MathEngine.DEG, bd("0.3"), "十进制精确性（double 会给出 0.30000000000000004）");
        cmp("1÷3*3", MathEngine.DEG, ONE, "截断后乘回来");
        cmp("2÷3+1÷3", MathEngine.DEG, ONE, "");
        cmp("sqrt(2)^2", MathEngine.DEG, TWO, "开方平方往返");
        cmp("sqrt(2)*sqrt(2)", MathEngine.DEG, TWO, "");
        cmp("cbrt(2)^3", MathEngine.DEG, TWO, "立方根往返");
        cmp("root(5,32)", MathEngine.DEG, TWO, "5 次根");
        cmp("root(3,-27)", MathEngine.DEG, bd("-3"), "负参数奇次根");
        cmp("root(4,16)", MathEngine.DEG, TWO, "偶次根");
        cmp("root(1,7)", MathEngine.DEG, bd("7"), "1 次根原样返回");
        cmp("sinh(1)", MathEngine.DEG, refSinh(ONE), "双曲正弦");
        cmp("cosh(1)", MathEngine.DEG, refCosh(ONE), "双曲余弦");
        cmp("tanh(1)", MathEngine.DEG, refSinh(ONE).divide(refCosh(ONE), C60), "双曲正切");
        cmp("tanh(0)", MathEngine.DEG, ZERO, "");
        cmp("sinh(0)", MathEngine.DEG, ZERO, "");
        cmp("cosh(0)", MathEngine.DEG, ONE, "");
        cmp("cosh(1)^2-sinh(1)^2", MathEngine.DEG, ONE, "双曲勾股恒等式");
        cmp("tanh(20)", MathEngine.DEG, refSinh(bd("20")).divide(refCosh(bd("20")), C60), "双曲正切大参数");
        cmp("ln(2)", MathEngine.DEG, refLn(TWO), "自然对数");
        cmp("ln(2)+ln(3)", MathEngine.DEG, refLn(bd("6")), "ln 加法恒等式");
        cmp("lg(2)", MathEngine.DEG, refLn(TWO).divide(refLn(bd("10")), C60), "常用对数");
        cmp("ln(e)", MathEngine.DEG, ONE, "ln(e)=1");
        cmp("log(2,1024)", MathEngine.DEG, bd("10"), "log(b,b^k)=k");
        cmp("log(3,3^7)", MathEngine.DEG, bd("7"), "log(b,b^k)=k");
        cmp("log(0.5,8)", MathEngine.DEG, bd("-3"), "底数 < 1");
        cmp("ln(1.0000001)", MathEngine.DEG, refLn(bd("1.0000001")), "接近 1 的对数");
        cmp("ln(10^300)", MathEngine.DEG, refLn(new BigDecimal(BigInteger.TEN.pow(300))), "大数对数");
        cmp("lg(10^300)", MathEngine.DEG, bd("300"), "大数常用对数");
        cmp("ln(10^-300)", MathEngine.DEG, refLn(ONE.divide(new BigDecimal(BigInteger.TEN.pow(300)), C60)), "小数对数");
        cmp("tan(89.9999999)", MathEngine.DEG, refTan(refRadFromDeg(bd("89.9999999"))), "接近奇点");
        cmp("tan(0.0000001)", MathEngine.DEG, refTan(refRadFromDeg(bd("0.0000001"))), "接近 0");
        cmp("sin(0.0000001)", MathEngine.DEG, refSin(refRadFromDeg(bd("0.0000001"))), "小角度");
        cmp("asin(0.0000001)", MathEngine.DEG, refDeg(refAsin(bd("0.0000001"))), "小参数反三角");
        cmp("lg(1024)*ln(10)", MathEngine.DEG, refLn(bd("1024")), "换底恒等式");
        cmp("1÷7*7", MathEngine.DEG, ONE, "");
        cmp("2^1023", MathEngine.DEG, new BigDecimal(BigInteger.TWO.pow(1023)), "接近 double 上限");
        cmp("1-1", MathEngine.DEG, ZERO, "精确 0");
        cmp("0*1e308", MathEngine.DEG, ZERO, "0 乘大数");
    }

    // ==================================================================
    // 12. 格式化
    // ==================================================================

    static void sec12() {
        header("12. format() 与独立格式规则对照");
        fmtCmp("1÷3", MathEngine.DEG);
        fmtCmp("2÷3", MathEngine.DEG);
        fmtCmp("1÷7", MathEngine.DEG);
        fmtCmp("100!", MathEngine.DEG);
        fmtCmp("1000!", MathEngine.DEG);
        fmtCmp("2^-1074", MathEngine.DEG);
        fmtCmp("10^-308", MathEngine.DEG);
        fmtCmp("10^308", MathEngine.DEG);
        fmtCmp("1234567890123456789+0", MathEngine.DEG);
        fmtCmp("3^39", MathEngine.DEG);
        fmtCmp("9^20", MathEngine.DEG);
        fmtCmp("9876543210987654321*3", MathEngine.DEG);
        fmtCmp("123456789012345678.9+0", MathEngine.DEG);
        fmtCmp("123456789012345678*1+0.5", MathEngine.DEG);
        fmtCmp("99999999999999999999+0", MathEngine.DEG);
        fmtCmp("0.0000012345678+0", MathEngine.DEG);
        fmtCmp("sin(30)", MathEngine.DEG);
        fmtCmp("pi", MathEngine.DEG);
        fmtCmp("e", MathEngine.DEG);
        fmtCmp("sqrt(2)", MathEngine.DEG);
        fmtCmp("0.1+0.2", MathEngine.DEG);
        fmtCmp("1-1", MathEngine.DEG);

        System.out.println("  ── 直接给 format() 喂字面值（绕开计算，只测格式化）");
        String[] literals = {
                "1234567890123456789", "4052555153018976267", "9876543210987654321",
                "123456789012345678.9", "123456789012345678.5", "123456789012345678.4",
                "12345678901234567.89", "999999999999999999", "9999999999999999999",
                "99999999999999999999", "0.0000012345678", "0.0000001", "0.00000001",
                "1.0000000049999", "1.00000000499999", "1.000000005", "123.456789012345",
                "2.5", "1E-7", "1E-8", "9.999999999E-7", "1.7976931348623157E+308",
                "1E+1000", "12345678901234567890", "-1234567890123456789", "0.999999999",
                "1234567.89123456789", "-0.000000123456789", "4.023872601E+2567"
        };
        for (String lit : literals) fmtCmpValue(lit);
    }

    // ==================================================================
    // 通用断言 / 报告工具
    // ==================================================================

    static final class R {
        String display;
        String errMsg;
        Throwable raw;
        BigDecimal value;
        String evalErrMsg;
        Throwable evalRaw;
        long calcNanos;
    }

    static final class Box {
        R r;
        Throwable fatal;
        boolean timeout;
    }

    /** 直接调用引擎（calculate + evaluate），记录耗时与异常。 */
    static R exec(String expr, int mode) {
        R r = new R();
        long t0 = System.nanoTime();
        try {
            r.display = MathEngine.calculate(expr, mode);
        } catch (MathEngine.MathException e) {
            r.errMsg = e.getMessage();
        } catch (Throwable t) {
            r.raw = t;
        }
        r.calcNanos = System.nanoTime() - t0;
        try {
            r.value = MathEngine.evaluate(expr, mode);
        } catch (MathEngine.MathException e) {
            r.evalErrMsg = e.getMessage();
        } catch (Throwable t) {
            r.evalRaw = t;
        }
        return r;
    }

    /** 放到独立线程里跑，超时就判定为死循环（避免整个验证程序被卡住）。 */
    static Box runTimed(final String expr, final int mode) {
        final Box b = new Box();
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    b.r = exec(expr, mode);
                } catch (Throwable x) {
                    b.fatal = x;
                }
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            t.join(TIME_BUDGET_MS);
        } catch (InterruptedException ignored) {
        }
        if (t.isAlive()) {
            b.timeout = true;
            t.interrupt();
        }
        return b;
    }

    /** 与独立参考值比较（相对/绝对误差按 max(|ref|,1) 归一）。 */
    static void cmp(String expr, int mode, BigDecimal ref, String note) {
        if (ref == null) {
            emit(expr, ml(mode), "-", "SUSPECT", "参考值不可用" + tail(note));
            return;
        }
        Box b = runTimed(expr, mode);
        String m = ml(mode);
        if (b.timeout) {
            emit(expr, m, "<超时>", "FAIL", "超过 " + TIME_BUDGET_MS + "ms 未返回" + tail(note));
            return;
        }
        if (b.fatal != null) {
            emit(expr, m, b.fatal.getClass().getSimpleName(), "FAIL", "求值线程抛出 " + b.fatal + tail(note));
            return;
        }
        R r = b.r;
        if (r.raw != null) {
            emit(expr, m, r.raw.getClass().getName(), "FAIL", "非 MathException 的原生异常：" + r.raw + tail(note));
            return;
        }
        if (r.errMsg != null) {
            emit(expr, m, "MathException(" + r.errMsg + ")", "FAIL", "本应有数值 " + brief(ref) + tail(note));
            return;
        }
        if (r.value == null) {
            emit(expr, m, r.display, "FAIL", "evaluate 返回 null" + tail(note));
            return;
        }
        BigDecimal err = r.value.subtract(ref).abs();
        BigDecimal denom = ref.abs().compareTo(ONE) > 0 ? ref.abs() : ONE;
        BigDecimal norm = err.divide(denom, C60);
        String st = norm.compareTo(new BigDecimal("1e-25")) <= 0 ? "PASS"
                : norm.compareTo(new BigDecimal("1e-12")) <= 0 ? "SUSPECT" : "FAIL";
        emit(expr, m, r.display, st, "参考=" + brief(ref) + " 误差=" + brief(err) + " 归一=" + brief(norm) + tail(note));
    }

    /** 期望某个数值（数学上成立但引擎可能直接报错的情形）。 */
    static void expectValue(String expr, int mode, BigDecimal ref, String why) {
        Box b = runTimed(expr, mode);
        String m = ml(mode);
        if (b.timeout) {
            emit(expr, m, "<超时>", "FAIL", "超过 " + TIME_BUDGET_MS + "ms 未返回");
            return;
        }
        if (b.fatal != null) {
            emit(expr, m, b.fatal.getClass().getSimpleName(), "FAIL", "求值线程抛出 " + b.fatal);
            return;
        }
        R r = b.r;
        if (r.raw != null) {
            emit(expr, m, r.raw.getClass().getName(), "FAIL", "原生异常：" + r.raw);
            return;
        }
        if (r.errMsg != null) {
            emit(expr, m, "MathException(" + r.errMsg + ")", "SUSPECT", "数学上是 " + ref.toPlainString() + "（" + why + "）");
            return;
        }
        cmp(expr, mode, ref, why);
    }

    /** 只报告结果，不做对错判定（看语义是否合理）。 */
    static void probe(String expr, int mode, String why) {
        Box b = runTimed(expr, mode);
        String m = ml(mode);
        if (b.timeout) {
            emit(expr, m, "<超时>", "FAIL", "超过 " + TIME_BUDGET_MS + "ms 未返回；" + why);
            return;
        }
        if (b.fatal != null) {
            emit(expr, m, b.fatal.getClass().getSimpleName(), "FAIL", "求值线程抛出 " + b.fatal + "；" + why);
            return;
        }
        R r = b.r;
        if (r.raw != null) {
            emit(expr, m, r.raw.getClass().getName(), "FAIL", "原生异常：" + r.raw + "；" + why);
            return;
        }
        if (r.errMsg != null) {
            emit(expr, m, "MathException(" + r.errMsg + ")", "SUSPECT", why);
            return;
        }
        String value = r.value == null ? "null" : brief(r.value);
        emit(expr, m, r.display, "SUSPECT", why + " → 数值 " + value + "，耗时 " + ms(r.calcNanos) + "ms");
    }

    /** 必须抛 MathException。 */
    static void errCase(String expr, int mode, String why) {
        Box b = runTimed(expr, mode);
        String m = ml(mode);
        if (b.timeout) {
            emit(expr, m, "<超时>", "FAIL", "应报错却卡住（" + why + "）");
            return;
        }
        if (b.fatal != null) {
            emit(expr, m, b.fatal.getClass().getSimpleName(), "FAIL", "应报 MathException，实际抛出 " + b.fatal + "（" + why + "）");
            return;
        }
        R r = b.r;
        if (r.raw != null) {
            emit(expr, m, r.raw.getClass().getName(), "FAIL", "应报 MathException，实际是原生异常：" + r.raw + "（" + why + "）");
            return;
        }
        if (r.errMsg == null) {
            emit(expr, m, r.display, "FAIL", "本该报错（" + why + "），却算出 " + r.display);
            return;
        }
        String st = (r.errMsg != null && r.errMsg.length() > 0) ? "PASS" : "SUSPECT";
        emit(expr, m, "MathException: " + r.errMsg, st, why);
    }

    static void fmtCmp(String expr, int mode) {
        R r = exec(expr, mode);
        if (r.raw != null) {
            emit(expr, ml(mode), r.raw.getClass().getSimpleName(), "FAIL", "格式化检查时求值崩溃");
            return;
        }
        if (r.errMsg != null) {
            emit(expr, ml(mode), "MathException(" + r.errMsg + ")", "SUSPECT", "无法求值，跳过格式化检查");
            return;
        }
        String engine = MathEngine.format(r.value);
        String mine = refFormat(r.value);
        if (engine.equals(mine)) emit(expr, ml(mode), engine, "PASS", "与独立格式规则一致（数值 " + brief(r.value) + "）");
        else emit(expr, ml(mode), engine, "FAIL", "独立规则应为 " + mine + "（数值 " + brief(r.value) + "）");
    }

    static void fmtCmpValue(String literal) {
        BigDecimal v;
        try {
            v = new BigDecimal(literal);
        } catch (NumberFormatException e) {
            return;
        }
        String engine = MathEngine.format(v);
        String mine = refFormat(v);
        if (engine.equals(mine)) emit("format(" + literal + ")", "-", engine, "PASS", "");
        else emit("format(" + literal + ")", "-", engine, "FAIL", "独立规则应为 " + mine);
    }

    static void emit(String expr, String mode, String out, String status, String note) {
        ROWS.add(new String[]{expr, mode, out, status, note});
        if ("PASS".equals(status)) nPass++;
        else if ("FAIL".equals(status)) nFail++;
        else nSuspect++;
        System.out.println(pad(expr, 36) + " | " + pad(mode, 3) + " | " + pad(out, 30) + " | "
                + pad(status, 7) + " | " + note);
    }

    static void header(String s) {
        System.out.println();
        System.out.println("┌────────────────────────────────────────────────────────────────────────────");
        System.out.println("│ " + s);
        System.out.println("└────────────────────────────────────────────────────────────────────────────");
    }

    static void summary(long elapsedMs) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════════");
        System.out.println("汇总：PASS " + nPass + "，SUSPECT " + nSuspect + "，FAIL " + nFail
                + "，总耗时 " + elapsedMs + "ms");
        System.out.println("回读一致性：一致 " + rtPass + " / 截断型超差 " + rtTruncSuspect
                + " / 科学计数法回读错误 " + rtSciBad + " / 其它失败 " + rtFail + " / 跳过 " + rtSkipped);
        System.out.println("──────── 非 PASS 明细 ────────");
        for (String[] row : ROWS) {
            if (!"PASS".equals(row[3])) {
                System.out.println("  [" + row[3] + "] " + row[0] + "  (" + row[1] + ")  = " + row[2] + "   :: " + row[4]);
            }
        }
        System.out.println("════════════════════════════════════════════════════════════════════════════");
    }

    static String ml(int mode) {
        return mode == MathEngine.DEG ? "DEG" : "RAD";
    }

    static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    static String brief(BigDecimal v) {
        if (v == null) return "null";
        if (v.signum() == 0) return "0";
        return v.round(new MathContext(5, RoundingMode.HALF_UP)).toString();
    }

    static String tail(String note) {
        return (note == null || note.isEmpty()) ? "" : " [" + note + "]";
    }

    static String pad(String s, int w) {
        if (s == null) s = "";
        if (s.length() > w) return s.substring(0, Math.max(0, w - 1)) + "…";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < w) sb.append(' ');
        return sb.toString();
    }
}
