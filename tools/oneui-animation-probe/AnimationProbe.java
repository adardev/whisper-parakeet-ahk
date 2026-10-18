import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import java.lang.reflect.*;

/** Temporary shell-side diagnostic. Never changes the Recents component. */
public final class AnimationProbe {
    static Object service(String name, String stub) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        return Class.forName(stub).getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    static final class Runner extends Binder {
        Runner() { attachInterface(null, "android.view.IRemoteAnimationRunner"); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                if (code == INTERFACE_TRANSACTION) {
                    reply.writeString("android.view.IRemoteAnimationRunner");
                    return true;
                }
                data.enforceInterface("android.view.IRemoteAnimationRunner");
                System.out.println("RUNNER callback=" + code);
                if (code == 1) {
                    int transit = data.readInt();
                    Parcelable.Creator creator = (Parcelable.Creator) Class
                            .forName("android.view.RemoteAnimationTarget").getField("CREATOR").get(null);
                    for (int i = 0; i < 3; i++) {
                        Object[] targets = data.createTypedArray(creator);
                        System.out.println("transit=" + transit + " targets[" + i + "]="
                                + (targets == null ? 0 : targets.length));
                    }
                    IBinder finish = data.readStrongBinder();
                    if (finish != null) {
                        Parcel p = Parcel.obtain();
                        try {
                            p.writeInterfaceToken("android.view.IRemoteAnimationFinishedCallback");
                            finish.transact(1, p, null, IBinder.FLAG_ONEWAY);
                        } finally { p.recycle(); }
                    }
                }
                return true;
            } catch (Throwable error) { error.printStackTrace(); return false; }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("uid=" + android.os.Process.myUid());
        Object wm = service("window", "android.view.IWindowManager$Stub");
        Object atm = service("activity_task", "android.app.IActivityTaskManager$Stub");
        for (Object svc : new Object[] {wm, atm}) {
            for (Method m : svc.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if (n.contains("remoteanimation") || n.contains("recents")
                        || n.contains("transitionplayer") || n.contains("activityclientcontroller"))
                    System.out.println(m.toString());
            }
        }
        if (args.length == 0 || !args[0].equals("--next")) return;
        Looper.prepareMainLooper();
        final Runner binder = new Runner();
        Class<?> iface = Class.forName("android.view.IRemoteAnimationRunner");
        Object runner = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface},
                (object, method, values) -> {
                    if (method.getName().equals("asBinder")) return binder;
                    if (method.getName().equals("toString")) return "NemotronAnimationProbe";
                    return null;
                });
        Class<?> adapterClass = Class.forName("android.view.RemoteAnimationAdapter");
        Object adapter = adapterClass.getConstructor(iface, long.class, long.class)
                .newInstance(runner, 250L, 0L);
        atm.getClass().getMethod("registerRemoteAnimationForNextActivityStart",
                        String.class, adapterClass, IBinder.class)
                .invoke(atm, args.length > 1 ? args[1] : "com.sec.android.app.launcher", adapter, new Binder());
        System.out.println("ARMED one pending transition; timeout=15s");
        new Handler(Looper.getMainLooper()).postDelayed(() -> System.exit(0), 15000);
        Looper.loop();
    }
}
