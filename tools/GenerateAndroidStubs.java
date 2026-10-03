import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 生成一份最小化的 Android API 桩（stub），用于在没有 Android SDK / Gradle 的环境下
 * 把 MainActivity / LogBubbleView / MathEngine 编译一遍，提前抓出 Java 语法与签名错误。
 *
 * 注意：这只是编译期检查，不能替代真机运行；签名是按本项目实际用到的成员写的。
 */
public final class GenerateAndroidStubs {

    private static final Map<String, String> CLASSES = new LinkedHashMap<>();
    private static final List<String> OUT = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Path outRoot = Paths.get(args.length > 0 ? args[0] : "build/android-stubs");
        if (Files.exists(outRoot)) deleteRecursively(outRoot);
        Files.createDirectories(outRoot);

        for (Map.Entry<String, String> e : CLASSES.entrySet()) {
            Path file = outRoot.resolve(e.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.write(file, e.getValue().getBytes(StandardCharsets.UTF_8));
            OUT.add(file.toString());
        }
        System.out.println("生成 " + OUT.size() + " 个桩文件到 " + outRoot.toAbsolutePath());
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
    }

    private static void put(String name, String body) {
        CLASSES.put(name, body);
    }

    static {
        // ------------------------------------------------------------------
        // android 基础类
        // ------------------------------------------------------------------
        put("android.content.SharedPreferences", "package android.content;\n"
                + "public interface SharedPreferences {\n"
                + "  String getString(String key, String defValue);\n"
                + "  boolean getBoolean(String key, boolean defValue);\n"
                + "  int getInt(String key, int defValue);\n"
                + "  Editor edit();\n"
                + "  interface Editor {\n"
                + "    Editor putString(String key, String value);\n"
                + "    Editor putBoolean(String key, boolean value);\n"
                + "    Editor putInt(String key, int value);\n"
                + "    boolean commit();\n"
                + "    void apply();\n"
                + "  }\n"
                + "}\n");

        put("android.content.Context", "package android.content;\n"
                + "public class Context {\n"
                + "  public static final int MODE_PRIVATE = 0;\n"
                + "  public Object getSystemService(String name) { return null; }\n"
                + "  public android.content.res.Resources getResources() { return null; }\n"
                + "  public android.content.res.Resources.Theme getTheme() { return null; }\n"
                + "  public SharedPreferences getSharedPreferences(String name, int mode) { return null; }\n"
                + "  public String getPackageName() { return null; }\n"
                + "}\n");

        put("android.content.res.Resources", "package android.content.res;\n"
                + "public class Resources {\n"
                + "  public DisplayMetrics getDisplayMetrics() { return null; }\n"
                + "  public Configuration getConfiguration() { return null; }\n"
                + "  public String getString(int id) { return null; }\n"
                + "  public static class Theme { }\n"
                + "}\n");

        put("android.content.res.Configuration", "package android.content.res;\n"
                + "public class Configuration {\n"
                + "  public boolean isScreenRound() { return false; }\n"
                + "  public int orientation;\n"
                + "  public int screenWidthDp;\n"
                + "  public int screenHeightDp;\n"
                + "}\n");

        put("android.content.res.DisplayMetrics", "package android.content.res;\n"
                + "public class DisplayMetrics {\n"
                + "  public int widthPixels;\n"
                + "  public int heightPixels;\n"
                + "  public float density;\n"
                + "  public int densityDpi;\n"
                + "}\n");

        put("android.app.Activity", "package android.app;\n"
                + "public class Activity extends android.content.Context {\n"
                + "  protected void onCreate(android.os.Bundle b) { }\n"
                + "  protected void onStart() { }\n"
                + "  protected void onResume() { }\n"
                + "  protected void onPause() { }\n"
                + "  protected void onStop() { }\n"
                + "  protected void onDestroy() { }\n"
                + "  protected void onSaveInstanceState(android.os.Bundle b) { }\n"
                + "  public void setContentView(int layoutResId) { }\n"
                + "  public void setContentView(android.view.View v) { }\n"
                + "  public <T extends android.view.View> T findViewById(int id) { return null; }\n"
                + "  public boolean onLongClick(android.view.View v) { return false; }\n"
                + "}\n");

        put("android.os.Bundle", "package android.os;\n"
                + "public class Bundle {\n"
                + "  public void putString(String k, String v) { }\n"
                + "  public String getString(String k) { return null; }\n"
                + "}\n");

        put("android.os.Build", "package android.os;\n"
                + "public class Build {\n"
                + "  /** 真实 Android 里 VERSION 是 Build 的静态嵌套类。 */\n"
                + "  public static class VERSION {\n"
                + "    public static final int SDK_INT = 30;\n"
                + "  }\n"
                + "  public static class VERSION_CODES {\n"
                + "    public static final int LOLLIPOP = 21;\n"
                + "    public static final int M = 23;\n"
                + "    public static final int N = 24;\n"
                + "    public static final int O = 26;\n"
                + "    public static final int R = 30;\n"
                + "  }\n"
                + "}\n");

        put("android.R", "package android;\n"
                + "public final class R {\n"
                + "  public static final class id {\n"
                + "    public static final int content = 16908290;\n"
                + "  }\n"
                + "  public static final class layout {\n"
                + "    public static final int simple_list_item_1 = 17367043;\n"
                + "  }\n"
                + "}\n");

        put("android.os.Handler", "package android.os;\n"
                + "public class Handler {\n"
                + "  public Handler(android.os.Looper l) { }\n"
                + "  public boolean post(Runnable r) { return true; }\n"
                + "  public boolean postDelayed(Runnable r, long d) { return true; }\n"
                + "  public void removeCallbacks(Runnable r) { }\n"
                + "}\n");

        put("android.os.Looper", "package android.os;\n"
                + "public class Looper {\n"
                + "  public static Looper getMainLooper() { return null; }\n"
                + "}\n");

        put("android.animation.ObjectAnimator", "package android.animation;\n"
                + "public class ObjectAnimator extends android.animation.ValueAnimator {\n"
                + "  public static ObjectAnimator ofFloat(Object target, String prop, float... values) { return null; }\n"
                + "}\n");

        put("android.animation.ValueAnimator", "package android.animation;\n"
                + "public class ValueAnimator {\n"
                + "  public void start() { }\n"
                + "  public void cancel() { }\n"
                + "  public void end() { }\n"
                + "  public boolean isRunning() { return false; }\n"
                + "  public ValueAnimator setDuration(long d) { return this; }\n"
                + "  public ValueAnimator setStartDelay(long d) { return this; }\n"
                + "  public ValueAnimator setRepeatCount(int c) { return this; }\n"
                + "  public ValueAnimator setInterpolator(android.animation.TimeInterpolator i) { return this; }\n"
                + "  public ValueAnimator addUpdateListener(AnimatorUpdateListener l) { return this; }\n"
                + "  public interface AnimatorUpdateListener { void onAnimationUpdate(ValueAnimator a); }\n"
                + "}\n");

        put("android.animation.TimeInterpolator", "package android.animation;\n"
                + "public interface TimeInterpolator { float getInterpolation(float t); }\n");

        put("android.view.animation.Interpolator", "package android.view.animation;\n"
                + "public interface Interpolator extends android.animation.TimeInterpolator { }\n");

        put("android.view.animation.DecelerateInterpolator", "package android.view.animation;\n"
                + "public class DecelerateInterpolator implements Interpolator {\n"
                + "  public DecelerateInterpolator() { }\n"
                + "  public DecelerateInterpolator(float factor) { }\n"
                + "  public float getInterpolation(float t) { return t; }\n"
                + "}\n");

        put("android.view.animation.OvershootInterpolator", "package android.view.animation;\n"
                + "public class OvershootInterpolator implements Interpolator {\n"
                + "  public OvershootInterpolator() { }\n"
                + "  public OvershootInterpolator(float tension) { }\n"
                + "  public float getInterpolation(float t) { return t; }\n"
                + "}\n");

        put("android.view.animation.LinearInterpolator", "package android.view.animation;\n"
                + "public class LinearInterpolator implements Interpolator {\n"
                + "  public float getInterpolation(float t) { return t; }\n"
                + "}\n");

        put("android.util.AttributeSet", "package android.util;\npublic interface AttributeSet { }\n");

        put("android.util.Log", "package android.util;\n"
                + "public class Log {\n"
                + "  public static int v(String tag, String msg) { return 0; }\n"
                + "  public static int d(String tag, String msg) { return 0; }\n"
                + "  public static int i(String tag, String msg) { return 0; }\n"
                + "  public static int w(String tag, String msg) { return 0; }\n"
                + "  public static int e(String tag, String msg) { return 0; }\n"
                + "}\n");

        put("android.util.TypedValue", "package android.util;\n"
                + "public class TypedValue {\n"
                + "  public static final int COMPLEX_UNIT_PX = 0;\n"
                + "  public static final int COMPLEX_UNIT_DIP = 1;\n"
                + "  public static final int COMPLEX_UNIT_SP = 2;\n"
                + "  public static float applyDimension(int unit, float value, android.content.res.DisplayMetrics m) { return value; }\n"
                + "}\n");

        put("android.view.View", "package android.view;\n"
                + "public class View {\n"
                + "  public static final int VISIBLE = 0;\n"
                + "  public static final int INVISIBLE = 4;\n"
                + "  public static final int GONE = 8;\n"
                + "  public static final int FOCUS_RIGHT = 66;\n" + "  public static final int FOCUS_DOWN = 130;\n" + "  public static final int FOCUS_UP = 33;\n"
                + "  public View(android.content.Context c) { }\n"
                + "  public View(android.content.Context c, android.util.AttributeSet a) { }\n"
                + "  public android.content.Context getContext() { return null; }\n"
                + "  public android.content.res.Resources getResources() { return null; }\n"
                + "  public void setOnClickListener(OnClickListener l) { }\n"
                + "  public void setOnLongClickListener(OnLongClickListener l) { }\n"
                + "  public void setOnTouchListener(OnTouchListener l) { }\n"
                + "  public void setOnGenericMotionListener(OnGenericMotionListener l) { }\n"
                + "  public void setBackgroundResource(int id) { }\n"
                + "  public void setBackgroundColor(int color) { }\n"
                + "  public void setClickable(boolean b) { }\n"
                + "  public void setSelected(boolean s) { }\n"
                + "  public boolean isSelected() { return false; }\n"
                + "  public void setFocusable(boolean b) { }\n"
                + "  public void setFocusableInTouchMode(boolean b) { }\n"
                + "  public void setVisibility(int v) { }\n"
                + "  public int getVisibility() { return 0; }\n"
                + "  public void setAlpha(float a) { }\n"
                + "  public void setScaleX(float s) { }\n"
                + "  public void setScaleY(float s) { }\n"
                + "  public void setPivotX(float p) { }\n"
                + "  public void setPivotY(float p) { }\n"
                + "  public void setX(float x) { }\n"
                + "  public void setY(float y) { }\n"
                + "  public float getX() { return 0f; }\n"
                + "  public float getY() { return 0f; }\n"
                + "  public int getTop() { return 0; }\n"
                + "  public int getBottom() { return 0; }\n"
                + "  public int getLeft() { return 0; }\n"
                + "  public int getRight() { return 0; }\n"
                + "  public int getWidth() { return 0; }\n"
                + "  public int getHeight() { return 0; }\n"
                + "  public int getMeasuredWidth() { return 0; }\n"
                + "  public int getMeasuredHeight() { return 0; }\n"
                + "  public <T extends View> T findViewById(int id) { return null; }\n"
                + "  public void setLayoutParams(ViewGroup.LayoutParams p) { }\n"
                + "  public ViewGroup.LayoutParams getLayoutParams() { return null; }\n"
                + "  public View getParent() { return null; }\n"
                + "  public boolean performHapticFeedback(int constant) { return true; }\n"
                + "  public boolean post(Runnable r) { return true; }\n"
                + "  public boolean postDelayed(Runnable r, long d) { return true; }\n"
                + "  public void requestFocus() { }\n"
                + "  public void scrollBy(int x, int y) { }\n"
                + "  public void setElevation(float e) { }\n"
                + "  public void setPadding(int l, int t, int r, int b) { }\n"
                + "  public int getPaddingTop() { return 0; }\n"
                + "  public int getPaddingBottom() { return 0; }\n"
                + "  public int getPaddingLeft() { return 0; }\n"
                + "  public int getPaddingRight() { return 0; }\n"
                + "  public void setClipToPadding(boolean b) { }\n"
                + "  public void getLocationInWindow(int[] outLocation) { }\n"
                + "  public void getLocationOnScreen(int[] outLocation) { }\n"
                + "  public ViewPropertyAnimator animate() { return null; }\n"
                + "  protected void onMeasure(int w, int h) { }\n"
                + "  protected void onLayout(boolean c, int l, int t, int r, int b) { }\n"
                + "  public interface OnClickListener { void onClick(View v); }\n"
                + "  public interface OnLongClickListener { boolean onLongClick(View v); }\n"
                + "  public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); }\n"
                + "  public interface OnGenericMotionListener { boolean onGenericMotion(View v, MotionEvent e); }\n"
                + "}\n");

        put("android.view.ViewPropertyAnimator", "package android.view;\n"
                + "public class ViewPropertyAnimator {\n"
                + "  public ViewPropertyAnimator alpha(float a) { return this; }\n"
                + "  public ViewPropertyAnimator scaleX(float s) { return this; }\n"
                + "  public ViewPropertyAnimator scaleY(float s) { return this; }\n"
                + "  public ViewPropertyAnimator setDuration(long d) { return this; }\n"
                + "  public ViewPropertyAnimator setInterpolator(android.animation.TimeInterpolator i) { return this; }\n"
                + "  public ViewPropertyAnimator withEndAction(Runnable r) { return this; }\n"
                + "  public void start() { }\n"
                + "  public void cancel() { }\n"
                + "}\n");

        put("android.view.ViewGroup", "package android.view;\n"
                + "public class ViewGroup extends View {\n"
                + "  public ViewGroup(android.content.Context c) { super(c); }\n"
                + "  public ViewGroup(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void addView(View v) { }\n"
                + "  public void addView(View v, int index) { }\n"
                + "  public void addView(View v, LayoutParams p) { }\n"
                + "  public void removeView(View v) { }\n"
                + "  public int getChildCount() { return 0; }\n"
                + "  public View getChildAt(int i) { return null; }\n"
                + "  public void removeAllViews() { }\n"
                + "  public void setClipChildren(boolean b) { }\n"
                + "  public void bringChildToFront(View v) { }\n"
                + "  public static class LayoutParams {\n"
                + "    public static final int MATCH_PARENT = -1;\n"
                + "    public static final int WRAP_CONTENT = -2;\n"
                + "    public int width;\n"
                + "    public int height;\n"
                + "    public LayoutParams(int w, int h) { width = w; height = h; }\n"
                + "  }\n"
                + "  public static class MarginLayoutParams extends LayoutParams {\n"
                + "    public int leftMargin;\n"
                + "    public int topMargin;\n"
                + "    public int rightMargin;\n"
                + "    public int bottomMargin;\n"
                + "    public MarginLayoutParams(int w, int h) { super(w, h); }\n"
                + "  }\n"
                + "  public static class MeasureSpec {\n"
                + "    public static final int UNSPECIFIED = 0;\n"
                + "    public static final int EXACTLY = 1073741824;\n"
                + "    public static final int AT_MOST = -2147483648;\n"
                + "    public static int makeMeasureSpec(int size, int mode) { return size + mode; }\n"
                + "    public static int getSize(int spec) { return spec; }\n"
                + "    public static int getMode(int spec) { return spec; }\n"
                + "  }\n"
                + "}\n");

        put("android.view.MotionEvent", "package android.view;\n"
                + "public class MotionEvent {\n"
                + "  public static final int ACTION_DOWN = 0;\n"
                + "  public static final int ACTION_UP = 1;\n"
                + "  public static final int ACTION_MOVE = 2;\n"
                + "  public static final int ACTION_CANCEL = 3;\n"
                + "  public static final int ACTION_SCROLL = 8;\n"
                + "  public static final int AXIS_SCROLL = 9;\n" + "  public static final int AXIS_HSCROLL = 10;\n"
                + "  public static final int AXIS_VSCROLL = 9;\n"
                + "  public int getAction() { return 0; }\n"
                + "  public int getActionMasked() { return 0; }\n"
                + "  public float getAxisValue(int axis) { return 0f; }\n"
                + "  public float getX() { return 0f; }\n"
                + "  public float getY() { return 0f; }\n"
                + "  public int getPointerCount() { return 1; }\n"
                + "  public boolean isFromSource(int source) { return false; }\n"
                + "}\n");

        put("android.view.InputDevice", "package android.view;\n"
                + "public class InputDevice {\n"
                + "  public static final int SOURCE_ROTARY_ENCODER = 4194304;\n"
                + "  public static final int SOURCE_CLASS_POINTER = 2;\n"
                + "}\n");

        put("android.view.HapticFeedbackConstants", "package android.view;\n"
                + "public class HapticFeedbackConstants {\n"
                + "  public static final int CLOCK_TICK = 4;\n"
                + "  public static final int KEYBOARD_TAP = 3;\n"
                + "  public static final int LONG_PRESS = 0;\n"
                + "  public static final int CONFIRM = 16;\n"
                + "  public static final int VIRTUAL_KEY = 1;\n"
                + "}\n");

        put("android.view.LayoutInflater", "package android.view;\n"
                + "public class LayoutInflater {\n"
                + "  public static LayoutInflater from(android.content.Context c) { return null; }\n"
                + "  public View inflate(int res, ViewGroup root, boolean attach) { return null; }\n"
                + "  public View inflate(int res, ViewGroup root) { return null; }\n"
                + "}\n");

        put("android.view.Gravity", "package android.view;\n"
                + "public class Gravity {\n"
                + "  public static final int CENTER = 17;\n"
                + "  public static final int CENTER_HORIZONTAL = 1;\n"
                + "  public static final int CENTER_VERTICAL = 16;\n"
                + "  public static final int BOTTOM = 80;\n"
                + "  public static final int END = 8388613;\n"
                + "}\n");

        // ------------------------------------------------------------------
        // android.widget
        // ------------------------------------------------------------------
        put("android.widget.TextView", "package android.widget;\n"
                + "public class TextView extends android.view.View {\n"
                + "  public TextView(android.content.Context c) { super(c); }\n"
                + "  public TextView(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void setText(CharSequence t) { }\n"
                + "  public CharSequence getText() { return null; }\n"
                + "  public void setTextSize(float sp) { }\n"
                + "  public void setTextSize(int unit, float size) { }\n"
                + "  public float getTextSize() { return 0f; }\n"
                + "  public void setTextColor(int color) { }\n"
                + "  public void setSelected(boolean s) { }\n"
                + "  public void setContentDescription(CharSequence d) { }\n"
                + "  public void setSingleLine(boolean b) { }\n"
                + "  public void setGravity(int g) { }\n"
                + "  public void setAllCaps(boolean b) { }\n"
                + "  public void setIncludeFontPadding(boolean b) { }\n"
                + "  public void setMinWidth(int w) { }\n"
                + "  public void setMinHeight(int h) { }\n"
                + "}\n");

        put("android.widget.Button", "package android.widget;\n"
                + "public class Button extends TextView {\n"
                + "  public Button(android.content.Context c) { super(c); }\n"
                + "  public Button(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "}\n");

        put("android.widget.CompoundButton", "package android.widget;\n"
                + "public class CompoundButton extends Button {\n"
                + "  public CompoundButton(android.content.Context c) { super(c); }\n"
                + "  public CompoundButton(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void setChecked(boolean checked) { }\n"
                + "  public boolean isChecked() { return false; }\n"
                + "  public void setOnCheckedChangeListener(OnCheckedChangeListener l) { }\n"
                + "  public interface OnCheckedChangeListener { void onCheckedChanged(CompoundButton b, boolean checked); }\n"
                + "}\n");

        put("android.widget.Switch", "package android.widget;\n"
                + "public class Switch extends CompoundButton {\n"
                + "  public Switch(android.content.Context c) { super(c); }\n"
                + "  public Switch(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "}\n");

        put("android.widget.ScrollView", "package android.widget;\n"
                + "public class ScrollView extends android.widget.FrameLayout {\n"
                + "  public ScrollView(android.content.Context c) { super(c); }\n"
                + "  public ScrollView(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void scrollTo(int x, int y) { }\n"
                + "  public int getScrollY() { return 0; }\n"
                + "  public void fullScroll(int direction) { }\n"
                + "}\n");

        put("android.widget.LinearLayout", "package android.widget;\n"
                + "public class LinearLayout extends android.view.ViewGroup {\n"
                + "  public static final int HORIZONTAL = 0;\n"
                + "  public static final int VERTICAL = 1;\n"
                + "  public LinearLayout(android.content.Context c) { super(c); }\n"
                + "  public LinearLayout(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void setOrientation(int o) { }\n"
                + "  public void setGravity(int g) { }\n"
                + "  public void setWeightSum(float w) { }\n"
                + "  public static class LayoutParams extends android.view.ViewGroup.LayoutParams {\n"
                + "    public float weight;\n"
                + "    public int gravity;\n"
                + "    public LayoutParams(int w, int h) { super(w, h); }\n"
                + "    public LayoutParams(int w, int h, float weight) { super(w, h); this.weight = weight; }\n"
                + "  }\n"
                + "}\n");

        put("android.widget.FrameLayout", "package android.widget;\n"
                + "public class FrameLayout extends android.view.ViewGroup {\n"
                + "  public FrameLayout(android.content.Context c) { super(c); }\n"
                + "  public FrameLayout(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public static class LayoutParams extends android.view.ViewGroup.LayoutParams {\n"
                + "    public LayoutParams(int w, int h) { super(w, h); }\n"
                + "  }\n"
                + "}\n");

        put("android.widget.HorizontalScrollView", "package android.widget;\n"
                + "public class HorizontalScrollView extends android.widget.FrameLayout {\n"
                + "  public HorizontalScrollView(android.content.Context c) { super(c); }\n"
                + "  public HorizontalScrollView(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void scrollTo(int x, int y) { }\n"
                + "  public void smoothScrollTo(int x, int y) { }\n"
                + "  public int getScrollX() { return 0; }\n"
                + "  public void fullScroll(int direction) { }\n"
                + "  public void setFillViewport(boolean b) { }\n"
                + "}\n");

        // ------------------------------------------------------------------
        // androidx.wear
        // ------------------------------------------------------------------
        put("androidx.wear.widget.CurvedTextView", "package androidx.wear.widget;\n"
                + "public class CurvedTextView extends android.view.View {\n"
                + "  public CurvedTextView(android.content.Context c) { super(c); }\n"
                + "  public CurvedTextView(android.content.Context c, android.util.AttributeSet a) { super(c, a); }\n"
                + "  public void setText(CharSequence t) { }\n"
                + "  public CharSequence getText() { return null; }\n"
                + "  public void setTextSize(float sp) { }\n"
                + "  public void setTextColor(int color) { }\n"
                + "  public void setAnchorAngleDegrees(float d) { }\n"
                + "  public void setAnchorPosition(int p) { }\n"
                + "  public void setClockwise(boolean c) { }\n"
                + "}\n");
    }
}
