package com.nickwoluff.wearcalculator;

import java.math.BigDecimal;

/**
 * 纯 Java 验证程序（不属于 app 构建，只放在 tools 目录下用来跑数学用例）。
 * 编译：javac -d build/tools app/src/main/java/com/nickwoluff/wearcalculator/MathEngine.java tools/MathEngineTest.java
 * 运行：java -cp build/tools com.nickwoluff.wearcalculator.MathEngineTest
 */
public final class MathEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---------------- 基础四则（回归测试） ----------------
        eq("1+2", "3", MathEngine.DEG);
        eq("7×8", "56", MathEngine.DEG);
        eq("10÷4", "2.5", MathEngine.DEG);
        eq("1÷3", "0.33333333...", MathEngine.DEG);
        eq("2-5", "-3", MathEngine.DEG);
        eq("−6+2", "-4", MathEngine.DEG);

        // ---------------- 括号 ----------------
        eq("(1+2)×3", "9", MathEngine.DEG);
        eq("((2+3)×(4-1))", "15", MathEngine.DEG);
        eq("2×(3+(4-1)×2)", "18", MathEngine.DEG);

        // ---------------- 幂运算 ----------------
        eq("2^10", "1024", MathEngine.DEG);
        eq("2^3^2", "512", MathEngine.DEG);
        eq("2^-1", "0.5", MathEngine.DEG);
        eq("-2^2", "-4", MathEngine.DEG);
        eq("2^0.5", "1.41421356...", MathEngine.DEG);
        eq("9^0.5", "3", MathEngine.DEG);
        eq("8^(1÷3)", "2", MathEngine.DEG);

        // ---------------- 阶乘 ----------------
        eq("5!", "120", MathEngine.DEG);
        eq("0!", "1", MathEngine.DEG);
        eq("3!^2", "36", MathEngine.DEG);
        eq("2^3!", "64", MathEngine.DEG);
        eq("20!", "2432902008176640000", MathEngine.DEG);
        eq("100!", null, MathEngine.DEG); // 只需保证能算出来不报错

        // ---------------- 取模 / 百分号 ----------------
        eq("7%2", "1", MathEngine.DEG);
        eq("7.5%2", "1.5", MathEngine.DEG);
        eq("-7%2", "-1", MathEngine.DEG);
        eq("50%", "0.5", MathEngine.DEG);
        eq("200×10%", "20", MathEngine.DEG);

        // ---------------- 常量与隐式乘法 ----------------
        eq("pi", "3.14159265...", MathEngine.DEG);
        eq("e", "2.71828183...", MathEngine.DEG);
        eq("π", "3.14159265...", MathEngine.DEG);
        eq("2pi", "6.28318531...", MathEngine.DEG);
        eq("3(4+5)", "27", MathEngine.DEG);
        eq("(1+1)(2+2)", "8", MathEngine.DEG);
        eq("2e", "5.43656366...", MathEngine.DEG);
        eq("e^2", "7.3890561...", MathEngine.DEG);
        eq("2(3)4", "24", MathEngine.DEG);
        eq("2sin(30)", "1", MathEngine.DEG);
        // ---------------- 三角函数（角度制） ----------------
        eq("sin(30)", "0.5", MathEngine.DEG);
        eq("sin(90)", "1", MathEngine.DEG);
        eq("sin(0)", "0", MathEngine.DEG);
        eq("sin(180)", "0", MathEngine.DEG);
        eq("sin(270)", "-1", MathEngine.DEG);
        eq("cos(60)", "0.5", MathEngine.DEG);
        eq("cos(90)", "0", MathEngine.DEG);
        eq("cos(0)", "1", MathEngine.DEG);
        eq("cos(180)", "-1", MathEngine.DEG);
        eq("tan(45)", "1", MathEngine.DEG);
        eq("tan(0)", "0", MathEngine.DEG);
        eq("sin(30)+cos(60)", "1", MathEngine.DEG);
        eq("2sin(30)", "1", MathEngine.DEG);

        // ---------------- 三角函数（弧度制） ----------------
        eq("sin(pi÷2)", "1", MathEngine.RAD);
        eq("cos(pi)", "-1", MathEngine.RAD);
        eq("sin(pi÷6)", "0.5", MathEngine.RAD);
        eq("tan(pi÷4)", "1", MathEngine.RAD);
        eq("sin(pi)", "0", MathEngine.RAD);

        // 角度制下 30 度 ≠ 弧度 30（这条用例证明模式真的生效了）
        ne("sin(30)", "sin(pi÷6)", MathEngine.DEG);

        // ---------------- 对数 / 开方 ----------------
        eq("lg(1000)", "3", MathEngine.DEG);
        eq("ln(e)", "1", MathEngine.DEG);
        eq("ln(1)", "0", MathEngine.DEG);
        eq("log(2,8)", "3", MathEngine.DEG);
        eq("log(10,100)", "2", MathEngine.DEG);
        eq("sqrt(16)", "4", MathEngine.DEG);
        eq("sqrt(2)", "1.41421356...", MathEngine.DEG);
        eq("root(3,27)", "3", MathEngine.DEG);
        eq("inv(4)", "0.25", MathEngine.DEG);
        eq("abs(0-5)", "5", MathEngine.DEG);

        // 精度回归：ln(1e3) 应该精确到 3
        eq("log(2,1024)", "10", MathEngine.DEG);

        // 对数只认逗号形式；一个参数时是常用对数
        eq("log(2)", "0.30103...", MathEngine.DEG);

        // ---------------- 逆三角函数 ----------------
        eq("asin(0.5)", "30", MathEngine.DEG);
        eq("acos(0.5)", "60", MathEngine.DEG);
        eq("atan(1)", "45", MathEngine.DEG);
        eq("asin(1)", "90", MathEngine.DEG);
        eq("asin(0.5)", "0.52359878...", MathEngine.RAD);

        // ---------------- 综合表达式 ----------------
        eq("(2+3)!÷(2!×3!)", "10", MathEngine.DEG);
        eq("sin(30)^2+cos(30)^2", "1", MathEngine.DEG);
        eq("sqrt(3^2+4^2)", "5", MathEngine.DEG);
        eq("ln(e^5)", "5", MathEngine.DEG);
        eq("2×(1+2(3+4))", "30", MathEngine.DEG);
        eq("100÷(2!+3)", "20", MathEngine.DEG);

        // ---------------- 错误场景（必须报错而不是给错答案） ----------------
        err("1÷0");
        err("tan(90)");        // DEG 模式下无定义
        err("tan(270)");
        eq("(1+2", "3", MathEngine.DEG);   // 括号没闭合：现在会自动补齐
        err("1+)");
        err("sqrt(0-4)");      // 负数开平方
        err("(0-2)^0.5");      // 负数开非整数次方
        err("1..2");
        err("!5");             // 阶乘前面没东西
        err("sin(0-2)!");      // 负数阶乘
        err("(0-1)!");
        err("log(1,5)");       // 底数为 1
        err("lg(0-1)");        // 对数取非正数
        err("2^^3");
        eq("(((", "0", MathEngine.DEG);   // 未闭合的左括号会自动补 0 并闭合
        err("");

        // ---------------- 天文数字走科学计数法 ----------------
        eq("1000!", null, MathEngine.DEG);
        sci("1000!");

        // ---------------- 极端值不卡死 ----------------
        long start = System.currentTimeMillis();
        eq("2000!", null, MathEngine.DEG);
        eq("1.0000001^100000", null, MathEngine.DEG);
        eq("sin(1000000)", null, MathEngine.RAD);
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 20000) {
            fail("极端值用例耗时 " + elapsed + "ms，超过 20 秒，手表上会卡界面");
        } else {
            ok("极端值用例耗时 " + elapsed + "ms（阈值 20000ms）");
        }

        // ================================================================
        // 下面这一组是「外部独立验证找出来的缺陷」的回归用例。
        // 每一条都对应过一个真实发生过的错误答案，别再删掉。
        // ================================================================

        System.out.println();
        System.out.println("--- 回归：cos 四个象限（曾经 (90°,135°) 整段算错） ---");
        eq("cos(95)", "-0.08715574...", MathEngine.DEG);
        eq("cos(100)", "-0.17364818...", MathEngine.DEG);
        eq("cos(120)", "-0.5", MathEngine.DEG);
        eq("cos(130)", "-0.64278761", MathEngine.DEG);
        eq("cos(135)", "-0.70710678...", MathEngine.DEG);
        eq("cos(230)", "-0.64278761", MathEngine.DEG);
        eq("cos(240)", "-0.5", MathEngine.DEG);
        eq("cos(265)", "-0.08715574...", MathEngine.DEG);
        eq("tan(100)", "-5.67128182", MathEngine.DEG);
        eq("tan(240)", "1.73205081...", MathEngine.DEG);
        eq("cos(7pi÷12)", "-0.25881905...", MathEngine.RAD);
        eq("cos(2pi÷3)", "-0.5", MathEngine.RAD);
        eq("cos(4pi÷3)", "-0.5", MathEngine.RAD);

        System.out.println("--- 回归：大角度规约（曾经 sin(1000) = 5e421） ---");
        eq("sin(100)", "-0.50636564...", MathEngine.RAD);
        eq("sin(1000)", "0.82687954", MathEngine.RAD);
        eq("cos(1000)", "0.56237908...", MathEngine.RAD);
        eq("sin(2000)", "0.9300395...", MathEngine.RAD);
        eq("cos(2000)", "-0.93969262", MathEngine.DEG);
        eq("sin(3000)", "0.21918997...", MathEngine.RAD);
        eq("sin(123456.789)", "-0.39411836...", MathEngine.DEG);
        eq("cos(123456.789)", "0.91905969", MathEngine.DEG);
        eq("cos(1000)", "0.17364818...", MathEngine.DEG);
        eq("sin(1000)", "-0.98480775...", MathEngine.DEG);
        // sin²+cos² 恒等式：大角度下也必须成立
        eq("sin(1000)^2+cos(1000)^2", "1", MathEngine.RAD);
        eq("sin(2000)^2+cos(2000)^2", "1", MathEngine.RAD);
        eq("sin(999999999)^2+cos(999999999)^2", "1", MathEngine.RAD);
        eq("sin(123456.789)^2+cos(123456.789)^2", "1", MathEngine.DEG);

        System.out.println("--- 回归：极小值不再被抹成 0 ---");
        eq("10^-54", "1e-54", MathEngine.DEG);
        eq("10^-308", "1e-308", MathEngine.DEG);
        eq("2^-1074", "4.940656458e-324", MathEngine.DEG);
        eq("0.5^180", "6.525304468e-55", MathEngine.DEG);

        System.out.println("--- 回归：大整数格式化不再被改数字 ---");
        eq("3^39", "4052555153018976267", MathEngine.DEG);
        eq("9999999999999999999", "9999999999999999999", MathEngine.DEG);
        eq("9876543210987654321", "9876543210987654321", MathEngine.DEG);
        eq("123456789012345678*1+0.5", "123456789012345678.5", MathEngine.DEG);
        eq("12345678901234567.89", "12345678901234567.89", MathEngine.DEG);

        System.out.println("--- 回归：% 后跟括号按取模 ---");
        eq("10%(3)", "1", MathEngine.DEG);
        eq("10%3", "1", MathEngine.DEG);
        eq("10 % (3)", "1", MathEngine.DEG);
        eq("(10)%(3)", "1", MathEngine.DEG);
        eq("10%(2+1)", "1", MathEngine.DEG);
        eq("7%(0-2)", "1", MathEngine.DEG);
        eq("1+10%(3)", "2", MathEngine.DEG);
        eq("50%", "0.5", MathEngine.DEG);
        eq("200×10%", "20", MathEngine.DEG);

        System.out.println("--- 回归：exp()/deg() 不再被 e 的正规化吃掉 ---");
        eq("exp(0)", "1", MathEngine.DEG);
        eq("exp(1)", "2.71828183...", MathEngine.DEG);
        eq("deg(30)", "30", MathEngine.DEG);
        eq("rad(30)", "30", MathEngine.DEG);

        System.out.println("--- 回归：幂的上限不再过严，也不再有原生异常 ---");
        eq("1^999999999", "1", MathEngine.DEG);
        eq("(-1)^999999999", "-1", MathEngine.DEG);
        eq("(-1)^1000000000", "1", MathEngine.DEG);
        eq("0^999999999", "0", MathEngine.DEG);
        eq("2^100001", null, MathEngine.DEG);
        err("9^999999999");
        err("2^2000000000");
        err("10^1000000000");

        System.out.println("--- 回归：科学计数法可以来回读 ---");
        eq("1e3", "1000", MathEngine.DEG);
        eq("2e3", "2000", MathEngine.DEG);
        eq("1e-3", "0.001", MathEngine.DEG);
        eq("4.023872601e2567", "4.023872601e2567", MathEngine.DEG);
        eq("3.369538562e-9", "3.369538562e-9", MathEngine.DEG);
        eq("2e", "5.43656366...", MathEngine.DEG);
        eq("e^2", "7.3890561...", MathEngine.DEG);

        System.out.println("--- 回归：0^0 与负底数分数次方 ---");
        eq("0^0", "1", MathEngine.DEG);
        eq("0^5", "0", MathEngine.DEG);
        eq("(-8)^(1÷3)", "-2", MathEngine.DEG);
        eq("(-8)^(2÷3)", "4", MathEngine.DEG);
        err("(-8)^(1÷2)");
        eq("(-2)^3", "-8", MathEngine.DEG);
        eq("(-2)^2", "4", MathEngine.DEG);

        System.out.println("--- 回归：多层嵌套括号 ---");
        eq("((1+2))", "3", MathEngine.DEG);
        eq("(((1+2)))", "3", MathEngine.DEG);
        eq("((((((((1+2))))))))", "3", MathEngine.DEG);
        eq("((1+2)×(3+4))", "21", MathEngine.DEG);
        eq("2×((3+4)×(5-2))", "42", MathEngine.DEG);
        eq("(((2+3)×(4-1))-5)÷2", "5", MathEngine.DEG);
        eq("((1+(2×(3+(4×5)))))", "47", MathEngine.DEG);
        eq("((((1+1))))^2", "4", MathEngine.DEG);
        eq("sin((((30))))", "0.5", MathEngine.DEG);
        eq("((((2))))!", "2", MathEngine.DEG);
        eq("((((((((((((((((((((1+1))))))))))))))))))))", "2", MathEngine.DEG);
        // 少写右括号不再报错：按算式结束的位置自动补齐（需求如此）
        eq("(1+2", "3", MathEngine.DEG);
        eq("((1+2)", "3", MathEngine.DEG);
        eq("sin(30", "0.5", MathEngine.DEG);
        eq("2×(3+", "6", MathEngine.DEG);
        eq("sin(30+cos(60", "0.50753836...", MathEngine.DEG);
        // 多写右括号仍然要报错
        err("(1+2))");

        System.out.println("--- 回归：Ans（上一题答案常量 ans） ---");
        eq("2+3", "5", MathEngine.DEG);
        eq("ans", "5", MathEngine.DEG);
        eq("ans×4", "20", MathEngine.DEG);
        eq("2ans", "40", MathEngine.DEG);        // 隐式乘法
        eq("ans^2", "1600", MathEngine.DEG);
        eq("ans+ans", "3200", MathEngine.DEG);
        eq("sin(30)+ans", "3200.5", MathEngine.DEG);

        System.out.println("--- 回归：反双曲函数 ---");
        eq("sinh(0)", "0", MathEngine.DEG);
        eq("asinh(0)", "0", MathEngine.DEG);
        eq("asinh(1)", "0.88137359...", MathEngine.DEG);
        eq("acosh(1)", "0", MathEngine.DEG);
        eq("acosh(2)", "1.3169579...", MathEngine.DEG);
        eq("atanh(0)", "0", MathEngine.DEG);
        eq("atanh(0.5)", "0.54930614...", MathEngine.DEG);
        eq("sinh(asinh(3))", "3", MathEngine.DEG);
        err("acosh(0)");
        err("atanh(1)");

        System.out.println("--- 回归：极小值绝不能显示成 0 ---");
        // 曾经 tidy() 里的 setScale(...,HALF_UP) 会把 3.4e-9 这类值直接舍成 0，
        // 屏幕上就出现「明明算出了结果却显示 0」
        eq("3.369538562e-9", "3.369538562e-9", MathEngine.DEG);
        eq("1e-9", "1e-9", MathEngine.DEG);
        eq("1e-9×2", "2e-9", MathEngine.DEG);
        eq("sin(0.00000001)", "1.745329252e-10", MathEngine.DEG);
        eq("0.0000001", "0.0000001", MathEngine.DEG);
        eq("0.00000001", "1e-8", MathEngine.DEG);
        eq("1e-30", "1e-30", MathEngine.DEG);
        // 正常的小数不受影响
        eq("1÷3", "0.33333333...", MathEngine.DEG);
        eq("0.5", "0.5", MathEngine.DEG);
        eq("cos(95)", "-0.08715574...", MathEngine.DEG);

        System.out.println("--- 回归：阶乘/幂的 int 溢出不再变成错误答案 ---");
        err("4294967296!");
        err("4294967297!");
        err("2147483648!");
        eq("20!", "2432902008176640000", MathEngine.DEG);

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------
    // 断言工具
    // ------------------------------------------------------------------

    /** 期望值与实际结果一致。expected 为 null 时只要求不抛异常。 */
    private static void eq(String expression, String expected, int mode) {
        String actual;
        try {
            actual = MathEngine.calculate(expression, mode);
        } catch (MathEngine.MathException e) {
            fail(expression + "  ->  意外报错：" + e.getMessage());
            return;
        } catch (Throwable t) {
            fail(expression + "  ->  崩溃：" + t);
            return;
        }
        if (expected == null) {
            ok(expression + " = " + actual);
            return;
        }
        if (expected.endsWith("...")) {
            String prefix = expected.substring(0, expected.length() - 3);
            if (actual.startsWith(prefix)) ok(expression + " = " + actual);
            else fail(expression + "  ->  得到 " + actual + "，期望以 " + prefix + " 开头");
            return;
        }
        if (expected.equals(actual)) {
            ok(expression + " = " + actual);
            return;
        }
        // 数值上相等也放过（例如 0.5 与 .5）
        try {
            if (new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0) {
                ok(expression + " = " + actual);
                return;
            }
        } catch (NumberFormatException ignored) {
            // 落到下面的失败分支
        }
        fail(expression + "  ->  得到 " + actual + "，期望 " + expected);
    }

    /** 结果必须使用科学计数法。 */
    private static void sci(String expression) {
        try {
            String actual = MathEngine.calculate(expression, MathEngine.DEG);
            if (actual.contains("e")) ok(expression + " 用科学计数法显示：" + actual);
            else fail(expression + "  ->  " + actual + "，期望科学计数法");
        } catch (Throwable t) {
            fail(expression + "  ->  崩溃：" + t);
        }
    }

    /** 两个表达式在同一模式下的结果必须不同。 */
    private static void ne(String a, String b, int mode) {
        try {
            String ra = MathEngine.calculate(a, mode);
            String rb = MathEngine.calculate(b, mode);
            if (!ra.equals(rb)) ok(a + "(" + ra + ") ≠ " + b + "(" + rb + ")");
            else fail(a + " 与 " + b + " 结果相同（" + ra + "），DEG/RAD 切换可能没生效");
        } catch (Throwable t) {
            fail(a + " / " + b + "  ->  崩溃：" + t);
        }
    }

    /** 必须抛出 MathException。 */
    private static void err(String expression) {
        try {
            String actual = MathEngine.calculate(expression, MathEngine.DEG);
            fail(expression + "  ->  本该报错，却算出了 " + actual);
        } catch (MathEngine.MathException e) {
            ok(expression + " 正确报错：" + e.getMessage());
        } catch (Throwable t) {
            fail(expression + "  ->  抛出了意料之外的异常：" + t);
        }
    }

    private static void ok(String message) {
        passed++;
        System.out.println("  [OK]   " + message);
    }

    private static void fail(String message) {
        failed++;
        System.out.println("  [FAIL] " + message);
    }
}
