package android.view;
import android.os.Looper;
/** Compile-only stub. Never packaged in the APK. */
public abstract class InputEventReceiver {
    public InputEventReceiver(InputChannel channel, Looper looper) {}
    public void onInputEvent(InputEvent event) {}
    public final void finishInputEvent(InputEvent event, boolean handled) {}
    public void dispose() {}
}
