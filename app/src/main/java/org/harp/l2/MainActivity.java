package org.harp.l2;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements HarpLog.Listener {
    private static final int REQ_PERMS = 41;
    private TextView log;
    private HarpAwareController controller;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controller = new HarpAwareController(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("HARP L2 — Relay preflight");
        title.setTextSize(22);
        root.addView(title);

        Button relay = button("B — RELAY", v -> startRelay());
        Button client = button("A — CLIENTE", v -> startClient());
        Button stop = button("DETENER", v -> resetController());
        Button copy = button("COPIAR LOG", v -> copyLog());
        root.addView(relay);
        root.addView(client);
        root.addView(stop);
        root.addView(copy);

        ScrollView scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextSize(12);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        HarpLog.addListener(this);
        requestRuntimePermissions();
        HarpLog.i("HARP " + BuildMarker.VERSION + " listo");
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private boolean hasNearbyPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRuntimePermissions() {
        if (hasNearbyPermission()) return;
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_PERMS);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMS);
        }
    }

    private void startClient() {
        if (!hasNearbyPermission()) {
            requestRuntimePermissions();
            HarpLog.i("Permiso Wi-Fi cercano pendiente");
            return;
        }
        resetControllerSilently();
        HarpLog.i("ROLE=A CLIENTE");
        controller.startClient();
    }

    private void startRelay() {
        if (!hasNearbyPermission()) {
            requestRuntimePermissions();
            HarpLog.i("Permiso Wi-Fi cercano pendiente");
            return;
        }
        resetControllerSilently();
        HarpLog.i("ROLE=B RELAY");
        controller.startRelay();
    }

    private void resetController() {
        resetControllerSilently();
        HarpLog.i("Sesiones detenidas");
    }

    private void resetControllerSilently() {
        if (controller != null) controller.close();
        controller = new HarpAwareController(this);
    }

    private void copyLog() {
        ClipboardManager cb = getSystemService(ClipboardManager.class);
        cb.setPrimaryClip(ClipData.newPlainText("HARP Relay Preflight", HarpLog.snapshot()));
        Toast.makeText(this, "Log copiado", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        HarpLog.removeListener(this);
        if (controller != null) controller.close();
        super.onDestroy();
    }

    @Override public void onLine(String line) {
        runOnUiThread(() -> {
            log.append(line + "\n");
            ((ScrollView) log.getParent()).fullScroll(View.FOCUS_DOWN);
        });
    }
}
