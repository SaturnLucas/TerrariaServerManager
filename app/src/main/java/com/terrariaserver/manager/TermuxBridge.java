package com.terrariaserver.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

public final class TermuxBridge {
    private TermuxBridge() {}

    public static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String HOME = "/data/data/com.termux/files/home";
    private static final String BASH = "/data/data/com.termux/files/usr/bin/bash";

    public static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean permitted(Context c) {
        return ContextCompat.checkSelfPermission(c, PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** foreground=true abre o Termux mostrando o progresso; false roda em segundo plano. */
    public static boolean run(Context c, String script, boolean foreground) {
        try {
            Intent i = new Intent("com.termux.RUN_COMMAND");
            i.setClassName("com.termux", "com.termux.app.RunCommandService");
            i.putExtra("com.termux.RUN_COMMAND_PATH", BASH);
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", script});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", !foreground);
            if (foreground) {
                i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            }
            ContextCompat.startForegroundService(c, i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
