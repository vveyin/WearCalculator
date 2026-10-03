package com.nickwoluff.wearcalculator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 嗷呜计算器 —— 高精度科学计算引擎。
 *
 * <p>在原本只有 + - × ÷ 的 BigDecimal 递归下降解析器基础上重写，新增：
 * <ul>
 *     <li>括号 ( )，常量 pi / e</li>
 *     <li>幂运算 ^、阶乘 !、取模 %、百分号 %（结尾时）</li>
 *     <li>三角函数 sin / cos / tan（支持角度制 DEG 与弧度制 RAD 两种输入）</li>
 *     <li>对数 ln / lg / log(底数, 真数)，开方 sqrt / root(n,x)，倒数 inv，绝对值 abs</li>
 *     <li>隐式乘法：2pi、3(4+5)、2sin(30) 都会被正确理解为乘法</li>
 * </ul>
 *
 * <p>所有实数运算均在 {@link BigDecimal} 上以 45 位有效数字完成，圆周率由 Machin 公式
 * 现场计算出 84 位精度（给级数预算留足余量），三角函数、对数、开方全部使用泰勒级数 /
 * 牛顿迭代实现，不使用 double，避免二进制浮点误差。
 */
public final class MathEngine {

    private MathEngine() {
    }

    /** 角度单位。 */
    public static final int DEG = 0;
    public static final int RAD = 1;

    /**
     * 内部计算精度：30 位有效数字。
     *
     * <p>这个值是刻意压过的：原来用 45 位，但屏幕最多显示 8~10 位小数，
     * 45 位纯属浪费 —— 每多一位，BigDecimal 的乘除和级数迭代都要多算一次，
     * 而手表的 CPU 很慢。压到 30 位后：
     * <ul>
     *     <li>屏幕精度完全不受影响（仍然远超浮点的 15~17 位）；</li>
     *     <li>sin/cos/ln/开方这些级数迭代的位数代价直接降三分之一左右；</li>
     *     <li>π 仍然用 84 位现算，比内部精度高得多，规约不会掉精度。</li>
     * </ul>
     */
    private static final int PRECISION = 30;
    /** 输出的最大小数位（超出部分用 ... 提示）。 */
    private static final int OUTPUT_SCALE = 8;

    private static final MathContext MC = new MathContext(PRECISION, RoundingMode.HALF_UP);

    /** 阶乘上限，防止用户输入 100000! 卡死界面。 */
    private static final int MAX_FACTORIAL = 2000;

    private static final BigDecimal TWO = new BigDecimal("2");

    /** 计算过程中使用的 π：84 位有效数字（Machin 公式）。 */
    private static final BigDecimal PI = machin(84);

    /** 2π，三角函数规约用。 */
    private static final BigDecimal TWO_PI = TWO.multiply(PI, MC);

    /** 专门给「大角度规约」用的 π，位数更高，避免 x mod 2π 相减时掉有效数字。 */
    private static final BigDecimal PI_REDUCE = machin(95);

    /** 2π，大角度规约用。 */
    private static final BigDecimal TWO_PI_REDUCE = PI_REDUCE.multiply(TWO);

    /**
     * 级数求和的下限。低于这个数量级的项已经没有意义（内部只有 45 位精度），
     * 继续累加只会把舍入噪声算进去。
     */
    private static final BigDecimal SERIES_LIMIT = BigDecimal.ONE.scaleByPowerOfTen(-(PRECISION - 3));

    /** 用来判断「结果是不是该吸附到 0 / ±0.5 / ±1」的阈值。 */
    private static final BigDecimal SNAP_TOLERANCE = BigDecimal.ONE.scaleByPowerOfTen(-20);

    /** 计算过程中使用的 e = sum(1/k!)：80 位有效数字。 */
    private static final BigDecimal E = computeE(80);

    /** 上一题的答案，由界面在每次求值后写入，供 PI 常量之外的 ANS 使用。 */
    private static BigDecimal lastAnswer = BigDecimal.ZERO;

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /**
     * 计算一条表达式。
     *
     * @param expression 界面上的原始表达式，可包含 × ÷ － π 等 Unicode 符号
     * @param angleMode  {@link #DEG} 或 {@link #RAD}
     * @return 格式良好的结果字符串
     * @throws MathException 表达式非法或结果无意义（除零、负数开偶次方等）
     */
    public static String calculate(String expression, int angleMode) throws MathException {
        BigDecimal value = evaluate(expression, angleMode);
        lastAnswer = value;
        return format(value);
    }

    /** 只要数值结果，不要格式化。 */
    public static BigDecimal evaluate(String expression, int angleMode) throws MathException {
        String normalized = normalize(expression);
        // 用户按了 sin( 之后没按右括号就点 = 是很正常的操作，
        // 这里按算式结束的位置把没闭合的左括号补上，再顺手补掉悬空的运算符。
        String balanced = autoClose(normalized);
        List<Token> tokens = new Tokenizer(balanced).tokenize();
        BigDecimal value = new Parser(tokens, balanced, angleMode).parse();
        // 只收拾「小数位多得没意义」的情况。
        // 千万不能只看 scale：10^-54 的 scale 是 54，一旦按 scale 收缩就会被抹成 0
        // （曾经 setScale(PRECISION+8) 无条件执行，于是 10^-54、2^-1074 全变成 0）。
        // 判据改成：整数部分有实际数字，而且总位数远超计算精度，才裁掉多余的尾巴。
        int integerDigits = value.precision() - value.scale();
        if (integerDigits > 0 && value.scale() > PRECISION + 8) {
            value = value.setScale(PRECISION + 8, RoundingMode.HALF_UP);
        }
        return value.stripTrailingZeros();
    }

