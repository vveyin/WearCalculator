package com.nickwoluff.wearcalculator;

/**
 * 最小化的 R 桩，只为了在没有 aapt2 的环境下把 Java 代码编译一遍。
 * 这里的 id 列表必须和两个 activity_main.xml 里定义的一致。
 */
public final class R {
    public static final class id {
        public static final int curvedTime = 0x7f010001;
        public static final int hsvHistory = 0x7f010002;
        public static final int tvHistory = 0x7f010003;
        public static final int hsvDisplay = 0x7f010004;
        public static final int tvDisplay = 0x7f010005;
        public static final int btnAc = 0x7f010006;
        public static final int btnDel = 0x7f010007;
        public static final int btnMore = 0x7f010008;
        public static final int btnDiv = 0x7f010009;
        public static final int btn7 = 0x7f01000a;
        public static final int btn8 = 0x7f01000b;
        public static final int btn9 = 0x7f01000c;
        public static final int btnMul = 0x7f01000d;
        public static final int btn4 = 0x7f01000e;
        public static final int btn5 = 0x7f01000f;
        public static final int btn6 = 0x7f010010;
        public static final int btnSub = 0x7f010011;
        public static final int btn1 = 0x7f010012;
        public static final int btn2 = 0x7f010013;
        public static final int btn3 = 0x7f010014;
        public static final int btnAdd = 0x7f010015;
        public static final int btn0 = 0x7f010016;
        public static final int btnDot = 0x7f010017;
        public static final int btnEq = 0x7f010018;
        public static final int bubbleOptions = 0x7f01001a;
        public static final int keypad = 0x7f01001c;
        public static final int historyPage = 0x7f01001d;
        public static final int tvHistoryTitle = 0x7f01001e;
        public static final int svHistory = 0x7f01001f;
        public static final int llHistoryItems = 0x7f010020;
        public static final int btnHistoryRecordToggle = 0x7f010021;
        public static final int btnHistoryBack = 0x7f010022;
        public static final int tvHistoryExpression = 0x7f010023;
        public static final int tvHistoryResult = 0x7f010024;
        public static final int calculatorContent = 0x7f010030;
    }

    public static final class layout {
        public static final int activity_main = 0x7f020001;
        public static final int view_function_bubble = 0x7f020002;
        public static final int item_bubble_option = 0x7f020003;
        public static final int view_history = 0x7f020004;
        public static final int item_history_entry = 0x7f020005;
    }

    public static final class drawable {
        public static final int bg_log_bubble = 0x7f030001;
        public static final int bg_btn_accent = 0x7f030003;
        public static final int bg_btn_dark = 0x7f030004;
        public static final int bg_btn_dark_toggle = 0x7f030006;
        public static final int bg_btn_danger = 0x7f030005;
    }

    public static final class color {
        public static final int log_bubble_bg = 0x7f040001;
        public static final int log_option_selected_bg = 0x7f040002;
        public static final int log_option_text = 0x7f040003;
    }

    public static final class string {
        public static final int app_name = 0x7f050001;
    }

    public static final class style {
        public static final int Theme_WearCalculator = 0x7f060001;
    }
}
