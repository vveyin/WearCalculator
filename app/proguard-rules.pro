# WearCalculator (嗷呜计算器) —— R8 / ProGuard 规则

# ------------------------------------------------------------------
# 保留行号信息，release 出问题时堆栈还能定位到具体行
# ------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------
# Activity 由系统通过 AndroidManifest 反射创建，必须保留
# （布局里通过 android:onClick 之类按名字引用的东西同理）
# ------------------------------------------------------------------
-keep class com.nickwoluff.wearcalculator.MainActivity { *; }

# 布局 XML 里的自定义 View / 由视图系统按类名实例化的类
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 布局里用到的自定义 View（FunctionBubbleView 会在 XML 之外被 new，
# 但保底留着，避免以后有人在 XML 里引用被裁掉）
-keep class com.nickwoluff.wearcalculator.FunctionBubbleView { *; }
-keep class com.nickwoluff.wearcalculator.CrownScroll { *; }

# ------------------------------------------------------------------
# 计算引擎：纯静态方法调用，R8 可以放心压缩；这里只保留类名，
# 方便万一线上报错时堆栈里能认出是引擎哪一层
# ------------------------------------------------------------------
-keepnames class com.nickwoluff.wearcalculator.MathEngine

# androidx.wear 的 CurvedTextView 是从 XML 里用的，保留它的构造器
-keep class androidx.wear.widget.CurvedTextView { *; }
