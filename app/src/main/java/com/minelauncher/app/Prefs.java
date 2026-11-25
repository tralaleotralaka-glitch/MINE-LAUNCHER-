package com.minelauncher.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String FILE = "mine_launcher";

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String nickname(Context c) {
        return get(c).getString("nickname", "Steve");
    }

    public static void setNickname(Context c, String v) {
        get(c).edit().putString("nickname", v).apply();
    }

    public static int versionIndex(Context c) {
        return get(c).getInt("version", 0);
    }

    public static void setVersionIndex(Context c, int v) {
        get(c).edit().putInt("version", v).apply();
    }

    public static int sensitivity(Context c) {
        return get(c).getInt("sensitivity", 45);
    }

    public static void setSensitivity(Context c, int v) {
        get(c).edit().putInt("sensitivity", v).apply();
    }

    public static boolean showFps(Context c) {
        return get(c).getBoolean("show_fps", true);
    }

    public static void setShowFps(Context c, boolean v) {
        get(c).edit().putBoolean("show_fps", v).apply();
    }
}
