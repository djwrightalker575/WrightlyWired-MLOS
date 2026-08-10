package com.uwright.wiredistro;

import android.app.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FreecordsSessionActivity extends Activity {
    private static final String BASE = "https://app.freecords.com";
    private WebView web;
    private TextView status;
    private ReleaseStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean busy = false;

    private interface BridgeCallback { void done(int status, String body); }
    private interface SessionCallback { void done(boolean loggedIn); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ReleaseStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9,11,16));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("Freecords session not checked.");
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button login = button("LOGIN"); login.setOnClickListener(v -> web.loadUrl(BASE + "/login"));
        Button reset = button("RESET"); reset.setOnClickListener(v -> web.loadUrl(BASE + "/reset-password"));
        Button check = button("CHECK"); check.setOnClickListener(v -> checkSession(null));
        row.addView(login, weight()); row.addView(reset, weight()); row.addView(check, weight());
        root.addView(row);

        Button submit = button("DISTRIBUTE PREPARED RELEASE");
        submit.setOnClickListener(v -> confirmDistribution());
        root.addView(submit);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                setStatus("Freecords page: " + url.replace(BASE, ""));
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        web.loadUrl(BASE + "/login");
    }

    private void confirmDistribution() {
        if (busy) return;
        JSONObject release = store.loadPrepared();
        if (release == null) { setStatus("No prepared release. Go back and prepare one first."); return; }
        int count = release.optJSONArray("tracks") == null ? 0 : release.optJSONArray("tracks").length();
        new AlertDialog.Builder(this)
                .setTitle("Submit to Freecords?")
                .setMessage("This is the REAL submission action. Freecords may send this release into moderation and distribution.\n\n" +
                        release.optString("type") + ": " + release.optString("title") + "\nTracks: " + count)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("SUBMIT", (d,w) -> startSubmission(release))
                .show();
    }

    private void startSubmission(JSONObject release) {
        busy = true;
        setStatus("Checking Freecords session…");
        checkSession(loggedIn -> {
            if (!loggedIn) { busy = false; setStatus("Not logged in. Log into Freecords in the browser below, then try again."); return; }
            setStatus("Session confirmed. Requesting artwork upload…");
            requestPresign(true,
                    release.optString("coverFileName"),
                    release.optString("coverMimeType", "image/jpeg"),
                    release.optLong("coverFileSize", -1),
                    (code, body) -> {
                        RemoteAsset cover = parsePresign(code, body);
                        if (cover == null) { fail("Freecords did not provide an artwork upload target."); return; }
                        putThenPublish(Uri.parse(release.optString("coverUri")), cover,
                                release.optString("coverMimeType", "image/jpeg"),
                                release.optLong("coverFileSize", -1),
                                "artwork", ok -> {
                                    if (!ok) return;
                                    uploadTrack(release, cover.publicUrl, 0, new ArrayList<>());
                                });
                    });
        });
    }

    private void uploadTrack(JSONObject release, String coverUrl, int index, List<RemoteTrack> remote) {
        JSONArray tracks = release.optJSONArray("tracks");
        if (tracks == null) { fail("Prepared release has no tracks."); return; }
        if (index >= tracks.length()) { finalizeRelease(release, coverUrl, remote); return; }

        JSONObject t = tracks.optJSONObject(index);
        if (t == null) { fail("Track metadata is unreadable."); return; }
        setStatus("Track " + (index + 1) + "/" + tracks.length() + ": requesting upload…");
        requestPresign(false, t.optString("fileName"), t.optString("mimeType", "application/octet-stream"), t.optLong("fileSize", -1),
                (code, body) -> {
                    RemoteAsset asset = parsePresign(code, body);
                    if (asset == null) { fail("Could not get an upload target for " + t.optString("title")); return; }
                    setStatus("Track " + (index + 1) + "/" + tracks.length() + ": uploading " + t.optString("title") + "…");
                    putThenPublish(Uri.parse(t.optString("uri")), asset, t.optString("mimeType", "application/octet-stream"), t.optLong("fileSize", -1),
                            "track " + (index + 1), ok -> {
                                if (!ok) return;
                                remote.add(new RemoteTrack(asset.publicUrl, asset.fileKey));
                                uploadTrack(release, coverUrl, index + 1, remote);
                            });
                });
    }

    private interface BoolDone { void done(boolean ok); }

    private void putThenPublish(Uri uri, RemoteAsset asset, String mime, long size, String label, BoolDone done) {
        io.execute(() -> {
            int putCode;
            try { putCode = putBinary(uri, asset.uploadUrl, mime, size); }
            catch (Exception e) { main.post(() -> fail("Upload failed for " + label + ": " + e.getMessage())); return; }
            if (putCode < 200 || putCode >= 300) { int c = putCode; main.post(() -> fail("Upload failed for " + label + " with HTTP " + c)); return; }
            main.post(() -> {
                setStatus("Publishing " + label + "…");
                try {
                    JSONObject q = new JSONObject().put("fileKey", asset.fileKey);
                    postJson("/api/upload/make-public", q, (code, body) -> {
                        if (code < 200 || code >= 300) { fail("Freecords could not publish " + label + "."); return; }
                        done.done(true);
                    });
                } catch (Exception e) { fail(e.getMessage()); }
            });
        });
    }

    private void finalizeRelease(JSONObject release, String coverUrl, List<RemoteTrack> remote) {
        try {
            String type = release.optString("type", "SINGLE");
            JSONArray tracks = release.getJSONArray("tracks");
            JSONObject payload = new JSONObject();
            String path;
            if ("SINGLE".equals(type)) {
                path = "/api/upload/finalize";
                JSONObject t = tracks.getJSONObject(0); RemoteTrack r = remote.get(0);
                payload.put("title", t.optString("title"));
                payload.put("genre", t.optString("genre"));
                payload.put("language", t.optString("language"));
                payload.put("country", t.optString("country"));
                payload.put("mood", t.optString("mood"));
                payload.put("explicit", t.optBoolean("explicit"));
                payload.put("visibility", release.optString("visibility", "distributed"));
                payload.put("firstName", release.optString("firstName"));
                payload.put("lastName", release.optString("lastName"));
                payload.put("typeOfWork", release.optString("typeOfWork", "Original"));
                payload.put("lyrics", t.optString("lyrics"));
                payload.put("releaseDate", release.optString("releaseDate"));
                payload.put("keepPrivateUntilRelease", release.optBoolean("keepPrivateUntilRelease"));
                payload.put("songFileUrl", r.publicUrl);
                payload.put("songFileKey", r.fileKey);
                payload.put("coverArtUrl", coverUrl);
                payload.put("collaborators", collaborators(release));
            } else {
                path = "/api/upload/finalize-album";
                payload.put("name", release.optString("title"));
                payload.put("visibility", release.optString("visibility", "distributed"));
                payload.put("firstName", release.optString("firstName"));
                payload.put("lastName", release.optString("lastName"));
                payload.put("typeOfWork", release.optString("typeOfWork", "Original"));
                payload.put("releaseDate", release.optString("releaseDate"));
                payload.put("keepPrivateUntilRelease", release.optBoolean("keepPrivateUntilRelease"));
                payload.put("coverArtUrl", coverUrl);
                JSONArray songs = new JSONArray();
                for (int i = 0; i < tracks.length(); i++) {
                    JSONObject t = tracks.getJSONObject(i); RemoteTrack r = remote.get(i);
                    songs.put(new JSONObject()
                            .put("title", t.optString("title"))
                            .put("genre", t.optString("genre"))
                            .put("language", t.optString("language"))
                            .put("country", t.optString("country"))
                            .put("mood", t.optString("mood"))
                            .put("explicit", t.optBoolean("explicit"))
                            .put("lyrics", t.optString("lyrics"))
                            .put("isrcCode", t.optString("isrcCode"))
                            .put("songFileUrl", r.publicUrl)
                            .put("collaborators", collaborators(release)));
                }
                payload.put("songs", songs);
            }

            setStatus("Finalizing " + type.toLowerCase(Locale.US) + "…");
            postJson(path, payload, (code, body) -> {
                if (code < 200 || code >= 300) { fail("Freecords finalization failed with HTTP " + code); return; }
                try {
                    JSONObject answer = new JSONObject(body);
                    boolean success = answer.optBoolean("success", false);
                    String id = answer.optString("SINGLE".equals(type) ? "songId" : "albumId");
                    String msg = answer.optString("message", "Submitted successfully.");
                    if (!success || id.isEmpty()) { fail(msg); return; }
                    store.addHistory(id, type, release.optString("title"), "SUBMITTED");
                    store.clearPrepared();
                    busy = false;
                    setStatus(msg + "\nID: " + id);
                    new AlertDialog.Builder(this).setTitle("Submitted").setMessage(msg + "\n\nRemote ID: " + id).setPositiveButton("OK", null).show();
                } catch (Exception e) { fail("Freecords returned an unreadable final response."); }
            });
        } catch (Exception e) { fail("Could not construct final release: " + e.getMessage()); }
    }

    private JSONArray collaborators(JSONObject release) throws Exception {
        return new JSONArray().put(new JSONObject()
                .put("name", (release.optString("firstName") + " " + release.optString("lastName")).trim())
                .put("email", release.optString("artistEmail"))
                .put("role", "Primary")
                .put("royaltyShare", 100)
                .put("isPrimary", true));
    }

    private void requestPresign(boolean cover, String name, String mime, long size, BridgeCallback cb) {
        try {
            JSONObject q = new JSONObject().put("fileName", name).put("fileType", mime).put("fileSize", size);
            postJson(cover ? "/api/upload/cover-art-presigned-url" : "/api/upload/presigned-url", q, cb);
        } catch (Exception e) { cb.done(0, e.toString()); }
    }

    private RemoteAsset parsePresign(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) return null;
        try {
            JSONObject o = new JSONObject(body);
            return new RemoteAsset(o.getString("uploadUrl"), o.getString("fileKey"), o.getString("publicUrl"));
        } catch (Exception e) { return null; }
    }

    private int putBinary(Uri uri, String uploadUrl, String mime, long size) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(uploadUrl).openConnection();
        try {
            c.setRequestMethod("PUT"); c.setDoOutput(true); c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(30000); c.setReadTimeout(60000); c.setRequestProperty("Content-Type", mime);
            if (size >= 0) c.setFixedLengthStreamingMode(size);
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = c.getOutputStream()) {
                if (in == null) throw new IOException("Cannot open selected file");
                byte[] buffer = new byte[128 * 1024]; int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                out.flush();
            }
            return c.getResponseCode();
        } finally { c.disconnect(); }
    }

    private void checkSession(SessionCallback callback) {
        getJson("/api/auth/session", (code, body) -> {
            boolean loggedIn = false; String label = "Not logged in.";
            try {
                JSONObject o = new JSONObject(body); JSONObject u = o.optJSONObject("user");
                loggedIn = code == 200 && u != null;
                if (loggedIn) label = "Logged in as " + u.optString("name", u.optString("email", "Freecords user")) + ".";
            } catch (Exception ignored) { }
            setStatus(label);
            if (callback != null) callback.done(loggedIn);
        });
    }

    private void getJson(String path, BridgeCallback cb) { bridgeFetch("GET", path, null, cb); }
    private void postJson(String path, JSONObject body, BridgeCallback cb) { bridgeFetch("POST", path, body.toString(), cb); }

    private void bridgeFetch(String method, String path, String body, BridgeCallback cb) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> bridgeFetch(method, path, body, cb)); return; }
        String bodyPart = body == null ? "" : ",body:" + JSONObject.quote(body);
        String js = "(async()=>{try{const r=await fetch(" + JSONObject.quote(path) + ",{method:" + JSONObject.quote(method) + ",credentials:'include',headers:{'Content-Type':'application/json','Accept':'application/json'}" + bodyPart + "});const t=await r.text();return JSON.stringify({status:r.status,body:t});}catch(e){return JSON.stringify({status:0,body:String(e)});}})()";
        web.evaluateJavascript(js, raw -> {
            try {
                Object decoded = new JSONTokener(raw).nextValue();
                String inner = decoded instanceof String ? (String)decoded : raw;
                JSONObject o = new JSONObject(inner);
                cb.done(o.optInt("status", 0), o.optString("body", ""));
            } catch (Exception e) { cb.done(0, e.toString()); }
        });
    }

    private void fail(String message) {
        busy = false; setStatus(message);
        if (!isFinishing()) new AlertDialog.Builder(this).setTitle("Submission stopped").setMessage(message).setPositiveButton("OK", null).show();
    }
    private void setStatus(String text) { if (Looper.myLooper() == Looper.getMainLooper()) status.setText(text); else main.post(() -> status.setText(text)); }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); return b; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(int d) { return Math.round(d * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() {
        io.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private static final class RemoteAsset {
        final String uploadUrl, fileKey, publicUrl;
        RemoteAsset(String u, String k, String p) { uploadUrl=u; fileKey=k; publicUrl=p; }
    }
    private static final class RemoteTrack {
        final String publicUrl, fileKey;
        RemoteTrack(String p, String k) { publicUrl=p; fileKey=k; }
    }
}
