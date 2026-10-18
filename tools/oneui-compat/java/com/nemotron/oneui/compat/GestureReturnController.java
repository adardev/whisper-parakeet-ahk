package com.nemotron.oneui.compat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.app.ActivityManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visual return for explicitly launched icons. All snapshots stay in memory. */
public final class GestureReturnController {
    private static final String TAG = "NemotronGestureReturn";
    private static final boolean ENABLED = true;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Handler RENDER;
    static {
        HandlerThread thread=new HandlerThread("Nemotron icon renderer", android.os.Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        RENDER=new Handler(thread.getLooper());
    }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static GestureReturnController current;
    private final Context context;
    private final String pkg;
    private final RectF iconRect;
    private final int width, height;
    private final float density;
    private final boolean widget;
    private Bitmap home, icon, app;
    private Object monitor, atm, wm;
    private volatile Process rawProcess;
    private volatile Object taskObserver;
    private int rawMaxX, rawMaxY;
    private volatile ReturnView overlay;
    private volatile boolean closed;
    private volatile boolean down;
    private boolean capturing;
    private volatile boolean claimed;
    private volatile boolean finishing;
    private volatile boolean homeGestureDetected;
    private float downX, downY, lastY;
    private boolean preparing, resumed;
    private boolean nativeAnimatorMuted;
    private String savedAnimatorScale;
    private ValueAnimator preview;
    private final Runnable gestureWatchdog = () -> {
        if (!closed && down && !finishing) {
            Log.w(TAG, "gesture watchdog; clearing preview");
            down = false;
            close();
        }
    };
    private boolean isHome(Activity activity) {
        return activity.getClass().getName().equals("com.sec.android.app.launcher.Launcher")
                || activity.getClass().getName().equals("com.sec.android.app.launcher.activities.LauncherActivity");
    }
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) {
            if (isHome(activity)) {
                resumed=true;
                Log.i(TAG, "home resumed");
                if (!homeGestureDetected) { close(); return; }
                if (overlay != null && overlay.active && !finishing) {
                    if (preview != null) preview.cancel();
                    finish(true, false);
                } else fallbackReturn(false);
            }
        }
        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {
            if (!isHome(a)) return;
            Log.i(TAG, "home started");
            if (homeGestureDetected) fallbackReturn(true);
            else close(); // Key/button/ADB navigation stays entirely native.
        }
        @Override public void onActivityPaused(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) {}
    };

    private GestureReturnController(View source, String target, Drawable image, boolean widget) {
        this.widget=widget;
        context = source.getContext().getApplicationContext();
        pkg = target;
        View root = source.getRootView();
        width = root.getWidth(); height = root.getHeight();
        density = context.getResources().getDisplayMetrics().density;
        Rect bounds = new Rect();
        source.getGlobalVisibleRect(bounds);
        iconRect = new RectF(bounds);
        // Icon views can include a text label. Keep a square anchored at the top.
        if (!widget && iconRect.height() > iconRect.width()) iconRect.bottom = iconRect.top + iconRect.width();
        try {
            int size=widget ? 0 : (Integer)source.getClass().getMethod("iconSize").invoke(source);
            if (size > 0 && size < iconRect.width()) {
                float inset=(iconRect.width()-size)/2;
                iconRect.inset(inset, inset);
            }
        } catch (ReflectiveOperationException ignored) {}
        float alpha=source.getAlpha();
        source.setAlpha(0);
        try { home=drawHome(root); } finally { source.setAlpha(alpha); }
        if (widget || image == null) icon=drawHome(source);
        else {
            icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            Rect old = new Rect(image.getBounds());
            image.setBounds(0, 0, 192, 192);
            try { image.draw(new Canvas(icon)); }
            catch (RuntimeException error) { recycle(icon); icon=null; }
            image.setBounds(old);
        }
    }

    public static void arm(View source, String pkg, Drawable icon) {
        arm(source, pkg, icon, false);
    }

    /** Shared launch-options hook covers native icons, deep links and widgets. */
    public static void armInfo(Object info) {
        try {
            View source=(View)info.getClass().getMethod("getTargetView").invoke(info);
            Intent intent=(Intent)info.getClass().getMethod("getIntent").invoke(info);
            Object user=info.getClass().getMethod("getUser").invoke(info);
            if (source == null || intent == null || (user != null && !user.equals(android.os.Process.myUserHandle()))) return;
            ComponentName component=intent.getComponent();
            if (component == null) component=intent.resolveActivity(source.getContext().getPackageManager());
            if (component == null) return;
            String pkg=component.getPackageName();
            if (pkg.equals(source.getContext().getPackageName())) return;
            Drawable icon=(Drawable)info.getClass().getMethod("getIcon").invoke(info);
            String type=String.valueOf(info.getClass().getMethod("getType").invoke(info));
            boolean widget=type.toLowerCase(java.util.Locale.ROOT).contains("widget");
            arm(source, pkg, icon, widget);
        } catch (Throwable error) { Log.w(TAG, "launch source unavailable", error); }
    }

    private static void arm(View source, String pkg, Drawable icon, boolean widget) {
        if (!ENABLED) { Log.i(TAG, "arm skipped: disabled"); return; }
        if (Looper.myLooper() != Looper.getMainLooper()) { Log.i(TAG, "arm skipped: not main thread"); return; }
        if (current != null) current.close();
        if (pkg == null || pkg.equals(source.getContext().getPackageName())
                || !Settings.canDrawOverlays(source.getContext())
                || source.getRootView().getWidth() == 0) { Log.i(TAG, "arm skipped: target/window unavailable pkg=" + pkg + " overlay=" + Settings.canDrawOverlays(source.getContext()) + " width=" + source.getRootView().getWidth()); return; }
        try {
            Class<?> s = Class.forName("rikka.shizuku.Shizuku");
            boolean ping=(Boolean)s.getMethod("pingBinder").invoke(null);
            int permission=(Integer)s.getMethod("checkSelfPermission").invoke(null);
            Log.i(TAG, "Shizuku ping=" + ping + " permission=" + permission);
            if (!ping || permission != 0) return;
            GestureReturnController controller = new GestureReturnController(source, pkg, icon, widget);
            current = controller;
            ((Application)controller.context).registerActivityLifecycleCallbacks(controller.lifecycle);
            IO.execute(controller::connect);
        } catch (Throwable error) {
            Log.w(TAG, "arm unavailable", error);
            if (current != null) current.close();
        }
    }

    // Injected scrcpy/ADB input does not pass through Linux evdev. Resume gives
    // that route an independent return, without observing or replacing input.
    private void fallbackReturn(boolean starting) {
        if (closed || finishing || claimed || preparing || atm == null || wm == null) return;
        muteNativeCloseAnimation();
        preparing=true;
        IO.execute(() -> {
            Bitmap bitmap=null;
            try {
                List<?> tasks=(List<?>)atm.getClass().getMethod("getTasks", int.class, boolean.class, boolean.class, int.class)
                        .invoke(atm, 20, false, false, 0);
                for (Object task:tasks) {
                    ComponentName top=(ComponentName)task.getClass().getField("topActivity").get(task);
                    if (top == null || top.getPackageName().equals(context.getPackageName())
                            || top.getPackageName().equals("com.sec.android.app.launcher")) continue;
                    if (pkg.equals(top.getPackageName())) {
                        bitmap=(Bitmap)wm.getClass().getMethod("snapshotTaskForRecents", int.class)
                                .invoke(wm, task.getClass().getField("taskId").getInt(task));
                    }
                    break; // Never animate an old task after the user switched apps.
                }
            } catch (Throwable error) { Log.w(TAG, "resume snapshot unavailable", error); }
            Bitmap result=bitmap;
            MAIN.post(() -> {
                preparing=false;
                if (closed || finishing || claimed) { recycle(result); return; }
                if (result == null) { close(); return; }
                try {
                    recycle(app); app=result;
                    show(); claimed=true;
                    if (!starting || resumed) {
                        RENDER.post(() -> {
                            if (!closed && overlay != null) overlay.rect.set(width*0.12f, height*0.04f, width*0.88f, height*0.80f);
                        });
                        Log.i(TAG, "resume return");
                        finish(true, false);
                    } else {
                        Log.i(TAG, "start return backdrop");
                        preview=ValueAnimator.ofFloat(0, 1);
                        preview.setDuration(220);
                        preview.addUpdateListener(a -> {
                            if (!closed && !finishing && overlay != null) overlay.drag((Float)a.getAnimatedValue()*height*0.24f);
                            if (overlay != null) overlay.postInvalidate();
                        });
                        preview.start();
                        MAIN.postDelayed(() -> { if (!closed && !finishing && !resumed) close(); }, 650);
                    }
                } catch (Throwable error) { fail("resume overlay", error); }
            });
        });
    }

    private static Bitmap drawHome(View root) {
        try {
            RenderNode node = new RenderNode("Nemotron return backdrop");
            node.setPosition(0, 0, root.getWidth(), root.getHeight());
            Canvas recording = node.beginRecording(root.getWidth(), root.getHeight());
            root.draw(recording);
            node.endRecording();
            Bitmap b = (Bitmap)Class.forName("android.graphics.HardwareRenderer")
                    .getDeclaredMethod("createHardwareBitmap", RenderNode.class, int.class, int.class)
                    .invoke(null, node, root.getWidth(), root.getHeight());
            node.discardDisplayList();
            return b;
        } catch (Throwable ignored) {
            Bitmap b = null;
            try {
                b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(b));
                return b;
            } catch (Throwable error) { if (b != null) b.recycle(); return null; }
        }
    }

    private static IBinder wrap(IBinder binder) throws Exception {
        return (IBinder)Class.forName("rikka.shizuku.ShizukuBinderWrapper")
                .getConstructor(IBinder.class).newInstance(binder);
    }

    private static Object service(String name, String stub) throws Exception {
        IBinder binder = (IBinder)Class.forName("rikka.shizuku.SystemServiceHelper")
                .getMethod("getSystemService", String.class).invoke(null, name);
        return Class.forName(stub).getMethod("asInterface", IBinder.class).invoke(null, wrap(binder));
    }

    private void connect() {
        try {
            atm = service("activity_task", "android.app.IActivityTaskManager$Stub");
            wm = service("window", "android.view.IWindowManager$Stub");
            try {
                taskObserver=new TaskObserver();
                atm.getClass().getMethod("registerTaskStackListener", Class.forName("android.app.ITaskStackListener"))
                        .invoke(atm, taskObserver);
            } catch (Throwable error) { Log.w(TAG, "task observer unavailable", error); taskObserver=null; }
            String path = touchscreen();
            if (path == null) throw new IllegalStateException("No direct touchscreen available");
            rawProcess = shell(new String[]{"getevent", "-lt", path});
            if (closed) { rawProcess.destroy(); unregisterTaskObserver(); return; }
            Log.i(TAG, "armed " + pkg + " icon=" + iconRect + " backdrop=" + (home != null));
            Thread reader = new Thread(this::readTouches, "Nemotron return touch");
            reader.setDaemon(true); reader.start();
        } catch (Throwable error) { MAIN.post(() -> fail("Shizuku connection", error)); }
    }

    private final class TaskObserver extends TaskStackListener {
        @Override public void onTaskMovedToFront(ActivityManager.RunningTaskInfo task) {
            ComponentName top=task.topActivity;
            if (top != null && top.getPackageName().equals("com.sec.android.app.launcher")
                    && !top.getClassName().equals("com.sec.android.app.launcher.activities.LauncherActivity")
                    && !top.getClassName().equals("com.sec.android.app.launcher.Launcher")) {
                MAIN.post(GestureReturnController.this::close);
                return;
            }
            if (top != null && !top.getPackageName().equals(pkg)
                    && !top.getPackageName().equals(context.getPackageName())
                    && !top.getPackageName().equals("com.sec.android.app.launcher")) MAIN.post(() -> { if (!finishing) close(); });
        }
    }

    private void unregisterTaskObserver() {
        Object observer=taskObserver; taskObserver=null;
        if (observer != null) IO.execute(() -> {
            try { atm.getClass().getMethod("unregisterTaskStackListener", Class.forName("android.app.ITaskStackListener"))
                    .invoke(atm, observer); } catch (Throwable ignored) {}
        });
    }

    private static Process shell(String[] command) throws Exception {
        Method method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess",
                String[].class, String[].class, String.class);
        method.setAccessible(true);
        return (Process)method.invoke(null, command, null, null);
    }

    private String touchscreen() throws Exception {
        Process process = shell(new String[]{"getevent", "-lp"});
        String path = null;
        int x = 0, y = 0;
        boolean direct = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("add device ")) {
                    if (path != null && direct && x > 0 && y > 0) { rawMaxX=x; rawMaxY=y; return path; }
                    path = line.substring(line.indexOf(":") + 1).trim(); x=0; y=0; direct=false;
                }
                if (line.contains("INPUT_PROP_DIRECT")) direct=true;
                if (line.contains("ABS_MT_POSITION_X")) x = axisMax(line);
                if (line.contains("ABS_MT_POSITION_Y")) y = axisMax(line);
            }
            if (direct && x > 0 && y > 0) { rawMaxX=x; rawMaxY=y; return path; }
            return null;
        } finally { process.destroy(); }
    }

    private static int axisMax(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("max (\\d+)").matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private void readTouches() {
        boolean[] active = new boolean[16];
        boolean previous = false, suppressed = false;
        int slot=0, x=0, y=0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(rawProcess.getInputStream()))) {
            String line;
            while (!closed && (line=reader.readLine()) != null) {
                line=line.trim();
                if (line.isEmpty()) continue;
                String value = line.substring(line.lastIndexOf(' ')+1).trim();
                if (line.contains("ABS_MT_SLOT")) slot = (int)Long.parseLong(value, 16);
                else if (line.contains("ABS_MT_TRACKING_ID") && slot >= 0 && slot < active.length)
                    active[slot] = !value.equals("ffffffff");
                else if (slot == 0 && line.contains("ABS_MT_POSITION_X")) x = (int)Long.parseLong(value, 16);
                else if (slot == 0 && line.contains("ABS_MT_POSITION_Y")) y = (int)Long.parseLong(value, 16);
                else if (line.contains("SYN_REPORT")) {
                    int count=0; for (boolean a : active) if (a) count++;
                    if (count > 1) {
                        if (previous) dispatch(MotionEvent.ACTION_CANCEL, x, y);
                        previous=false; suppressed=true;
                    } else if (count == 0) {
                        if (previous) dispatch(MotionEvent.ACTION_UP, x, y);
                        previous=false; suppressed=false;
                    } else if (!suppressed && active[0]) {
                        // Ignore ordinary app touches on the reader thread;
                        // only a gesture starting at the bottom needs UI work.
                        if (!previous && y < rawMaxY*(1f-30*density/height)) {
                            suppressed=true;
                            continue;
                        }
                        dispatch(previous ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_DOWN, x, y);
                        previous=true;
                    }
                }
            }
        } catch (Throwable error) { if (!closed) Log.w(TAG, "touch source ended", error); }
        finally { MAIN.post(this::close); }
    }

    private void dispatch(int action, int x, int y) {
        float screenX = x * (width - 1f) / rawMaxX, screenY = y * (height - 1f) / rawMaxY;
        long now = android.os.SystemClock.uptimeMillis();
        RENDER.post(() -> {
            if (closed) return;
            MotionEvent event = MotionEvent.obtain(now, now, action, screenX, screenY, 0);
            try { touch(event); }
            catch (Throwable error) { fail("gesture", error); }
            finally { event.recycle(); }
        });
    }

    private void touch(MotionEvent event) throws Exception {
        if (closed || finishing) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            down = event.getY() > height - 30 * density && event.getPointerCount() == 1;
            if (!down) return;
            homeGestureDetected = false;
            MAIN.postDelayed(gestureWatchdog, 5000);
            muteNativeCloseAnimation();
            downX = event.getX(); downY = lastY = event.getY();
            capturing = true;
            IO.execute(() -> {
                Bitmap captured = null;
                try {
                    List<?> tasks = (List<?>)atm.getClass().getMethod("getTasks", int.class, boolean.class, boolean.class, int.class)
                            .invoke(atm, 1, false, false, 0);
                    if (!tasks.isEmpty()) {
                        Object task = tasks.get(0);
                        ComponentName top = (ComponentName)task.getClass().getField("topActivity").get(task);
                        if (top != null && pkg.equals(top.getPackageName())) {
                            int id = task.getClass().getField("taskId").getInt(task);
                            captured = (Bitmap)wm.getClass().getMethod("snapshotTaskForRecents", int.class).invoke(wm, id);
                        }
                    }
                } catch (Throwable error) { Log.w(TAG, "snapshot unavailable", error); }
                Bitmap result = captured;
                RENDER.post(() -> {
                    capturing = false;
                    if (closed || !down) { recycle(result); return; }
                    recycle(app); app = result;
                    if (app == null) close();
                });
            });
        } else if (action == MotionEvent.ACTION_MOVE && down) {
            lastY = event.getY();
            float dy = downY - lastY, dx = Math.abs(event.getX() - downX);
            if (dy > 18 * density && dy > dx * 1.3f) homeGestureDetected = true;
            if (!claimed && app != null && !capturing && dy > 18 * density && dy > dx * 1.3f) {
                show(); claimed = true;
                Log.i(TAG, "visual return tracking");
            }
            if (claimed) { overlay.drag(dy); overlay.postInvalidate(); }
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && down) {
            MAIN.removeCallbacks(gestureWatchdog);
            down = false;
            float dy = downY - lastY;
            boolean complete = action == MotionEvent.ACTION_UP && dy > 48 * density;
            if (!complete) {
                if (claimed || action == MotionEvent.ACTION_CANCEL) close();
                else { recycle(app); app = null; restoreNativeCloseAnimation(0); }
                return;
            }
            if (!claimed && app != null) { show(); claimed = true; }
            // The system owns the Home gesture. Animate only when its launcher
            // activity resumes; never start a second Home task from this view.
            MAIN.postDelayed(() -> { if (!closed && !finishing) close(); }, 1800);
        }
    }

    private void prepareWindow() {
        if (Looper.myLooper() != RENDER.getLooper()) { RENDER.post(this::prepareWindow); return; }
        if (closed) return;
        if (overlay != null) return;
        overlay = new ReturnView(context);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        p.alpha=0.01f; // Warm renderer without obscuring touches in the target app.
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setTitle("Nemotron icon return");
        ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).addView(overlay, p);
    }

    private void show() {
        if (Looper.myLooper() != RENDER.getLooper()) { RENDER.post(this::show); return; }
        if (closed) return;
        prepareWindow();
        overlay.active=true;
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)overlay.getLayoutParams();
        p.alpha=1f;
        ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(overlay, p);
        if (android.os.Build.VERSION.SDK_INT >= 35) overlay.setRequestedFrameRate(60f);
        overlay.postInvalidate();
    }

    private void finish(boolean complete, boolean recents) {
        if (closed || finishing) return;
        if (!complete || recents) { close(); return; }
        finishing = true;
        RENDER.post(() -> animateReturn(complete, recents));
        MAIN.postDelayed(() -> { if (!closed && finishing) close(); }, 2200);
    }

    private void animateReturn(boolean complete, boolean recents) {
        if (closed || overlay == null) return;
        RectF from = new RectF(overlay.rect);
        RectF to = complete && !recents ? iconRect : new RectF(0, 0, width, height);
        final long start = android.os.SystemClock.uptimeMillis();
        // Give the return enough time to read clearly, then let it settle into
        // the app icon with a small damped spring. Choreographer keeps every
        // frame aligned to display vsync (unlike a second ValueAnimator).
        final long duration = complete ? 620 : 360;
        final Choreographer choreographer = Choreographer.getInstance();
        final Choreographer.FrameCallback[] callback = new Choreographer.FrameCallback[1];
        callback[0] = frameTimeNanos -> {
            if (closed || overlay == null) return;
            float raw = Math.min(1f, Math.max(0f,
                    (frameTimeNanos / 1_000_000L - start) / (float) duration));
            float t = raw >= 1f ? 1f
                    : 1f - (float)(Math.exp(-11f * raw) * Math.cos(11f * raw));
            overlay.rect.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            overlay.morph = complete && !recents ? Math.max(0f, Math.min(1f, t)) : 0;
            overlay.postInvalidateOnAnimation();
            if (raw < 1f) choreographer.postFrameCallback(callback[0]);
            else {
                Log.i(TAG, "return frames=" + overlay.draws + " icon=" + iconRect);
                restoreNativeCloseAnimation(120);
                MAIN.post(GestureReturnController.this::close);
            }
        };
        choreographer.postFrameCallback(callback[0]);
    }

    // The stock Samsung close animation is driven by ValueAnimator in the
    // privileged Recents process. Temporarily setting its global duration
    // scale to zero suppresses that one gesture; our return uses Choreographer
    // directly, so it remains frame-synchronized and independent of that scale.
    private void muteNativeCloseAnimation() {
        if (nativeAnimatorMuted || closed) return;
        try { savedAnimatorScale = Float.toString(Settings.Global.getFloat(
                context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)); }
        catch (RuntimeException error) { Log.w(TAG, "cannot read animator scale", error); return; }
        if ("0.0".equals(savedAnimatorScale)) return;
        nativeAnimatorMuted = true;
        final String restore = savedAnimatorScale;
        IO.execute(() -> {
            try {
                Process process = shell(new String[]{"sh", "-c", "settings put global animator_duration_scale 0; sleep 2; settings put global animator_duration_scale " + restore});
                Thread safetyRestore = new Thread(() -> { try { process.waitFor(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }, "Nemotron animator-scale restore");
                safetyRestore.setDaemon(true);
                safetyRestore.start();
                Log.i(TAG, "suppressed stock close animator; saved=" + restore);
            } catch (Throwable error) { Log.w(TAG, "cannot suppress stock close animator", error); }
        });
    }

    private void restoreNativeCloseAnimation(long delayMs) {
        if (!nativeAnimatorMuted || savedAnimatorScale == null) return;
        nativeAnimatorMuted = false;
        final String restore = savedAnimatorScale;
        MAIN.postDelayed(() -> IO.execute(() -> {
            try {
                Process process = shell(new String[]{"settings", "put", "global", "animator_duration_scale", restore});
                process.waitFor();
                Log.i(TAG, "restored animator scale=" + restore);
            } catch (Throwable error) { Log.w(TAG, "animator scale safety restore", error); }
        }), delayMs);
    }

    private final class ReturnView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Path clip = new Path();
        final RectF rect = new RectF(0, 0, width, height);
        float morph;
        volatile boolean active;
        int draws;
        long firstDraw, lastDraw, longestGap;
        ReturnView(Context context) { super(context); setWillNotDraw(false); }
        void drag(float dy) {
            if (Looper.myLooper() != RENDER.getLooper()) { RENDER.post(() -> drag(dy)); return; }
            if (closed || finishing) return;
            float scale = Math.max(0.65f, 1 - Math.max(0, dy) / (height * 1.5f));
            float w = width * scale, h = height * scale;
            float cx = width / 2f, cy = height / 2f - dy * 0.3f;
            rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (!active || closed) return;
            long now=android.os.SystemClock.uptimeMillis();
            if (draws == 0) firstDraw=now;
            else longestGap=Math.max(longestGap, now-lastDraw);
            lastDraw=now; draws++;
            canvas.drawColor(Color.BLACK);
            if (home != null && !home.isRecycled()) canvas.drawBitmap(home, null, new RectF(0, 0, width, height), paint);
            float fade = Math.max(0, (morph - 0.60f) / 0.40f);
            int save = canvas.save();
            float radius=lerp(44*density, widget ? 20*density : rect.width()*0.25f, morph);
            clip.reset(); clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            paint.setAlpha((int)(255 * (1 - fade)));
            if (app != null && !app.isRecycled()) canvas.drawBitmap(app, null, rect, paint);
            if (icon != null && !icon.isRecycled()) {
                paint.setAlpha((int)(255 * fade)); canvas.drawBitmap(icon, null, rect, paint);
            }
            paint.setAlpha(255); canvas.restoreToCount(save);
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static void recycle(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
    private static void disposeMonitor(Object monitor) {
        try { monitor.getClass().getMethod("dispose").invoke(monitor); } catch (Throwable ignored) {}
    }
    private void fail(String where, Throwable error) { Log.w(TAG, where, error); close(); }
    private void close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(this::close); return; }
        if (closed) return;
        closed = true;
        MAIN.removeCallbacks(gestureWatchdog);
        restoreNativeCloseAnimation(0);
        if (preview != null) { preview.cancel(); preview=null; }
        if (rawProcess != null) { rawProcess.destroy(); rawProcess = null; }
        unregisterTaskObserver();
        ((Application)context).unregisterActivityLifecycleCallbacks(lifecycle);
        if (monitor != null) { disposeMonitor(monitor); monitor = null; }
        RENDER.post(() -> {
            if (overlay != null) {
                Log.i(TAG, "render draws=" + overlay.draws + " spanMs="
                        + (overlay.lastDraw-overlay.firstDraw) + " maxGapMs=" + overlay.longestGap);
                try { ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).removeViewImmediate(overlay); }
                catch (RuntimeException ignored) {}
                overlay = null;
            }
            recycle(home); recycle(icon); recycle(app); home = icon = app = null;
        });
        if (current == this) current = null;
    }
}
