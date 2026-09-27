package com.dangerzero.tvlab;

import java.util.ArrayList;
import java.util.List;

public class ScanResult {
    public String packageName = "";
    public String appName = "";
    public String version = "";
    public boolean systemApp;
    public String sourceDir = "";
    public String sha256 = "";
    public String installer = "";
    public String initiatingPackage = "";
    public String signatureSha256 = "";
    public final List<String> permissions = new ArrayList<>();
    public final List<String> findings = new ArrayList<>();
    public int score = 0;

    public String severity() {
        if (score >= 80) return "CRÍTICO";
        if (score >= 55) return "ALTO";
        if (score >= 30) return "MEDIO";
        if (score > 0) return "BAJO";
        return "SIN SEÑALES";
    }
}