    /**
     * 把没写完的算式补完整。
     *
     * <p>按了 {@code sin(} 之后懒得按右括号是常态，所以这里把没闭合的左括号补上。
     * 关键顺序：<b>从最里面那层往外补</b>（拿栈记住每个左括号的位置再倒着弹），
     * 否则 {@code sin(30+cos(60} 会被补成 {@code sin(30+cos(60))} 之外的怪东西。
     * 每补一个右括号前，如果末尾还是运算符或 {@code (}，先补一个 0，
     * 免得出现 {@code 2×(3+)} 这种悬空运算符。
     * 只会往末尾补，不会改动用户已经输入的内容。
     */
    private static String autoClose(String expression) {
        StringBuilder sb = new StringBuilder(expression.trim());
        if (sb.length() == 0) return sb.toString();

        Deque<Integer> openStack = new ArrayDeque<>();
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '(') openStack.push(i);
            else if (c == ')' && !openStack.isEmpty()) openStack.pop();
        }
        // 栈顶是最后一个（最内层）没闭合的左括号，依次往外关
        while (!openStack.isEmpty()) {
            openStack.pop();
            if (!endsWithValue(sb)) sb.append('0');
            sb.append(')');
        }
        return sb.toString();
    }

    /** 算式末尾是不是一个「值」（数字/右括号/常量……），不是的话说明需要补 0。 */
    private static boolean endsWithValue(StringBuilder sb) {
        if (sb.length() == 0) return false;
        char last = sb.charAt(sb.length() - 1);
        if (last == ')' || last == '!' || last == '%') return true;
        if (last >= '0' && last <= '9') return true;
        if (last == 'e' || last == '#') return true; // 常量 e / π
        return false;
    }


    // ------------------------------------------------------------------
    // 文本正规化
    // ------------------------------------------------------------------

    /**
     * 把界面显示用的表达式转成词法分析器认识的纯 ASCII 形式。
     * {@code π -> #}、{@code e -> $}、{@code × -> *}、{@code ÷ -> /}。
     */
    public static String normalize(String src) {
        if (src == null) return "";
        StringBuilder sb = new StringBuilder(src.length());
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            switch (c) {
                case 'π':
                case 'Π':
                    sb.append('#');
                    break;
                case 'e':
                case 'E':
                    // e 有两种含义，必须在这里分清楚：
                    //   「2e」/「e^2」里的 e 是自然常数，转成 $；
                    //   「1e3」/「4.02e2567」里的 e 是科学计数法，必须原样保留；
                    //   「exp(」里的 e 是函数名的一部分，同样必须原样保留
                    //   （曾经一律转成 $，导致 exp() 被切成 "xp"，函数根本调不到）。
                    if (isExponentMarker(src, i) || isFunctionNameLetter(src, i)) sb.append('e');
                    else sb.append('$');
                    break;
                case '×':
                case '✕':
                case '·':
                case '∙':
                    sb.append('*');
                    break;
                case '÷':
                case '∕':
                    sb.append('/');
                    break;
                case '−':
                case '–':
                case '—':
                case '－':
                    sb.append('-');
                    break;
                case '＋':
                    sb.append('+');
                    break;
                case '（':
                    sb.append('(');
                    break;
                case '）':
                    sb.append(')');
                    break;
                case '＾':
                    sb.append('^');
                    break;
                case '！':
                    sb.append('!');
                    break;
                case '％':
                    sb.append('%');
                    break;
                case '．':
                    sb.append('.');
                    break;
                case '√':
                    sb.append("sqrt");
                    break;
                default:
                    if (c == '\u00A0' || c == '\u3000') sb.append(' ');
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 判断 src 里第 i 个字符的 e 是不是科学计数法的指数标记。
     * 条件：前面紧挨着数字或小数点，后面紧跟（可选正负号 +）数字。
     * 例如 "1e3"、"4.02e2567"、"3.3e-9" 是，而 "2e"、"e^2"、"2e5e" 里的第一个 e 之后
     * 就要另行判断。
     */
    private static boolean isExponentMarker(String src, int i) {
        if (i <= 0) return false;
        char before = src.charAt(i - 1);
        if (!(before >= '0' && before <= '9') && before != '.') return false;
        // "1exp" 不是科学计数法
        if (isFunctionNameLetter(src, i)) return false;

        int next = i + 1;
        if (next < src.length() && (src.charAt(next) == '+' || src.charAt(next) == '-')) next++;
        if (next >= src.length()) return false;
        char after = src.charAt(next);
        return after >= '0' && after <= '9';
    }

    /**
     * 这个 e 是不是某个函数名的一部分（exp、deg 都带 e）。
     * 这类 e 必须保留原样，否则函数名会被切成 "xp"、"d$g" 而认不出来。
     * 判断方式：以这个 e 为中心，往左右各扩到字母边界，看整段是不是已知函数名。
     */
    private static boolean isFunctionNameLetter(String src, int i) {
        int start = i;
        while (start > 0 && isAsciiLetter(src.charAt(start - 1))) start--;
        int end = i + 1;
        while (end < src.length() && isAsciiLetter(src.charAt(end))) end++;
        String word = src.substring(start, end).toLowerCase();
        switch (word) {
            case "exp":
            case "deg":
            case "expm":
                return true;
            default:
                return false;
        }
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // ------------------------------------------------------------------
    // 结果格式化
    // ------------------------------------------------------------------

    /** 把计算结果排成屏幕上好看的样子。 */
    public static String format(BigDecimal value) {
        if (value == null) return "0";
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0";

        BigDecimal stripped = tidy(value);
        int integerDigits = stripped.precision() - stripped.scale();

        // 极小值：非 0 但按 8 位小数截断会被抹成 0 的（|v| < 1e-8），改用科学计数法。
        // 判据必须直接比数值大小，不能拿 precision-scale 去凑 —— 那样会把 0.333… 也误判。
        // 门槛取 1e-8 而不是 5e-9：3.4e-9 按 8 位小数确实是 0.00000000，得走科学计数法。
        BigDecimal roundingGuard = BigDecimal.ONE.scaleByPowerOfTen(-OUTPUT_SCALE);
        if (stripped.abs().compareTo(roundingGuard) < 0) {
            BigDecimal mantissa = stripped.round(new MathContext(10, RoundingMode.HALF_UP));
            int exponent = mantissa.precision() - mantissa.scale() - 1;
            mantissa = mantissa.movePointLeft(exponent).stripTrailingZeros();
            return mantissa.toPlainString() + "e" + exponent;
        }

        // 天文数字：同样改用科学计数法，否则屏幕会被数字撑爆。
        // 20! 这种 19 位的整数还是希望原样显示，所以门槛放到 19 位。
        if (integerDigits > 19) {
            BigDecimal mantissa = stripped.round(new MathContext(10, RoundingMode.HALF_UP));
            int exponent = mantissa.precision() - mantissa.scale() - 1;
            mantissa = mantissa.movePointLeft(exponent).stripTrailingZeros();
            return mantissa.toPlainString() + "e" + exponent;
        }

        // 小数位太多就截断到 8 位。只有「截断丢掉的东西是真实精度」时才加 ... 提示；
        // 像 2.99999…（其实是 3）这种被截成 3.00000000 的情况不该出现省略号。
        BigDecimal scaled = stripped;
        if (scaled.scale() > OUTPUT_SCALE) {
            BigDecimal cut = scaled.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
            if (isCleanAt(scaled, cut)) return cut.stripTrailingZeros().toPlainString();
            return cut.stripTrailingZeros().toPlainString() + "...";
        }
        return scaled.stripTrailingZeros().toPlainString();
    }

    /** 8 位小数的截断是否已经足够精确（差别小于相对 1e-9 就算够）。 */
    private static boolean isCleanAt(BigDecimal original, BigDecimal cut) {
        BigDecimal error = cut.subtract(original, MC).abs();
        BigDecimal tolerance = original.abs().multiply(new BigDecimal("1e-9"), MC);
        return error.compareTo(tolerance) <= 0;
    }

    /**
     * 把「差最后几位」的结果拉回干净值。
     *
     * <p>ln(e) 用 45 位精度算出来是 0.9999999999…，屏幕上却应该显示 1；
     * sqrt(9)、lg(1000)、asin(0.5) 同理。
     *
     * <p><b>只动小数部分，绝不动整数部分。</b>
     * 曾经这里无脑取 18 位有效数字，于是 3^39 = 4052555153018976267 被改成 …270、
     * format(9999999999999999999) 变成 1e19 —— 都是屏幕可见的错误答案。
     * 现在先按「最多 20 位有效数字、且不超过现有小数位」收一次，只有确实变了
     * 才继续判断；整数位比 20 位还长的值一律原样返回。
     */
    private static BigDecimal tidy(BigDecimal value) {
        if (value.signum() == 0) return value;

        // 极小值不能走下面的 setScale 收尾：3.4e-9 收到 20 位小数会被直接舍成 0，
        // 屏幕上就出现「明明有值却显示 0」。小于 1e-8 的一律原样返回，
        // 交给 format 用科学计数法显示。
        if (value.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-OUTPUT_SCALE)) < 0) {
            return value;
        }

        BigDecimal scaled = value.scale() > 0
                ? value.setScale(Math.min(value.scale(), 20), RoundingMode.HALF_UP)
                : value;
        if (scaled.compareTo(value) == 0) return value;

        // 只有相对误差足够小（1e-13）才认为是「吸附到干净值」，否则保留原值
        BigDecimal error = scaled.subtract(value, MC).abs();
        BigDecimal tolerance = value.abs().multiply(new BigDecimal("1e-13"), MC);
        return error.compareTo(tolerance) <= 0 ? scaled : value;
    }

    // ------------------------------------------------------------------
    // 基本数学函数
    // ------------------------------------------------------------------

    static BigDecimal factorial(BigDecimal x) throws MathException {
        if (x.scale() > 0 && x.stripTrailingZeros().scale() > 0) {
            throw new MathException("阶乘只支持非负整数");
        }
        if (x.signum() < 0) throw new MathException("负数没有阶乘");
        // 必须先比大小再取 int：直接 intValue() 遇到 2^32 会溢出成 1，
        // 于是 4294967296! 会被算成 1!（这个坑很隐蔽）
        if (x.compareTo(BigDecimal.valueOf(MAX_FACTORIAL)) > 0) {
            throw new MathException("阶乘数字太大（上限 " + MAX_FACTORIAL + "）");
        }
        int n = x.intValue();
        BigInteger acc = BigInteger.ONE;
        for (int i = 2; i <= n; i++) acc = acc.multiply(BigInteger.valueOf(i));
        return new BigDecimal(acc);
    }

    static BigDecimal abs(BigDecimal x) {
        return x.abs();
    }

    static BigDecimal percent(BigDecimal x) {
        return x.divide(new BigDecimal("100"), MC);
    }

    static BigDecimal reciprocal(BigDecimal x) throws MathException {
        if (x.signum() == 0) throw new MathException("0 没有倒数");
        return BigDecimal.ONE.divide(x, MC);
    }

    /** 取模：结果的符号跟随被除数，和常见的计算器 fmod 行为一致。 */
    static BigDecimal modulo(BigDecimal a, BigDecimal b) throws MathException {
        if (b.signum() == 0) throw new MathException("取模的除数不能是 0");
        BigDecimal r = a.remainder(b, MC);
        return r.stripTrailingZeros();
    }

    /** 幂运算 a^b，整数指数走精确路线，小数指数走 exp(b·ln a)。 */
    static BigDecimal pow(BigDecimal a, BigDecimal b) throws MathException {
        // 0^0 按约定取 1（0 的正数次方也是 0），这条要在下面所有分支之前判掉
        if (a.signum() == 0) {
            if (b.signum() > 0) return BigDecimal.ZERO;
            if (b.signum() == 0) return BigDecimal.ONE;
            throw new MathException("0 的负数次方无意义");
        }
        if (b.signum() == 0) return BigDecimal.ONE;

        BigDecimal exact = exactInteger(b);
        if (exact != null) {
            // 1^x、(-1)^x、0^x 的结果要么是 1 要么是 ±1，跟指数多大无关，
            // 必须放在指数上限前面判，否则 1^999999999 会被误报成「指数太大」
            if (a.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ONE;
            if (a.compareTo(BigDecimal.ONE.negate()) == 0) {
                // 奇数指数给 -1，偶数给 1
                boolean odd = exact.remainder(new BigDecimal("2")).signum() != 0;
                return odd ? BigDecimal.ONE.negate() : BigDecimal.ONE;
            }

            // 其余情况：先估算结果位数，太大就直接拒绝，省得算到一半撑爆内存
            // （必须先比大小，不能直接 intValue()：2^31 会溢出成负数）
            double baseMagnitude = Math.abs(a.doubleValue());
            double digits = Math.abs(exact.doubleValue()) * Math.log10(Math.max(baseMagnitude, 1e-300));
            if (!Double.isFinite(digits) || digits > MAX_RESULT_DIGITS) {
                throw new MathException("结果太大，算不出来");
            }

            int n = exact.intValue();
            if (n >= 0) return a.pow(n, MC);
            return BigDecimal.ONE.divide(a.pow(-n, MC), MC);
        }

        if (a.signum() < 0) {
            BigDecimal integerRoot = exactRoot(a, b);
            if (integerRoot != null) return integerRoot;
            throw new MathException("负数的非整数次方不在实数范围内");
        }
        if (a.signum() == 0) {
            if (b.signum() == 0) return BigDecimal.ONE;
            throw new MathException("0 的负数次方无意义");
        }
        // exp(b·ln a) 里指数会被拆成整数部分与小数部分，整数部分太大同样没法算
        BigDecimal exponent = b.multiply(ln(a), MC);
        if (!Double.isFinite(exponent.doubleValue())
                || Math.abs(exponent.doubleValue()) > MAX_RESULT_DIGITS / 0.4343) {
            throw new MathException("结果太大，算不出来");
        }
        return exp(exponent);
    }

    /** 允许的结果规模（按十进制位数估），大约 1e6 位，够用又不会把内存吃光。 */
    private static final double MAX_RESULT_DIGITS = 1000000;

    static BigDecimal sqrt(BigDecimal x) throws MathException {
        int scale = Math.min(Math.max(x.precision() + 25, 30), 60);
        return sqrt(x, scale);
    }

    /** 牛顿迭代开平方，迭代次数固定，保证不会死循环。 */
    static BigDecimal sqrt(BigDecimal x, int scale) throws MathException {
        if (x.signum() < 0) throw new MathException("负数不能开平方");
        if (x.signum() == 0) return BigDecimal.ZERO;
        if (scale < 10) scale = 10;

        BigDecimal guess = BigDecimal.valueOf(Math.sqrt(x.doubleValue()));
        if (guess.signum() == 0) guess = BigDecimal.ONE;
        guess = guess.setScale(scale + 5, RoundingMode.HALF_UP);

        MathContext ctx = new MathContext(scale + 12, RoundingMode.HALF_UP);
        for (int i = 0; i < 60; i++) {
            BigDecimal next = guess.add(x.divide(guess, ctx), ctx)
                    .divide(TWO, ctx);
            if (next.compareTo(guess) == 0) {
                guess = next;
                break;
            }
            guess = next;
        }
        return guess.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** n 次根号 x，n 必须是正整数。 */
    static BigDecimal root(BigDecimal n, BigDecimal x) throws MathException {
        BigDecimal exactN = exactInteger(n);
        if (exactN == null || exactN.signum() <= 0) throw new MathException("根指数必须是正整数");
        int degree = exactN.intValue();
        if (degree == 1) return x;
        if (degree > 1000) throw new MathException("根指数太大");
        if (x.signum() < 0) {
            if (degree % 2 == 0) throw new MathException("负数不能开偶次方");
            BigDecimal inner = root(BigDecimal.valueOf(degree), x.negate());
            return inner.negate();
        }
        if (x.signum() == 0) return BigDecimal.ZERO;

        BigDecimal seedBase = x;
        if (seedBase.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0) {
            // 超出 double 范围时先用科学计数法定个种子
            int exponent = seedBase.precision() - seedBase.scale() - 1;
            seedBase = seedBase.movePointLeft(exponent);
            BigDecimal seed = BigDecimal.valueOf(Math.pow(seedBase.doubleValue(), 1.0 / degree))
                    .multiply(BigDecimal.TEN.pow(Math.floorDiv(exponent, degree)));
            return refineRoot(seed, x, degree);
        }
        BigDecimal guess = BigDecimal.valueOf(Math.pow(seedBase.doubleValue(), 1.0 / degree));
        return refineRoot(guess, x, degree);
    }

    /** 牛顿迭代求 n 次根，迭代次数固定。 */
    private static BigDecimal refineRoot(BigDecimal seed, BigDecimal x, int degree) throws MathException {
        BigDecimal guess = seed.signum() == 0 ? BigDecimal.ONE : seed;
        MathContext ctx = new MathContext(PRECISION + 12, RoundingMode.HALF_UP);
        BigDecimal bigDegree = BigDecimal.valueOf(degree);
        for (int i = 0; i < 120; i++) {
            BigDecimal next = guess.multiply(BigDecimal.valueOf(degree - 1), ctx)
                    .add(x.divide(guess.pow(degree - 1, ctx), ctx), ctx)
                    .divide(bigDegree, ctx);
            if (next.compareTo(guess) == 0) {
                guess = next;
                break;
            }
            guess = next;
        }
        return guess.round(MC).stripTrailingZeros();
    }

    // ------------------------------------------------------------------
    // 三角函数（全部以弧度实现，角度制在入口换算）
    // ------------------------------------------------------------------

    /** 把界面上的角度按当前单位换算成弧度。 */
    static BigDecimal toRadians(BigDecimal raw, int angleMode) {
        if (angleMode != DEG) return raw;
        return raw.multiply(PI, MC).divide(new BigDecimal("180"), MC);
    }

    /**
     * sin。
     *
     * <p>先把角度规约到 [0, 2π]，再反射到 [0, π/2]，最后才交给级数。
     * 三级反射是必须的：
     * <ul>
     *     <li>x &gt; π 时 sin x = −sin(x−π)（<b>不是</b> sin(π−x)，这里曾经写反过，
     *         导致 π 到 1000π 之间的角度全错，sin(1000) 直接算出 5e421）；</li>
     *     <li>x &gt; π/2 时 sin x = sin(π−x)；</li>
     *     <li>只剩 x &gt; π/4 时再用 sin x = cos(π/2−x)，让级数参数落在 π/4 以内。</li>
     * </ul>
     */
    static BigDecimal sin(BigDecimal radians) {
        BigDecimal x = reduceAngle(radians);
        boolean negative = x.signum() < 0;
        if (negative) x = x.negate();

        BigDecimal halfPi = PI.divide(TWO, MC);
        BigDecimal quarter = PI.divide(new BigDecimal("4"), MC);

        BigDecimal result;
        if (x.compareTo(halfPi) <= 0) {
            result = sinHalf(x);
        } else if (x.compareTo(PI) <= 0) {
            result = sinHalf(PI.subtract(x, MC));
        } else {
            // x ∈ (π, 2π)：sin x = −sin(x−π)，此时 x−π ∈ (0, π)
            result = sinHalf(x.subtract(PI, MC)).negate();
        }
        return snapTrig(negative ? result.negate() : result);
    }

    /** sin 在 [0, π/2] 上的取值，超过 π/4 时换成 cos 分支以加快级数收敛。 */
    private static BigDecimal sinHalf(BigDecimal x) {
        BigDecimal quarter = PI.divide(new BigDecimal("4"), MC);
        if (x.compareTo(quarter) <= 0) return sinSeries(x);
        return cosSeries(PI.divide(TWO, MC).subtract(x, MC));
    }

    /**
     * cos。
     *
     * <p>先把角度规约到 [0, 2π]，再反射到 [0, π]，然后：
     * <ul>
     *     <li>x &gt; 3π/4：cos x = −cos(π−x)；</li>
     *     <li>x &gt; π/4：cos x = sin(π/2−x)；</li>
     *     <li>否则直接展开 cos 级数。</li>
     * </ul>
     * 「正好落在 π 上」要先判出来，否则结果会被吸附成 0 而不是 −1。
     */
    static BigDecimal cos(BigDecimal radians) {
        BigDecimal x = reduceAngle(radians);
        // 先把符号提出来：cos 是偶函数，|x| 的余弦和 x 一样；
        // 取绝对值后再规约一次，保证进入 cosPositive 的一定在 [0, 2π)
        if (x.signum() < 0) x = x.negate();
        if (x.compareTo(TWO_PI) >= 0) x = reduceAngle(x);
        return cosPositive(x);
    }

    /**
     * 只处理 [0, 2π) 的 cos。
     *
     * <p>这里不能再递归回 cos()：递归时传进去的是 x−2π，它又会走进规约、
     * 又变成同一个负数，直接无限递归到 StackOverflowError（曾经真的栈溢出过）。
     * 折回 [0, π] 这一步必须在函数内部一次做完。
     */
    private static BigDecimal cosPositive(BigDecimal x) {
        // 「正好落在 π 上」要单独判，否则结果会被吸附成 0 而不是 −1
        if (x.subtract(PI, MC).abs().compareTo(SNAP_TOLERANCE) < 0) {
            return BigDecimal.ONE.negate();
        }

        BigDecimal halfPi = PI.divide(TWO, MC);
        BigDecimal quarter = PI.divide(new BigDecimal("4"), MC);

        // 用象限写法，别再用一串 else-if 比区间 —— 之前那样写容易漏条件，
        // 结果会悄悄走到错误的分支上（cos(2000) 就是这么错的）。
        // 四个象限（x 为已经归约到 [0,2π) 的正数）：
        //   0: [0, π/2]        cos =  |cos x|
        //   1: (π/2, π]        cos = −|sin(x−π/2)|   ← 这里曾经写成 −cos(π/2−x)，
        //                        那等于 −sin(x)，于是 cos(120°) 算成 −0.866 而不是 −0.5
        //   2: (π, 3π/2]       cos = −|cos x|（x 已折成 x−π）
        //   3: (3π/2, 2π)      cos =  |sin(x−3π/2)|
        int quadrant;
        if (x.compareTo(halfPi) <= 0) {
            quadrant = 0;
        } else if (x.compareTo(PI) <= 0) {
            quadrant = 1;
            x = x.subtract(halfPi, MC);
        } else if (x.compareTo(halfPi.add(PI, MC)) <= 0) {
            quadrant = 2;
            x = x.subtract(PI, MC);
        } else {
            quadrant = 3;
            x = x.subtract(PI.add(halfPi, MC), MC);
        }

        BigDecimal magnitude;
        if (quadrant == 1 || quadrant == 3) {
            // sin 是奇函数，这里 x 一定是正的，方向已经由符号位负责
            magnitude = sinSeries(x);
        } else {
            magnitude = cosSeries(x);
        }
        boolean negative = quadrant == 1 || quadrant == 2;
        return snapTrig(negative ? magnitude.negate() : magnitude);
    }

    /**
     * 把任意弧度值规约到 [0, 2π)。
     *
     * <p>这里不能再有「小于某个值就跳过规约」的短路 —— 之前写成
     * {@code |x| < 1000π 就直接返回}，结果 π 到 1000π 之间的角度全部没规约，
     * 后面的反射公式前提就不成立了，sin(1000) 会算出 5e421。
     * 现在只要 |x| ≥ π 就规约。
     */
    private static BigDecimal reduceAngle(BigDecimal x) {
        if (x.abs().compareTo(PI) < 0) return x;
        BigDecimal[] qr = x.divideAndRemainder(TWO_PI, new MathContext(PRECISION + 25, RoundingMode.HALF_UP));
        return qr[1].round(MC);
    }

    /**
     * 把「差一丁点」的结果吸附到数学上的精确值。
     *
     * <p>sin(180°) 经过 π/180 换算之后，用 45 位精度算出来大约是 -6e-46，
     * 屏幕上却应该老老实实显示 0。这里对所有常见洁癖角度
     * （0、±0.5、±√2/2、±√3/2、±1）做一次吸附，容差 1e-20 ——
     * 远小于屏幕显示到小数点后 8 位的分辨率，不可能把真实结果改错。
     */
    private static BigDecimal snapTrig(BigDecimal value) {
        BigDecimal abs = value.abs();
        if (abs.compareTo(SNAP_TOLERANCE) < 0) return BigDecimal.ZERO;

        BigDecimal[] cardinal = {
                BigDecimal.ONE,
                new BigDecimal("0.5"),
                BigDecimal.ONE.divide(sqrt2(), MC),
                sqrt3().divide(TWO, MC)
        };
        for (BigDecimal target : cardinal) {
            if (abs.subtract(target).abs().compareTo(SNAP_TOLERANCE) < 0) {
                return value.signum() < 0 ? target.negate() : target;
            }
        }
        return value.stripTrailingZeros();
    }

    private static BigDecimal sqrt2Cache;
    private static BigDecimal sqrt3Cache;

    private static BigDecimal sqrt2() {
        if (sqrt2Cache == null) sqrt2Cache = sqrtExact(new BigDecimal("2"));
        return sqrt2Cache;
    }

    private static BigDecimal sqrt3() {
        if (sqrt3Cache == null) sqrt3Cache = sqrtExact(new BigDecimal("3"));
        return sqrt3Cache;
    }

    /** 常量开方不会失败，把受检异常收在这里。 */
    private static BigDecimal sqrtExact(BigDecimal x) {
        try {
            return sqrt(x, PRECISION + 10);
        } catch (MathException e) {
            return BigDecimal.ONE; // 逻辑上不可达
        }
    }

    static BigDecimal tan(BigDecimal radians) throws MathException {
        BigDecimal s = sin(radians);
        BigDecimal c = cos(radians);
        // 角度制下 tan(90)、tan(270) 的 cos 会被吸附成精确的 0，这里直接判定无定义
        if (c.signum() == 0) throw new MathException("该角度 tan 无定义");
        return s.divide(c, MC).stripTrailingZeros();
    }

    static BigDecimal asin(BigDecimal x) throws MathException {
        if (x.abs().compareTo(BigDecimal.ONE) > 0) throw new MathException("asin 的输入必须在 -1 到 1 之间");
        if (x.abs().compareTo(BigDecimal.ONE) == 0) {
            return x.signum() > 0 ? PI.divide(TWO, MC) : PI.divide(TWO, MC).negate();
        }
        if (x.signum() == 0) return BigDecimal.ZERO;
        BigDecimal denominator = sqrt(BigDecimal.ONE.subtract(x.multiply(x, MC), MC), PRECISION + 10);
        return atan(x.divide(denominator, MC));
    }

    static BigDecimal acos(BigDecimal x) throws MathException {
        if (x.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO;
        if (x.compareTo(BigDecimal.ONE.negate()) == 0) return PI;
        return PI.divide(TWO, MC).subtract(asin(x), MC);
    }

    /**
     * arctan。
     *
     * <p>归约路线是一次次踩坑试出来的，写在这里当护栏：
     * <ul>
     *     <li>atan(x) = 2·atan(x/(1+√(1+x²))) 恒成立，但每翻倍一次掉 4 位有效数字，
     *         45 位精度下 atan(1) 只能算到 1e-6 —— 屏幕上看得见的错误，不能用。</li>
     *     <li>atan(x) = π/4 + atan((x−1)/(x+1)) <b>只在 x &gt; 0 时成立</b>。
     *         把它喂给负数会得到 0.577→−0.268→−1.732→3.732→0.577 的循环，
     *         答案直接错掉（曾经真的这么错过）。所以归约全程只处理正数。</li>
     *     <li>阈值不能太大，否则会在不动点附近反复打转。</li>
     * </ul>
     *
     * <p>现在用的流程：先取绝对值并记住符号；|x| &gt; 1 用 π/2 − atan(1/|x|) 反射到 (0,1]；
     * 之后保持正数，用 π/4 + atan(|x−1|/(x+1)) 逐级压缩（结果符号取反），
     * 直到参数小于 0.5 就交给泰勒级数。每一步只有加减法、没有相消误差。
     * 另有保护：某次归约没能把参数变小就直接展开级数，绝不让递归失控
     * （tools/MathEngineTest 里有对照 π 的精度用例）。
     */
    static BigDecimal atan(BigDecimal value) throws MathException {
        if (value.signum() == 0) return BigDecimal.ZERO;
        boolean negative = value.signum() < 0;
        BigDecimal result = atanPositive(negative ? value.negate() : value);
        return negative ? result.negate() : result;
    }

    /**
     * 只处理正数的 arctan。
     *
     * <p>核心恒等式：x &gt; 0 时 atan(x) = π/4 + atan((x−1)/(x+1))。
     * 此时 r = (x−1)/(x+1) 是负数，而 atan 是奇函数，所以
     * atan(x) = π/4 + atan(r) = π/4 − atan(|r|)。
     *
     * <p>这里就是之前翻车的地方：写成 π/4 − atan(带符号的 r)（等价于 π/4 + atan(|r|)）
     * 会让 atan(0.5) 算出 0.4603 而不是 0.4636，asin(0.5) 从 30° 变成 30.0098°。
     * 所以下面用 magnitude 并且是 subtract。
     */
    private static BigDecimal atanPositive(BigDecimal x) throws MathException {
        BigDecimal halfPi = PI.divide(TWO, MC);
        BigDecimal quarterPi = PI.divide(new BigDecimal("4"), MC);

        if (x.compareTo(BigDecimal.ONE) > 0) {
            // atan(x) = π/2 − atan(1/x)
            return halfPi.subtract(atanPositive(BigDecimal.ONE.divide(x, MC)), MC);
        }
        if (x.compareTo(ATAN_SERIES_THRESHOLD) <= 0) return atanCore(x);

        BigDecimal reduced = x.subtract(BigDecimal.ONE, MC).divide(x.add(BigDecimal.ONE, MC), MC);
        BigDecimal magnitude = reduced.abs();
        if (magnitude.compareTo(x) >= 0) return atanCore(x); // 归约不动了，直接收级数

        return quarterPi.subtract(atanPositive(magnitude), MC);
    }

    /** 小于这个值就直接用泰勒级数。 */
    private static final BigDecimal ATAN_SERIES_THRESHOLD = new BigDecimal("0.5");

    /**
     * 泰勒级数 atan(z) = z − z³/3 + z⁵/5 − z⁷/7 + …，|z| ≤ 0.5。
     *
     * <p>注意这里不能用「上一项乘 z² 再除 (2k+1)」的递推：
     * (2k+1) 一旦被当成整数参与乘法，BigDecimal.divide(整数, MC) 就退化成
     * 向下取整的除法，于是 z³/3 变成 0.0416/3 → 被截断成 0.0416，
     * 整个级数收敛到 0.4603 而不是 0.4636（曾经真的这么错过）。
     * 所以幂和除法分开：先算精确的 z^(2k+1)，再除 (2k+1)。
     */
    private static BigDecimal atanCore(BigDecimal z) {
        BigDecimal z2 = z.multiply(z, MC);
        BigDecimal power = z;
        BigDecimal sum = z;
        for (int k = 1; k < 20000; k++) {
            power = power.multiply(z2, MC);
            BigDecimal term = power.divide(BigDecimal.valueOf(2L * k + 1), MC);
            sum = (k % 2 == 1) ? sum.subtract(term, MC) : sum.add(term, MC);
            if (term.abs().compareTo(SERIES_LIMIT) < 0) break;
        }
        return sum;
    }

    /**
     * sin 的泰勒级数：sin x = x − x³/3! + x⁵/5! − x⁷/7! + …
     *
     * <p>内部一律按 |x| 展开、最后再补符号。这一点非常关键：
     * 如果直接喂负数，级数的各项会以相反符号累加、互相抵消，
     * 45 位精度会被吃光（实测 cos(2000) 因此算成 −0.93 而不是 0.367）。
     * 幂、阶乘、除法分开算，原因见 {@link #atanCore}。
     */
    private static BigDecimal sinSeries(BigDecimal x) {
        boolean negative = x.signum() < 0;
        BigDecimal ax = negative ? x.negate() : x;
        BigDecimal x2 = ax.multiply(ax, MC);
        BigDecimal power = ax;
        BigDecimal sum = ax;
        BigDecimal factorial = BigDecimal.ONE; // 当前项的分母 (2n+1)!
        for (int n = 1; n < 400; n++) {
            power = power.multiply(x2, MC);
            factorial = factorial
                    .multiply(BigDecimal.valueOf(2L * n))
                    .multiply(BigDecimal.valueOf(2L * n + 1));
            BigDecimal term = power.divide(factorial, MC);
            sum = (n % 2 == 1) ? sum.subtract(term, MC) : sum.add(term, MC);
            if (term.abs().compareTo(SERIES_LIMIT) < 0) break;
        }
        return negative ? sum.negate() : sum;
    }

    /**
     * cos 的泰勒级数：cos x = 1 − x²/2! + x⁴/4! − x⁶/6! + …
     *
     * <p>cos 是偶函数，内部按 |x| 展开，原因同 {@link #sinSeries}。
     */
    private static BigDecimal cosSeries(BigDecimal x) {
        BigDecimal ax = x.abs();
        BigDecimal x2 = ax.multiply(ax, MC);
        BigDecimal power = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal factorial = BigDecimal.ONE; // 当前项的分母 (2n)!
        for (int n = 1; n < 400; n++) {
            power = power.multiply(x2, MC);
            factorial = factorial
                    .multiply(BigDecimal.valueOf(2L * n - 1))
                    .multiply(BigDecimal.valueOf(2L * n));
            BigDecimal term = power.divide(factorial, MC);
            sum = (n % 2 == 1) ? sum.subtract(term, MC) : sum.add(term, MC);
            if (term.abs().compareTo(SERIES_LIMIT) < 0) break;
        }
        return sum;
    }

    /** 把任意弧度值规约到 [0, 2π)，避免大角度下泰勒级数精度崩塌。 */
    private static BigDecimal reduce(BigDecimal x) {
        if (x.abs().compareTo(PI_REDUCE.multiply(BigDecimal.valueOf(1000))) < 0) return x;
        BigDecimal[] qr = x.divideAndRemainder(TWO_PI_REDUCE, new MathContext(PRECISION + 25, RoundingMode.HALF_UP));
        return qr[1].round(MC);
    }

    // ------------------------------------------------------------------
    // 对数与指数
    // ------------------------------------------------------------------

    /** 自然对数：先把数压到 [0.75, 1.5]，再用 atanh 级数。 */
    static BigDecimal ln(BigDecimal x) throws MathException {
        if (x.signum() <= 0) throw new MathException("对数只能对正数求解");

        int exponent = 0;
        BigDecimal adjusted = x;
        BigDecimal lower = new BigDecimal("0.75");
        BigDecimal upper = new BigDecimal("1.5");
        while (adjusted.compareTo(upper) >= 0) {
            adjusted = adjusted.divide(TWO, MC);
            exponent++;
        }
        while (adjusted.compareTo(lower) < 0) {
            adjusted = adjusted.multiply(TWO, MC);
            exponent--;
        }

        BigDecimal z = adjusted.subtract(BigDecimal.ONE, MC)
                .divide(adjusted.add(BigDecimal.ONE, MC), MC);
        BigDecimal z2 = z.multiply(z, MC);
        // atanh(z) = z + z³/3 + z⁵/5 + …，幂与除法分开算，原因见 atanCore
        BigDecimal power = z;
        BigDecimal sum = z;
        for (int k = 1; k < 20000; k++) {
            power = power.multiply(z2, MC);
            BigDecimal addend = power.divide(BigDecimal.valueOf(2L * k + 1), MC);
            sum = sum.add(addend, MC);
            if (addend.abs().compareTo(SERIES_LIMIT) < 0) break;
        }
        BigDecimal result = sum.multiply(TWO, MC);
        if (exponent != 0) result = result.add(new BigDecimal(exponent).multiply(LN2, MC), MC);
        return result;
    }

    /** ln(2)，由 atanh 级数在 1/3 处算出。 */
    private static final BigDecimal LN2 = computeLn2();

    private static BigDecimal computeLn2() {
        BigDecimal third = BigDecimal.ONE.divide(new BigDecimal("3"), MC);
        BigDecimal z2 = third.multiply(third, MC);
        BigDecimal power = third;
        BigDecimal sum = third;
        for (int k = 1; k < 20000; k++) {
            power = power.multiply(z2, MC);
            BigDecimal addend = power.divide(BigDecimal.valueOf(2L * k + 1), MC);
            sum = sum.add(addend, MC);
            if (addend.abs().compareTo(SERIES_LIMIT) < 0) break;
        }
        return sum.multiply(TWO, MC);
    }

    static BigDecimal log10(BigDecimal x) throws MathException {
        return ln(x).divide(ln(new BigDecimal("10")), MC);
    }

    static BigDecimal logBase(BigDecimal base, BigDecimal x) throws MathException {
        if (base.signum() <= 0 || base.compareTo(BigDecimal.ONE) == 0) {
            throw new MathException("对数的底数必须是不等于 1 的正数");
        }
        return ln(x).divide(ln(base), MC);
    }

    // ------------------------------------------------------------------
    // 反双曲函数：用对数恒等式实现
    //   asinh x = ln(x + √(x²+1))
    //   acosh x = ln(x + √(x²−1))     x ≥ 1
    //   atanh x = ½·ln((1+x)/(1−x))   |x| < 1
    // ------------------------------------------------------------------

    static BigDecimal asinh(BigDecimal x) throws MathException {
        BigDecimal inner = sqrt(x.multiply(x, MC).add(BigDecimal.ONE, MC), PRECISION + 10);
        return ln(x.add(inner, MC));
    }

    static BigDecimal acosh(BigDecimal x) throws MathException {
        if (x.compareTo(BigDecimal.ONE) < 0) throw new MathException("acosh 的输入必须不小于 1");
        BigDecimal inner = sqrt(x.multiply(x, MC).subtract(BigDecimal.ONE, MC), PRECISION + 10);
        return ln(x.add(inner, MC));
    }

    static BigDecimal atanh(BigDecimal x) throws MathException {
        if (x.abs().compareTo(BigDecimal.ONE) >= 0) throw new MathException("atanh 的输入必须在 -1 到 1 之间");
        if (x.signum() == 0) return BigDecimal.ZERO;
        BigDecimal ratio = BigDecimal.ONE.add(x, MC).divide(BigDecimal.ONE.subtract(x, MC), MC);
        return ln(ratio).divide(TWO, MC);
    }

    /** e^x：拆成整数部分与小数部分，小数部分用泰勒级数。 */
    static BigDecimal exp(BigDecimal x) throws MathException {
        BigInteger integerPart = x.toBigInteger();
        BigDecimal fraction = x.subtract(new BigDecimal(integerPart), MC);
        if (fraction.signum() < 0) {
            fraction = fraction.add(BigDecimal.ONE, MC);
            integerPart = integerPart.subtract(BigInteger.ONE);
        }
        // 先比大小再转 int：直接 intValue() 遇到 2^31 会溢出成负数，
        // 后面 E.pow() 就会抛「Invalid operation」这种原生异常（曾经真的抛过）
        if (integerPart.abs().compareTo(BigInteger.valueOf(1000000)) > 0) {
            throw new MathException("结果太大，算不出来");
        }

        BigDecimal fractionPower = BigDecimal.ONE;
        BigDecimal factorial = BigDecimal.ONE; // 当前项的分母 k!
        BigDecimal sum = BigDecimal.ONE;
        for (int k = 1; k < 20000; k++) {
            fractionPower = fractionPower.multiply(fraction, MC);
            factorial = factorial.multiply(BigDecimal.valueOf(k));
            BigDecimal term = fractionPower.divide(factorial, MC);
            sum = sum.add(term, MC);
            if (term.abs().compareTo(SERIES_LIMIT) < 0) break;
        }

        int integerExponent = integerPart.intValue();
        if (integerExponent == 0) return sum;
        BigDecimal scaleFactor = E.pow(Math.abs(integerExponent), MC);
        return integerExponent > 0
                ? sum.multiply(scaleFactor, MC)
                : sum.divide(scaleFactor, MC);
    }

    // ------------------------------------------------------------------
    // 常量生成
    // ------------------------------------------------------------------

    /** Machin 公式：π = 16·atan(1/5) − 4·atan(1/239)，用整数运算保证每一位都可靠。 */
    private static BigDecimal machin(int digits) {
        int scale = digits + 10;
        BigInteger unity = BigInteger.TEN.pow(scale);
        BigInteger first = arctanInverse(5, unity, scale);
        BigInteger second = arctanInverse(239, unity, scale);
        BigInteger pi = first.multiply(BigInteger.valueOf(16))
                .subtract(second.multiply(BigInteger.valueOf(4)));
        return new BigDecimal(pi, scale).round(new MathContext(digits, RoundingMode.HALF_UP));
    }

    private static BigInteger arctanInverse(int inverse, BigInteger unity, int scale) {
        BigInteger x = unity.divide(BigInteger.valueOf(inverse));
        BigInteger xSquared = BigInteger.valueOf((long) inverse * inverse);
        BigInteger term = x;
        BigInteger sum = x;
        int n = 1;
        while (term.signum() != 0) {
            term = term.divide(xSquared);
            if (term.signum() == 0) break;
            BigInteger addend = term.divide(BigInteger.valueOf(2L * n + 1));
            if (addend.signum() == 0) break;
            sum = (n % 2 == 1) ? sum.subtract(addend) : sum.add(addend);
            n++;
        }
        return sum;
    }

    /** e = 1/0! + 1/1! + 1/2! + ... */
    private static BigDecimal computeE(int digits) {
        int scale = digits + 10;
        BigInteger unity = BigInteger.TEN.pow(scale);
        BigInteger sum = unity;
        BigInteger term = unity;
        for (int k = 1; k < 5000; k++) {
            term = term.divide(BigInteger.valueOf(k));
            if (term.signum() == 0) break;
            sum = sum.add(term);
        }
        return new BigDecimal(sum, scale).round(new MathContext(digits, RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 如果 b 在数学上是整数，返回它的整数值；否则返回 null。 */
    private static BigDecimal exactInteger(BigDecimal b) {
        BigDecimal stripped = b.stripTrailingZeros();
        if (stripped.scale() <= 0) return stripped;
        return null;
    }

    /**
     * a^b 在实数范围内是否为有理数幂，例如 (-8)^(1/3) = -2、(-8)^(2/3) = 4。
     *
     * <p>b 得先写成最简分数 k/n。这里用连分数做有理逼近，而不是简单地取倒数再取整：
     * 计算器里 1÷3 得到的是 0.3333…（不是精确的 1/3），
     * 直接拿倒数去凑整数会认不出来，(-8)^(2÷3) 就会被误判成「实数范围内无解」。
     * 连分数对 0.333…、0.666… 这类循环小数都能还原出 1/3、2/3。
     */
    private static BigDecimal exactRoot(BigDecimal a, BigDecimal b) {
        BigInteger[] fraction = approximateFraction(b, 64);
        if (fraction == null) return null;
        return rationalPow(a, fraction[0], fraction[1]);
    }

    /** a^(k/n) 的实数解，解不出来返回 null。 */
    private static BigDecimal rationalPow(BigDecimal a, BigInteger k, BigInteger n) {
        if (n.signum() == 0) return null;
        BigInteger denominator = n.abs();
        if (denominator.signum() == 0 || k.signum() == 0) return null;

        boolean negativeBase = a.signum() < 0;
        // 负底数只在分母为奇数时才有实数的 n 次方根
        if (negativeBase && !denominator.testBit(0)) return null;

        try {
            BigDecimal base = negativeBase ? a.negate() : a;
            BigDecimal root = root(new BigDecimal(denominator), base);
            // k 是奇数时结果是负数
            if (negativeBase && k.testBit(0)) root = root.negate();

            int power = k.abs().intValueExact();
            BigDecimal result = root.pow(power, MC);
            if (k.signum() < 0) {
                if (result.signum() == 0) return null;
                result = BigDecimal.ONE.divide(result, MC);
            }
            return result;
        } catch (MathException e) {
            return null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * 把十进制数识别成一个「小分母分数」，返回 {分子, 分母}；认不出来返回 null。
     *
     * <p>直接从小到大枚举分母，看 b×n 是不是接近整数。这比连分数更省心：
     * 计算器里 1÷3 得到的是 0.3333…（不是精确的 1/3），
     * 只要 b×n 的偏差小于 1e-24 就认下来。
     * 分母上限设成 1000，代价可以忽略，又能覆盖常见的 1/2、1/3、2/3、1/4…
     */
    private static BigInteger[] approximateFraction(BigDecimal value, int maxDenominator) {
        if (value.signum() == 0) return null;
        BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-24);

        for (int n = 2; n <= maxDenominator; n++) {
            BigDecimal product = value.multiply(BigDecimal.valueOf(n), MC);
            BigDecimal rounded = product.setScale(0, RoundingMode.HALF_UP);
            if (product.subtract(rounded).abs().compareTo(tolerance) > 0) continue;

            // 约分：分子分母同除最大公约数
            BigInteger numerator = rounded.toBigInteger();
            BigInteger denominator = BigInteger.valueOf(n);
            BigInteger gcd = numerator.gcd(denominator);
            if (gcd.signum() != 0) {
                numerator = numerator.divide(gcd);
                denominator = denominator.divide(gcd);
            }
            if (denominator.signum() == 0 || numerator.signum() == 0) return null;
            return new BigInteger[]{numerator, denominator};
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 词法分析
    // ------------------------------------------------------------------

    private enum Kind {
        NUMBER, NAME, CONST, PLUS, MINUS, STAR, SLASH, CARET, BANG, PERCENT,
        LPAREN, RPAREN, COMMA, EOF
    }

    private static final class Token {
        final Kind kind;
        final String text;
        final BigDecimal number;
        /** 这个 token 在原始文本里的起始下标，用来消解 % 的歧义。 */
        final int position;

        Token(Kind kind, String text, BigDecimal number) {
            this(kind, text, number, -1);
        }

        Token(Kind kind, String text, BigDecimal number, int position) {
            this.kind = kind;
            this.text = text;
            this.number = number;
            this.position = position;
        }

        @Override
        public String toString() {
            return kind + "(" + text + ")";
        }
    }

    /**
     * 把表达式切成 token，并在此阶段插入隐式乘法。
     * 例如 {@code 2pi} 会变成 {@code 2 * pi}，{@code 3(4)} 会变成 {@code 3 * (4)}。
     */
    private static final class Tokenizer {
        private final String src;
        private final List<Token> out = new ArrayList<>();
        private int pos = 0;
        private boolean previousWasValue = false;

        Tokenizer(String src) {
            this.src = src;
        }

        List<Token> tokenize() throws MathException {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                    continue;
                }
                if (isDigit(c) || c == '.') {
                    pushNumber();
                    continue;
                }
                if (isNameStart(c)) {
                    pushName();
                    continue;
                }
                int tokenStart = pos;
                pos++;
                switch (c) {
                    case '#':
                        // 注意：π 和 e 走的是这条分支，隐式乘法必须在这里也补一次，
                        // 否则 2π / 2e 这些写法会解析失败（曾经真的漏了）
                        // 隐式乘法交给 Parser 处理：词法阶段插入 * 会和解析器的兜底逻辑打架
                        addConst(PI, "pi");
                        break;
                    case '$':
                        // 隐式乘法交给 Parser 处理：词法阶段插入 * 会和解析器的兜底逻辑打架
                        addConst(E, "e");
                        break;
                    case '+':
                        if (previousWasValue) add(Kind.PLUS, "+");
                        else parseLeadingSign(1);
                        break;
                    case '-':
                        if (previousWasValue) add(Kind.MINUS, "-");
                        else parseLeadingSign(-1);
                        break;
                    case '*':
                        add(Kind.STAR, "*");
                        break;
                    case '/':
                        add(Kind.SLASH, "/");
                        break;
                    case '^':
                        add(Kind.CARET, "^");
                        break;
                    case '!':
                        add(Kind.BANG, "!");
                        break;
                    case '%':
                        if (previousWasValue) add(Kind.PERCENT, "%", tokenStart);
                        else throw new MathException("百分号 / 取模的位置不对");
                        break;
                    case '(':
                        // 隐式乘法交给 Parser 处理：词法阶段插入 * 会和解析器的兜底逻辑打架
                        add(Kind.LPAREN, "(");
                        break;
                    case ')':
                        add(Kind.RPAREN, ")");
                        break;
                    case ',':
                        add(Kind.COMMA, ",");
                        break;
                    default:
                        throw new MathException("无法识别的符号：" + c);
                }
            }
            out.add(new Token(Kind.EOF, "", null));
            return out;
        }

        /**
         * 开头的 +/- 一律当作一元运算符处理。
         * 不能把 -2 直接合成一个负数 token，否则 -2^2 会算成 (-2)^2 = 4，
         * 而数学上应该是 -(2^2) = -4。
         */
        private void parseLeadingSign(int sign) {
            out.add(new Token(sign < 0 ? Kind.MINUS : Kind.PLUS, sign < 0 ? "-" : "+", null));
            previousWasValue = false;
        }

        private void pushNumber() throws MathException {
            int start = pos;
            boolean dotSeen = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    pos++;
                } else {
                    break;
                }
            }
            // 科学计数法的后一半：结果就是这么显示出来的（例如 1000! = 4.02e2567），
            // 屏幕上的结果必须能被重新读回来算，所以这里要认它。
            // 只有 e 后面确实跟着（可选正负号 +）数字时才吞掉，
            // 这样 "2e"（2 乘自然常数）依旧走隐式乘法。
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                int lookahead = pos + 1;
                if (lookahead < src.length()
                        && (src.charAt(lookahead) == '+' || src.charAt(lookahead) == '-')) {
                    lookahead++;
                }
                if (lookahead < src.length() && isDigit(src.charAt(lookahead))) {
                    pos = lookahead;
                    while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
                }
            }
            String text = src.substring(start, pos);
            if (text.equals(".")) throw new MathException("小数点位置不对");
            // "1..2" 会在切分时从 ".2" 处冒出一个新数字，必须拦住，
            // 否则 1..2 会被悄悄算成 1 × 0.2。
            // 判断依据是「紧挨着的前一个字符」：小数点前面只能是数字。
            if (text.startsWith(".") && pos > 0 && (isDigit(src.charAt(pos - 1)) || src.charAt(pos - 1) == '.')) {
                throw new MathException("小数点重复了");
            }
            try {
                out.add(new Token(Kind.NUMBER, text, new BigDecimal(text)));
                previousWasValue = true;
            } catch (NumberFormatException e) {
                throw new MathException("数字格式不对：" + text);
            }
        }

        private void pushName() throws MathException {
            int start = pos;
            // 只吃字母：这样 "2e" 会被切成 2 × e，而不是一个叫 "e" 的函数名；
            // "e2"、"pi2" 也都能正确理解成乘法。
            while (pos < src.length() && isNameLetter(src.charAt(pos))) pos++;
            String name = src.substring(start, pos);

            // 隐式乘法交给 Parser 处理：词法阶段插入 * 会和解析器的兜底逻辑打架

            if (name.equals("pi")) {
                addConst(PI, "pi");
            } else if (name.equals("e")) {
                addConst(E, "e");
            } else if (name.equals("ans")) {
                addConst(lastAnswer, "ans");
            } else if (isKnownFunction(name)) {
                out.add(new Token(Kind.NAME, name, null));
                previousWasValue = false;
            } else {
                throw new MathException("不认识这个名字：" + name);
            }
        }

        private static boolean isKnownFunction(String name) {
            switch (name) {
                case "sin": case "cos": case "tan":
                case "asin": case "acos": case "atan":
                case "arcsin": case "arccos": case "arctan":
                case "sinh": case "cosh": case "tanh":
                case "asinh": case "acosh": case "atanh":
                case "ln": case "lg": case "log":
                case "sqrt": case "cbrt": case "root":
                case "inv": case "abs": case "sqr": case "exp": case "pow":
                case "deg": case "rad":
                    return true;
                default:
                    return false;
            }
        }

        private void addConst(BigDecimal value, String text) {
            out.add(new Token(Kind.CONST, text, value));
            previousWasValue = true;
        }

        private void add(Kind kind, String text) {
            add(kind, text, -1);
        }

        private void add(Kind kind, String text, int position) {
            out.add(new Token(kind, text, null, position));
            previousWasValue = kind == Kind.RPAREN || kind == Kind.BANG || kind == Kind.PERCENT;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isNameStart(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private static boolean isNameLetter(char c) {
            return isNameStart(c);
        }
    }

    // ------------------------------------------------------------------
    // 语法分析（Pratt：数字越大结合越紧）
    // ------------------------------------------------------------------

    private static final class Parser {
        private final List<Token> tokens;
        /** 原始文本，% 的歧义判断需要看它（token 流里已经被插入过隐式乘号）。 */
        private final String source;
        private final int angleMode;
        private int index = 0;

        Parser(List<Token> tokens, int angleMode) {
            this(tokens, "", angleMode);
        }

        Parser(List<Token> tokens, String source, int angleMode) {
            this.tokens = tokens;
            this.source = source == null ? "" : source;
            this.angleMode = angleMode;
        }

        BigDecimal parse() throws MathException {
            if (peek().kind == Kind.EOF) throw new MathException("表达式是空的");
            BigDecimal value = expression(0);
            if (peek().kind != Kind.EOF) throw new MathException("表达式没有写完或者括号不匹配");
            return value;
        }

        private BigDecimal expression(int minPrecedence) throws MathException {
            BigDecimal left = prefix();

            // 后缀运算符（! 和 %）永远先落地。
            // % 有歧义：50% 里的 % 是「除以 100」，7%2 里的 % 是取模。
            // 判断方法就看 % 后面还有没有操作数 —— 有就是取模，没有就是百分号。
            for (; ; ) {
                Token token = peek();
                if (token.kind == Kind.BANG) {
                    next();
                    left = factorial(left);
                } else if (token.kind == Kind.PERCENT && percentIsPostfix()) {
                    next();
                    left = percent(left);
                } else {
                    break;
                }
            }

            for (; ; ) {
                Token token = peek();
                int precedence = precedenceOf(token);
                if (precedence < minPrecedence) {
                    // 隐式乘法兜底：数字/常量/函数/左括号紧挨在一起时按乘法算，
                    // 例如 2(3)4、2sin(30)、2pi。词法阶段已经补过一部分 *，
                    // 这里保证「衔接到值」的情况一个都不漏。
                    if (canStartValue(token) && PRECEDENCE_MUL >= minPrecedence) {
                        BigDecimal right = expression(PRECEDENCE_MUL + 1);
                        left = left.multiply(right, MC);
                        continue;
                    }
                    return left;
                }
                next();
                int nextMin = rightAssociative(token) ? precedence : precedence + 1;
                BigDecimal right = expression(nextMin);
                left = apply(token, left, right);
            }
        }

        /** 这个 token 能不能作为一个值的开头（数字、常量、函数名、左括号）。 */
        private static boolean canStartValue(Token token) {
            switch (token.kind) {
                case NUMBER:
                case CONST:
                case NAME:
                case LPAREN:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * 当前的 % 是不是「百分号」而不是取模。
         *
         * <p>之所以要区分「隐式补出来的 *」和用户真按的 ×：
         * tokenizer 会在「值后面紧跟左括号」时插入一个 STAR，
         * 于是 {@code 10%(3)} 的 % 后面看起来是 STAR，会被误判成后缀百分号
         * （曾经 %() 取模因此彻底失效，10%(3) 被算成 0.3）。
         * 现在只要看到「隐式 STAR + (」，就认定是取模。
         */
        /**
         * 当前的 % 是不是「百分号」而不是取模。
         *
         * <p>判据：% 后面还跟着操作数的开头（数字、小数点、字母、左括号、正负号）
         * 就是二元的取模；走到末尾、右括号或逗号，它才是后缀百分号。
         *
         * <p>这里看的是 % 在原文里的位置，而不是 token 流 —— 之前词法阶段会在
         * 「值后紧跟左括号」时插一个隐式 *，于是 10%(3) 的 % 后面看起来是 *，
         * 被判成百分号，整个 %() 取模就废了。现在隐式乘法统一由 Parser 兜底，
         * 词法阶段不再插 *，这个歧义就没了。
         */
        private boolean percentIsPostfix() {
            int at = tokens.get(index).position;
            if (at < 0) return true;
            int i = at + 1;
            while (i < source.length() && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) i++;
            if (i >= source.length()) return true;
            char c = source.charAt(i);
            if (c == '(' || c == '.') return false;
            if (c >= '0' && c <= '9') return false;
            if (c == '+' || c == '-') return false;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return false;
            return true;
        }

        private BigDecimal prefix() throws MathException {            Token token = peek();
            if (token.kind == Kind.MINUS) {
                next();
                return prefix().negate();
            }
            if (token.kind == Kind.PLUS) {
                next();
                return prefix();
            }
            return power();
        }

        /** 乘方比一元负号结合得更紧，所以单独占一层，保证 -2^2 = -4。 */
        private BigDecimal power() throws MathException {
            BigDecimal base = atom();
            if (peek().kind == Kind.CARET) {
                next();
                BigDecimal exponent = expression(PRECEDENCE_POW_UNARY);
                return pow(base, exponent);
            }
            return base;
        }

        private BigDecimal atom() throws MathException {
            Token token = next();
            switch (token.kind) {
                case NUMBER:
                    return token.number;
                case CONST:
                    return token.number;
                case LPAREN: {
                    BigDecimal inner = expression(0);
                    expect(Kind.RPAREN, "缺少右括号");
                    return inner;
                }
                case NAME:
                    return function(token.text);
                default:
                    throw new MathException("表达式里出现了奇怪的符号：" + token.text);
            }
        }

        private BigDecimal function(String rawName) throws MathException {
            String name = rawName.toLowerCase();
            if (name.startsWith("arc")) name = "a" + name.substring(3);
            if (isDegreesFunction(name)) {
                // 例如 deg(x)：把 x 当作角度制处理
                name = name.substring(3);
            }

            List<BigDecimal> args = new ArrayList<>();
            if (peek().kind == Kind.LPAREN) {
                next();
                if (peek().kind != Kind.RPAREN) {
                    args.add(expression(0));
                    while (peek().kind == Kind.COMMA) {
                        next();
                        args.add(expression(0));
                    }
                }
                expect(Kind.RPAREN, name + " 缺少右括号");
            } else {
                args.add(prefix());
            }

            return applyFunction(name, args);
        }

        private BigDecimal applyFunction(String name, List<BigDecimal> args) throws MathException {
            switch (name) {
                case "sin":
                    requireArgs(name, args, 1);
                    return sin(toRadians(args.get(0), angleMode)).round(MC);
                case "cos":
                    requireArgs(name, args, 1);
                    return cos(toRadians(args.get(0), angleMode)).round(MC);
                case "tan":
                    requireArgs(name, args, 1);
                    return tan(toRadians(args.get(0), angleMode)).round(MC);
                case "asin":
                    requireArgs(name, args, 1);
                    return fromRadians(asin(args.get(0)), angleMode);
                case "acos":
                    requireArgs(name, args, 1);
                    return fromRadians(acos(args.get(0)), angleMode);
                case "atan":
                    requireArgs(name, args, 1);
                    return fromRadians(atan(args.get(0)), angleMode);
                case "sinh":
                    requireArgs(name, args, 1);
                    return sinh(args.get(0));
                case "cosh":
                    requireArgs(name, args, 1);
                    return cosh(args.get(0));
                case "tanh":
                    requireArgs(name, args, 1);
                    return sinh(args.get(0)).divide(cosh(args.get(0)), MC);
                case "asinh":
                    requireArgs(name, args, 1);
                    return asinh(args.get(0));
                case "acosh":
                    requireArgs(name, args, 1);
                    return acosh(args.get(0));
                case "atanh":
                    requireArgs(name, args, 1);
                    return atanh(args.get(0));
                case "ln":
                    requireArgs(name, args, 1);
                    return ln(args.get(0));
                case "lg":
                    requireArgs(name, args, 1);
                    return log10(args.get(0));
                case "log":
                    if (args.size() == 1) return log10(args.get(0));
                    requireArgs(name, args, 2);
                    return logBase(args.get(0), args.get(1));
                case "sqrt":
                    requireArgs(name, args, 1);
                    return sqrt(args.get(0));
                case "cbrt":
                    requireArgs(name, args, 1);
                    return root(new BigDecimal("3"), args.get(0));
                case "root":
                    requireArgs(name, args, 2);
                    return root(args.get(0), args.get(1));
                case "inv":
                    requireArgs(name, args, 1);
                    return reciprocal(args.get(0));
                case "abs":
                    requireArgs(name, args, 1);
                    return abs(args.get(0));
                case "sqr":
                    requireArgs(name, args, 1);
                    return args.get(0).multiply(args.get(0), MC);
                case "exp":
                    requireArgs(name, args, 1);
                    return exp(args.get(0));
                case "pow":
                    requireArgs(name, args, 2);
                    return pow(args.get(0), args.get(1));
                case "deg":
                case "rad":
                    requireArgs(name, args, 1);
                    return args.get(0);
                default:
                    throw new MathException("不认识这个函数：" + name);
            }
        }

        /** 逆三角函数在角度制下要把弧度结果换算回角度。 */
        private static BigDecimal fromRadians(BigDecimal radians, int angleMode) {
            if (angleMode != DEG) return radians;
            return radians.multiply(new BigDecimal("180"), MC).divide(PI, MC);
        }

        private static BigDecimal sinh(BigDecimal x) throws MathException {
            BigDecimal e1 = exp(x);
            BigDecimal e2 = exp(x.negate());
            return e1.subtract(e2, MC).divide(TWO, MC);
        }

        private static BigDecimal cosh(BigDecimal x) throws MathException {
            BigDecimal e1 = exp(x);
            BigDecimal e2 = exp(x.negate());
            return e1.add(e2, MC).divide(TWO, MC);
        }

        private static boolean isDegreesFunction(String name) {
            return name.length() > 3 && name.startsWith("deg");
        }

        private static void requireArgs(String name, List<BigDecimal> args, int expected) throws MathException {
            if (args.size() != expected) {
                throw new MathException(name + " 需要 " + expected + " 个参数，实际给了 " + args.size() + " 个");
            }
        }

        private static final int PRECEDENCE_LOWEST = 0;
        private static final int PRECEDENCE_ADD = 1;
        private static final int PRECEDENCE_MUL = 2;
        private static final int PRECEDENCE_UNARY = 3;
        private static final int PRECEDENCE_POW = 4;
        /** 乘方右边的起点：允许 2^-1 这种写法，同时保证右结合。 */
        private static final int PRECEDENCE_POW_UNARY = PRECEDENCE_UNARY;
        /** 不是运算符的 token（数字、右括号、末尾）用这个值，保证一定会让循环返回。 */
        private static final int PRECEDENCE_NONE = -1;

        private static int precedenceOf(Token token) {
            switch (token.kind) {
                case PLUS:
                case MINUS:
                    return PRECEDENCE_ADD;
                case STAR:
                case SLASH:
                case PERCENT:
                    return PRECEDENCE_MUL;
                case CARET:
                    return PRECEDENCE_POW;
                default:
                    return PRECEDENCE_NONE;
            }
        }

        private static boolean rightAssociative(Token token) {
            return token.kind == Kind.CARET;
        }

        private BigDecimal apply(Token token, BigDecimal left, BigDecimal right) throws MathException {
            switch (token.kind) {
                case PLUS:
                    return left.add(right, MC);
                case MINUS:
                    return left.subtract(right, MC);
                case STAR:
                    return left.multiply(right, MC);
                case SLASH:
                    if (right.signum() == 0) throw new MathException("除数不能是 0");
                    return left.divide(right, MC);
                case PERCENT:
                    return modulo(left, right);
                case CARET:
                    return pow(left, right);
                default:
                    throw new MathException("无法处理的运算符：" + token.text);
            }
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token next() {
            Token token = tokens.get(index);
            if (token.kind != Kind.EOF) index++;
            return token;
        }

        private void expect(Kind kind, String message) throws MathException {
            if (peek().kind != kind) throw new MathException(message);
            next();
        }
    }

    /** 计算过程中的可预期错误（除零、负数开方等等），界面会把它显示成 Error。 */
    public static class MathException extends Exception {
        private static final long serialVersionUID = 1L;

        public MathException(String message) {
            super(message);
        }
    }
}
