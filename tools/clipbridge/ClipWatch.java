import android.os.Binder;
import android.os.Parcel;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Base64;

/**
 * Runs on the device through app_process as uid 2000 (shell).
 *
 * uid 2000 is the only unprivileged identity that holds
 * android.permission.READ_CLIPBOARD_IN_BACKGROUND on this build, so it can read the
 * clipboard while no app has focus. We subscribe to clipboard changes (no polling) and
 * print each new text to stdout as "CLIP <base64>". The AirSync app reads that pipe and
 * forwards the text over its WebSocket.
 */
public class ClipWatch {
    static final String PKG = "com.android.shell";
    static final String AIRSYNC = "com.sameerasw.airsync";
    static Object svc;
    static Class<?> clipData;
    static Method mGet, mSrc;
    static volatile String last = null;
    static final java.util.concurrent.atomic.AtomicBoolean busy =
            new java.util.concurrent.atomic.AtomicBoolean();

    static Method find(String name) {
        for (Method m : svc.getClass().getMethods()) if (m.getName().equals(name)) return m;
        return null;
    }

    static Object[] fill(Method m, Object clip) {
        Class<?>[] pt = m.getParameterTypes();
        Object[] a = new Object[pt.length];
        boolean pkgDone = false;
        for (int i = 0; i < pt.length; i++) {
            if (pt[i] == String.class) { a[i] = pkgDone ? null : PKG; pkgDone = true; }
            else if (pt[i] == int.class) a[i] = 0;
            else if (pt[i] == boolean.class) a[i] = false;
            else if (clip != null && pt[i].getName().equals("android.content.ClipData")) a[i] = clip;
            else a[i] = null;
        }
        return a;
    }

    static void log(String s) {
        System.out.println(s);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Object binder = sm.getMethod("getService", String.class).invoke(null, "clipboard");
        Class<?> stub = Class.forName("android.content.IClipboard$Stub");
        svc = stub.getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
        clipData = Class.forName("android.content.ClipData");
        mGet = find("getPrimaryClip");
        mSrc = find("getPrimaryClipSource");

        Binder cb = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (busy.compareAndSet(false, true)) {
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            try { handle(); } catch (Throwable e) { log("ERR handle " + e); }
                            finally { busy.set(false); }
                        }
                    });
                    t.setDaemon(true);
                    t.start();
                }
                return true;
            }
        };

        Class<?> iface = Class.forName("android.content.IOnPrimaryClipChangedListener");
        Object listener = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "asBinder": return cb;
                        case "hashCode": return System.identityHashCode(cb);
                        case "equals": return p == (a == null ? null : a[0]);
                        default: return "ClipWatch";
                    }
                });

        Method add = find("addPrimaryClipChangedListener");
        Class<?>[] pt = add.getParameterTypes();
        Object[] a = new Object[pt.length];
        boolean pkgDone = false;
        for (int i = 0; i < pt.length; i++) {
            if (pt[i].isInstance(listener)) a[i] = listener;
            else if (pt[i] == String.class) { a[i] = pkgDone ? null : PKG; pkgDone = true; }
            else if (pt[i] == int.class) a[i] = 0;
            else a[i] = null;
        }
        add.invoke(svc, a);
        log("READY");

        Thread.sleep(Long.MAX_VALUE);
    }

    static void handle() {
        try {
            Thread.sleep(120);
            String text = null;
            Object clip = mGet.invoke(svc, fill(mGet, null));
            if (clip != null) {
                int n = (Integer) clipData.getMethod("getItemCount").invoke(clip);
                if (n > 0) {
                    Object item = clipData.getMethod("getItemAt", int.class).invoke(clip, 0);
                    CharSequence t = (CharSequence) item.getClass().getMethod("getText").invoke(item);
                    if (t != null) text = t.toString();
                }
            }
            String src = (String) mSrc.invoke(svc, fill(mSrc, null));
            log("CHANGED src=" + src + " len=" + (text == null ? 0 : text.length()));
            if (text == null || text.isEmpty()) return;
            if (AIRSYNC.equals(src)) { log("SKIP echo from AirSync"); return; }
            if (text.equals(last)) { log("SKIP duplicate"); return; }
            last = text;
            log("CLIP " + Base64.getEncoder().encodeToString(text.getBytes("UTF-8")));
        } catch (Throwable t) {
            log("ERR " + t);
        }
    }
}
