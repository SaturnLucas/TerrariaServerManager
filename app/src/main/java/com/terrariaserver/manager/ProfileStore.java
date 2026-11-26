package com.terrariaserver.manager;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public final class ProfileStore {
    private ProfileStore() {}

    private static final String PREFS = "prefs";
    private static final String KEY = "profiles";

    public static List<Profile> load(Context c) {
        List<Profile> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                list.add(Profile.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static Profile get(Context c, String id) {
        for (Profile p : load(c)) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static void saveAll(Context c, List<Profile> list) {
        JSONArray a = new JSONArray();
        for (Profile p : list) {
            a.put(p.toJson());
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }

    public static void upsert(Context c, Profile p) {
        List<Profile> list = load(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(p.id)) {
                list.set(i, p);
                found = true;
            }
        }
        if (!found) {
            list.add(p);
        }
        saveAll(c, list);
    }

    public static void delete(Context c, String id) {
        List<Profile> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
            }
        }
        saveAll(c, list);
    }
}
