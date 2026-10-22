package ::APP_PACKAGE::;

import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;

/** The game runs separately so a native crash cannot close the report screen. */
public class MainActivity extends org.haxe.lime.GameActivity {
    private static File startupLog;

    @Override protected void onCreate(Bundle state) {
        startupLog = new File(getFilesDir(), "startup-current.txt");
        File previous = new File(getFilesDir(), "startup-previous.txt");
        if (startupLog.exists()) {
            previous.delete();
            startupLog.renameTo(previous);
        }
        mark("Java onCreate; pid=" + android.os.Process.myPid());
        final Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            mark("Java uncaught error on " + thread.getName() + ":\n" + Log.getStackTraceString(error));
            if (original != null) original.uncaughtException(thread, error);
            else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
        super.onCreate(state);
        mark("Java onCreate complete; native libraries broken=" + mBrokenLibraries);
    }

    @Override public void loadLibraries() {
        for (String library : getLibraries()) {
            mark("Loading native library: " + library);
            try {
                org.libsdl.app.SDL.loadLibrary(library);
            } catch (RuntimeException | UnsatisfiedLinkError error) {
                mark("Native library error: " + Log.getStackTraceString(error));
                throw error;
            }
        }
        mark("All native libraries loaded");
    }

    public static synchronized void mark(String message) {
        Log.i("MarioStartup", message);
        if (startupLog == null || startupLog.length() > 131072) return;
        try (FileOutputStream output = new FileOutputStream(startupLog, true)) {
            output.write((new java.util.Date() + " " + message + "\n").getBytes("UTF-8"));
        } catch (Exception error) {
            Log.w("MarioStartup", "Could not save startup report", error);
        }
    }
}
