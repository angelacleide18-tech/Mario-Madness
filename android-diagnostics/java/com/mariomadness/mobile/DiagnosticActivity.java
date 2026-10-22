package com.mariomadness.mobile;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.system.OsConstants;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;

/** A Java-only launcher: never references SDL or loads a game native library. */
public class DiagnosticActivity extends Activity {
    private TextView reportView;
    private volatile String report = "Coletando diagnóstico...";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView instructions = new TextView(this);
        instructions.setText("Mario's Madness — diagnóstico\nToque em Iniciar jogo. Se ele fechar, abra o app novamente e toque em Copiar diagnóstico. Cole o texto na conversa.");
        layout.addView(instructions);
        Button start = new Button(this);
        start.setText("Iniciar jogo");
        start.setOnClickListener(view -> {
            // String component avoids loading the game's class in this process.
            Intent game = new Intent().setClassName(this, getPackageName() + ".MainActivity");
            game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(game);
        });
        layout.addView(start);
        Button copy = new Button(this);
        copy.setText("Copiar diagnóstico");
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Mario diagnóstico", report));
            Toast.makeText(this, "Diagnóstico copiado. Cole na conversa.", Toast.LENGTH_LONG).show();
        });
        layout.addView(copy);
        Button refresh = new Button(this);
        refresh.setText("Atualizar diagnóstico");
        refresh.setOnClickListener(view -> refreshReport());
        layout.addView(refresh);
        ScrollView scroll = new ScrollView(this);
        reportView = new TextView(this);
        reportView.setTextIsSelectable(true);
        scroll.addView(reportView);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshReport();
    }

    private void refreshReport() {
        new Thread(() -> {
            String result = collectReport();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                report = result;
                reportView.setText(result);
            });
        }, "MarioReport").start();
    }

    private String collectReport() {
        StringBuilder text = new StringBuilder("Mario's Madness — diagnostic build 19\n");
        text.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL);
        text.append("\nAndroid: ").append(Build.VERSION.RELEASE).append(" API ").append(Build.VERSION.SDK_INT);
        text.append("\nSupported ABI: ").append(Arrays.toString(Build.SUPPORTED_ABIS));
        text.append("\n64-bit process: ").append(android.os.Process.is64Bit());
        try {
            text.append("\nPage size: ").append(Os.sysconf(OsConstants._SC_PAGESIZE));
            if (Build.VERSION.SDK_INT >= 31) text.append("\nSoC: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL);
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            text.append("\nMemory now (not at crash), MiB available/total: ")
                .append(memory.availMem / 1048576).append('/').append(memory.totalMem / 1048576);
            if (Build.VERSION.SDK_INT >= 30) {
                text.append("\n\nRecent process exits (newest first):\n");
                for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(getPackageName(), 0, 6)) {
                    text.append(new java.util.Date(exit.getTimestamp())).append("\nprocess=").append(exit.getProcessName());
                    text.append(" reason=").append(reasonName(exit.getReason())).append(" (").append(exit.getReason()).append(')');
                    text.append(" status=").append(exit.getStatus()).append(" PSS/RSS KiB=").append(exit.getPss()).append('/').append(exit.getRss());
                    text.append("\n").append(exit.getDescription()).append('\n');
                    // Read one recent failure trace; Android may retain no trace.
                    if (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE || exit.getReason() == ApplicationExitInfo.REASON_ANR) {
                        try (InputStream trace = exit.getTraceInputStream()) {
                            text.append("OS trace:\n").append(exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE
                                ? TombstoneSummary.read(trace) : readLimited(trace, 12288)).append('\n');
                        } catch (Exception error) {
                            text.append("Trace unavailable: ").append(error.getClass().getSimpleName()).append('\n');
                        }
                        break;
                    }
                }
            } else text.append("\nOS exit history requires Android 11+.\n");
        } catch (Exception error) {
            text.append("\nOS report unavailable: ").append(error.getClass().getSimpleName()).append('\n');
        }
        for (String name : new String[] {"startup-current.txt", "startup-previous.txt"}) {
            text.append("\n").append(name).append(":\n");
            try (InputStream input = new FileInputStream(new File(getFilesDir(), name))) {
                text.append(readLimited(input, 4096));
            } catch (Exception error) {
                text.append("No saved startup log yet.\n");
            }
        }
        return text.length() <= 28000 ? text.toString() : text.substring(0, 28000) + "\n[report truncated]\n";
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case 1: return "EXIT_SELF";
            case 2: return "SIGNALED";
            case 3: return "LOW_MEMORY";
            case 4: return "JAVA_CRASH";
            case 5: return "NATIVE_CRASH";
            case 6: return "ANR";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 16: return "PACKAGE_UPDATED";
            default: return "OTHER";
        }
    }

    private static String readLimited(InputStream input, int limit) throws java.io.IOException {
        if (input == null) return "No trace retained by Android.\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (output.size() < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
            if (count < 0) return output.toString("UTF-8");
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8") + "\n[report section truncated]\n";
    }
}
