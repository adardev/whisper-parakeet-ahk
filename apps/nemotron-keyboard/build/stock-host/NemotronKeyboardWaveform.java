package com.samsung.android.honeyboard.nemotron;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Replaces HoneyBoard's spacebar language label with Nemotron's live voice bars. */
public final class NemotronKeyboardWaveform {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern LANGUAGE = Pattern.compile("^[\\p{L}][\\p{L} .-]*\\s*\\([\\p{L}-]+\\)$");
    private static WeakReference<View> keyboard = new WeakReference<>(null);
    private static final List<LabelSlot> labels = new ArrayList<>();
    private static Waveform indicator;
    private static float tapX;

    private NemotronKeyboardWaveform() {}

    public static void bind(View view, float x) {
        clear();
        keyboard = new WeakReference<>(view);
        tapX = x;
    }

    public static void setRecording(boolean recording) {
        MAIN.post(() -> {
            clearNow();
            View root = keyboard.get();
            if (!recording || root == null || !root.isAttachedToWindow()) return;
            int[] origin = new int[2];
            root.getLocationOnScreen(origin);
            List<TextView> candidates = new ArrayList<>();
            findLanguageLabels(root, candidates);
            candidates.sort((a, b) -> Float.compare(Math.abs(centerX(a) - tapX), Math.abs(centerX(b) - tapX)));
            if (candidates.isEmpty()) {
                Log.d("NemotronKeyboardWaveform", "HoneyBoard spacebar language label not found");
                return;
            }
            List<Rect> areas = new ArrayList<>();
            for (TextView label : candidates) {
                int[] position = new int[2];
                label.getLocationOnScreen(position);
                Rect area = new Rect(position[0] - origin[0], position[1] - origin[1],
                        position[0] - origin[0] + label.getWidth(), position[1] - origin[1] + label.getHeight());
                if (area.exactCenterY() <= root.getHeight() * .65f || area.width() <= area.height()) continue;
                labels.add(new LabelSlot(label, label.getAlpha()));
                areas.add(area);
                label.setAlpha(0f);
            }
            if (areas.isEmpty()) return;
            Rect region = new Rect(areas.get(0));
            for (int i = 1; i < areas.size(); i++) region.union(areas.get(i));
            indicator = new Waveform(root.getResources().getDisplayMetrics().density, areas);
            indicator.setBounds(region);
            root.getOverlay().add(indicator);
            indicator.start();
        });
    }

    public static void setLevel(float rms) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (indicator != null) indicator.setLevel(rms);
        } else {
            MAIN.post(() -> { if (indicator != null) indicator.setLevel(rms); });
        }
    }

    private static void clear() { MAIN.post(NemotronKeyboardWaveform::clearNow); }

    private static void clearNow() {
        Waveform old = indicator;
        indicator = null;
        if (old != null) {
            old.stop();
            View root = keyboard.get();
            if (root != null) root.getOverlay().remove(old);
        }
        for (LabelSlot slot : labels) {
            TextView view = slot.view.get();
            if (view != null) view.setAlpha(slot.alpha);
        }
        labels.clear();
    }

    private static float centerX(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location[0] + view.getWidth() / 2f;
    }

    private static void findLanguageLabels(View view, List<TextView> result) {
        if (!view.isShown()) return;
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && LANGUAGE.matcher(value.toString().trim()).matches()) result.add((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) findLanguageLabels(group.getChildAt(i), result);
        }
    }

    private static final class LabelSlot {
        final WeakReference<TextView> view;
        final float alpha;
        LabelSlot(TextView view, float alpha) { this.view = new WeakReference<>(view); this.alpha = alpha; }
    }

    private static final class Waveform extends Drawable {
        private final float density;
        private final List<Rect> areas;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float level = .15f;
        private float phase;
        private boolean running;
        private final Runnable frame = new Runnable() {
            @Override public void run() {
                if (!running) return;
                phase += .24f;
                invalidateSelf();
                scheduleSelf(this, SystemClock.uptimeMillis() + 45L);
            }
        };

        Waveform(float density, List<Rect> areas) {
            this.density = density;
            this.areas = areas;
            paint.setColor(0xffffffff);
            paint.setStrokeWidth(3f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void start() { running = true; scheduleSelf(frame, SystemClock.uptimeMillis() + 45L); }
        void stop() { running = false; unscheduleSelf(frame); }
        void setLevel(float rms) {
            level = Math.max(.12f, Math.min(1f, (rms + 2f) / 14f));
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            for (Rect area : areas) {
                float cluster = Math.min(100f * density, area.width() - 16f * density);
                if (cluster <= 9f * paint.getStrokeWidth()) continue;
                float gap = (cluster - paint.getStrokeWidth()) / 8f;
                float start = area.exactCenterX() - cluster / 2f + paint.getStrokeWidth() / 2f;
                float center = area.exactCenterY();
                float maxHeight = Math.min(24f * density, area.height() * .62f);
                for (int i = 0; i < 9; i++) {
                    float wave = .45f + .55f * (float) Math.abs(Math.sin(phase + i * .8f));
                    float height = Math.min(maxHeight * .88f, maxHeight * (.18f + .62f * level * wave));
                    float x = start + gap * i;
                    canvas.drawLine(x, center - height / 2f, x, center + height / 2f, paint);
                }
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @SuppressWarnings("deprecation")
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
