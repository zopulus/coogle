package androidx.activity;
public class ComponentActivity extends android.app.Activity {
    public static final java.util.concurrent.BlockingQueue<Runnable> ui = new java.util.concurrent.LinkedBlockingQueue<>();
    public boolean destroyed;
    public boolean isDestroyed() { return destroyed; }
    public void runOnUiThread(Runnable runnable) { ui.add(runnable); }
}
