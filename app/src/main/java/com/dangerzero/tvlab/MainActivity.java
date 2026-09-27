package com.dangerzero.tvlab;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(0xFF101418);

        root.addView(tv("DANGER ZERO — TV LAB 0.2", 24));
        root.addView(tv("Diagnóstico local, lectura únicamente. No instala, elimina ni modifica aplicaciones.", 15));

        Button scan = new Button(this);
        scan.setText("INICIAR ESCANEO");
        scan.setAllCaps(false);
        scan.setFocusable(true);
        scan.setOnClickListener(v -> startScan());
        root.addView(scan, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        status = tv("Listo. Ninguna configuración será modificada.", 14);
        root.addView(status);
        setContentView(root);
        scan.requestFocus();
    }

    private TextView tv(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFECEFF1);
        t.setTextSize(size);
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    private void startScan() {
        progress.setVisibility(View.VISIBLE);
        status.setText("Analizando aplicaciones instaladas...");
        new Thread(() -> {
            try {
                final List<ScanResult> results = Scanner.scan(this);
                runOnUiThread(() -> showResults(results));
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("El escaneo no pudo completarse: " + e.getClass().getSimpleName());
                });
            }
        }, "DangerZero-Scanner").start();
    }

    private void showResults(List<ScanResult> results) {
        progress.setVisibility(View.GONE);
        int critical = 0, high = 0, medium = 0, low = 0, clean = 0;
        for (ScanResult r : results) {
            switch (r.severity()) {
                case "CRÍTICO": critical++; break;
                case "ALTO": high++; break;
                case "MEDIO": medium++; break;
                case "BAJO": low++; break;
                default: clean++;
            }
        }

        root.removeAllViews();
        root.addView(tv("DANGER ZERO — RESULTADO 0.2", 24));
        root.addView(tv(String.format(Locale.US,
                "Aplicaciones: %d\nCríticas: %d | Altas: %d | Medias: %d | Bajas: %d | Sin señales: %d",
                results.size(), critical, high, medium, low, clean), 15));
        root.addView(tv("Las heurísticas son señales, no una prueba automática de malware. En esta versión no se toman acciones destructivas.", 14));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (ScanResult r : results) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(16, 16, 16, 16);

            StringBuilder s = new StringBuilder();
            s.append(r.appName).append("\n");
            s.append(r.packageName).append("\n");
            s.append("Riesgo heurístico: ").append(r.severity()).append(" (").append(r.score).append("/100)\n");
            s.append("Versión: ").append(r.version).append("\n");
            if (r.systemApp) s.append("Tipo: aplicación de sistema\n");
            if (!r.installer.isEmpty()) s.append("Instalador: ").append(r.installer).append("\n");
            if (!r.initiatingPackage.isEmpty()) s.append("Iniciador: ").append(r.initiatingPackage).append("\n");
            if (!r.sha256.isEmpty()) s.append("SHA-256 APK: ").append(r.sha256).append("\n");
            if (!r.signatureSha256.isEmpty()) s.append("SHA-256 firma: ").append(r.signatureSha256).append("\n");
            if (!r.findings.isEmpty()) {
                s.append("Motivos:\n");
                for (String f : r.findings) s.append("• ").append(f).append("\n");
            }

            card.addView(tv(s.toString(), 13));
            list.addView(card);
        }

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button back = new Button(this);
        back.setText("VOLVER AL DIAGNÓSTICO");
        back.setAllCaps(false);
        back.setOnClickListener(v -> buildUi());
        root.addView(back);
        status = tv("Escaneo terminado. Solo se recopilaron datos de lectura.", 14);
        root.addView(status);
        back.requestFocus();
    }
}
