package com.nemotron.oneui.compat;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** App picker used from Samsung Home settings; pins real Android shortcuts. */
public final class NemotronAppPicker {
    private NemotronAppPicker() {}

    public static void show(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        Collections.sort(resolved, new ResolveInfo.DisplayNameComparator(pm));
        List<ResolveInfo> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<CharSequence> labels = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(context.getPackageName()) || !seen.add(pkg)) continue;
            entries.add(info);
            labels.add(info.loadLabel(pm));
        }
        if (entries.isEmpty()) {
            Toast.makeText(context, "No hay aplicaciones disponibles", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle("A\u00f1adir aplicaciones")
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> pin(context, pm, entries.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void pin(Context context, PackageManager pm, ResolveInfo info) {
        try {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null || !manager.isRequestPinShortcutSupported()) {
                Toast.makeText(context, "El launcher no admite accesos directos", Toast.LENGTH_LONG).show();
                return;
            }
            String pkg = info.activityInfo.packageName;
            CharSequence rawLabel = info.loadLabel(pm);
            String label = rawLabel == null ? pkg : rawLabel.toString();
            if (label.length() > 25) label = label.substring(0, 25);
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(new ComponentName(pkg, info.activityInfo.name));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable drawable = info.loadIcon(pm);
            Bitmap bitmap = renderIcon(drawable);
            String id = "nemotron_app_" + pkg + "_" + System.currentTimeMillis();
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(Icon.createWithBitmap(bitmap))
                    .setIntent(launch)
                    .build();
            if (manager.requestPinShortcut(shortcut, null)) {
                SharedPreferences preferences = context.getSharedPreferences("nemotron_launcher", Context.MODE_PRIVATE);
                Set<String> selected = new HashSet<>(preferences.getStringSet("manual_apps", Collections.emptySet()));
                selected.add(pkg);
                preferences.edit().putStringSet("manual_apps", selected).apply();
                Toast.makeText(context, "Confirma añadir " + label + " a Inicio", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "No se pudo solicitar el acceso directo", Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException error) {
            Toast.makeText(context, "No se pudo añadir la aplicación", Toast.LENGTH_LONG).show();
        }
    }

    private static Bitmap renderIcon(Drawable drawable) {
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }
}
