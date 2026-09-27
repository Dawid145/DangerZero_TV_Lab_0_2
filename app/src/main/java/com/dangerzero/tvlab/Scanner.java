package com.dangerzero.tvlab;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local, read-only diagnostic scanner. It never installs, removes, disables or modifies packages. */
public final class Scanner {
    private Scanner() {}

    public static List<ScanResult> scan(Context context) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages;
        if (Build.VERSION.SDK_INT >= 33) {
            packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS
                            | PackageManager.GET_SIGNING_CERTIFICATES
                            | PackageManager.GET_SERVICES));
        } else {
            packages = pm.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS
                            | PackageManager.GET_SIGNATURES
                            | PackageManager.GET_SERVICES);
        }

        Set<String> accessibilityPackages = findAccessibilityServicePackages(pm);
        List<ScanResult> out = new ArrayList<>();

        for (PackageInfo p : packages) {
            try {
                ScanResult r = inspectPackage(pm, p, accessibilityPackages);
                out.add(r);
            } catch (Throwable ignored) {
                // One malformed/inaccessible package must never abort the complete scan.
            }
        }

        Collections.sort(out, (a, b) -> Integer.compare(b.score, a.score));
        return out;
    }

    private static ScanResult inspectPackage(PackageManager pm, PackageInfo p,
                                             Set<String> accessibilityPackages) {
        ScanResult r = new ScanResult();
        r.packageName = p.packageName == null ? "" : p.packageName;

        ApplicationInfo ai = p.applicationInfo;
        if (ai != null) {
            CharSequence label = pm.getApplicationLabel(ai);
            r.appName = label == null ? r.packageName : label.toString();
            r.systemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            r.sourceDir = ai.sourceDir == null ? "" : ai.sourceDir;
        } else {
            r.appName = r.packageName;
        }

        String versionName = p.versionName == null ? "desconocida" : p.versionName;
        if (Build.VERSION.SDK_INT >= 28) {
            r.version = versionName + " (" + p.getLongVersionCode() + ")";
        } else {
            r.version = versionName;
        }

        readInstallSource(pm, r);

        if (p.requestedPermissions != null) {
            for (String perm : p.requestedPermissions) {
                if (perm != null) r.permissions.add(perm);
            }
        }

        r.sha256 = sha256File(r.sourceDir);
        r.signatureSha256 = signatureHash(p);
        applyHeuristics(r, accessibilityPackages.contains(r.packageName));
        return r;
    }

    private static void readInstallSource(PackageManager pm, ScanResult r) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            InstallSourceInfo info = pm.getInstallSourceInfo(r.packageName);
            if (info == null) return;
            String installer = info.getInstallingPackageName();
            String initiating = info.getInitiatingPackageName();
            if (installer != null) r.installer = installer;
            if (initiating != null) r.initiatingPackage = initiating;
        } catch (Throwable ignored) {
            // Provenance is best-effort and must not stop scanning.
        }
    }

    /**
     * Detects actual AccessibilityService declarations rather than looking for the
     * BIND_ACCESSIBILITY_SERVICE permission in requestedPermissions.
     */
    private static Set<String> findAccessibilityServicePackages(PackageManager pm) {
        Set<String> result = new HashSet<>();
        try {
            Intent intent = new Intent(AccessibilityService.SERVICE_INTERFACE);
            List<ResolveInfo> services = pm.queryIntentServices(intent, PackageManager.MATCH_ALL);
            for (ResolveInfo ri : services) {
                ServiceInfo si = ri.serviceInfo;
                if (si != null && si.packageName != null
                        && "android.permission.BIND_ACCESSIBILITY_SERVICE".equals(si.permission)) {
                    result.add(si.packageName);
                }
            }
        } catch (Throwable ignored) {
            // Capability detection is optional; scanning continues without it.
        }
        return result;
    }

    private static void applyHeuristics(ScanResult r, boolean accessibility) {
        boolean overlay = has(r, "android.permission.SYSTEM_ALERT_WINDOW");
        boolean installPackages = has(r, "android.permission.REQUEST_INSTALL_PACKAGES");
        boolean queryAll = has(r, "android.permission.QUERY_ALL_PACKAGES");
        boolean sms = has(r, "android.permission.READ_SMS")
                || has(r, "android.permission.RECEIVE_SMS")
                || has(r, "android.permission.SEND_SMS");
        boolean contacts = has(r, "android.permission.READ_CONTACTS")
                || has(r, "android.permission.WRITE_CONTACTS");
        boolean storage = has(r, "android.permission.MANAGE_EXTERNAL_STORAGE");
        boolean internet = has(r, "android.permission.INTERNET");

        if (installPackages) {
            r.score += 25;
            r.findings.add("Puede solicitar permiso para instalar otros APK (REQUEST_INSTALL_PACKAGES).");
        }
        if (overlay) {
            r.score += 20;
            r.findings.add("Puede dibujar sobre otras aplicaciones (SYSTEM_ALERT_WINDOW).");
        }
        if (accessibility) {
            r.score += 20;
            r.findings.add("Declara un AccessibilityService; esta capacidad merece revisión contextual.");
        }
        if (sms) {
            r.score += 25;
            r.findings.add("Solicita permisos relacionados con SMS.");
        }
        if (contacts) {
            r.score += 10;
            r.findings.add("Solicita acceso a contactos.");
        }
        if (storage) {
            r.score += 15;
            r.findings.add("Solicita gestión amplia del almacenamiento.");
        }
        if (queryAll) {
            r.score += 5;
            r.findings.add("Solicita visibilidad amplia de aplicaciones instaladas.");
        }

        if (internet && (installPackages || overlay || sms || storage)) {
            r.score += 10;
            r.findings.add("Combinación de INTERNET con capacidades sensibles: requiere revisión contextual.");
        }

        if (r.score > 100) r.score = 100;
    }

    private static boolean has(ScanResult r, String permission) {
        return r.permissions.contains(permission);
    }

    private static String sha256File(String path) {
        if (path == null || path.isEmpty()) return "";
        File f = new File(path);
        if (!f.isFile() || !f.canRead()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return hex(md.digest());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String signatureHash(PackageInfo p) {
        try {
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                if (p.signingInfo == null) return "";
                sigs = p.signingInfo.hasMultipleSigners()
                        ? p.signingInfo.apkContentsSigners
                        : p.signingInfo.signingCertificateHistory;
            } else {
                sigs = p.signatures;
            }
            if (sigs == null || sigs.length == 0) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature sig : sigs) {
                if (sig != null) md.update(sig.toByteArray());
            }
            return hex(md.digest());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        final char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = digits[v >>> 4];
            out[i * 2 + 1] = digits[v & 0x0f];
        }
        return new String(out);
    }
}
