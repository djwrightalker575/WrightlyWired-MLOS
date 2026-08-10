package com.uwright.wiredistro;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReleaseStore {
    private final SharedPreferences prefs;

    public ReleaseStore(Context context) {
        prefs = context.getSharedPreferences("wire_distro", Context.MODE_PRIVATE);
    }

    public void savePrepared(JSONObject release) {
        prefs.edit().putString("prepared_release", release.toString()).apply();
    }

    public JSONObject loadPrepared() {
        String raw = prefs.getString("prepared_release", null);
        if (raw == null) return null;
        try { return new JSONObject(raw); } catch (Exception e) { return null; }
    }

    public void clearPrepared() {
        prefs.edit().remove("prepared_release").apply();
    }

    public synchronized void addHistory(String id, String type, String title, String status) {
        try {
            JSONArray old = new JSONArray(prefs.getString("history", "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject()
                    .put("id", id)
                    .put("type", type)
                    .put("title", title)
                    .put("status", status)
                    .put("submittedAt", java.time.Instant.now().toString());
            next.put(item);
            for (int i = 0; i < old.length() && i < 99; i++) next.put(old.getJSONObject(i));
            prefs.edit().putString("history", next.toString()).apply();
        } catch (Exception ignored) { }
    }

    public JSONArray history() {
        try { return new JSONArray(prefs.getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
