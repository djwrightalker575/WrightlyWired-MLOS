package com.uwright.wiredistro;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_COVER = 1001;
    private static final int REQ_AUDIO = 1002;

    private final List<TrackEditor> tracks = new ArrayList<>();
    private String releaseType = "SINGLE";
    private Uri coverUri;
    private String coverName = "";
    private String coverMime = "image/jpeg";
    private long coverSize = -1;
    private int coverWidth = -1, coverHeight = -1;

    private EditText releaseTitle, firstName, lastName, artistEmail, releaseDate, typeOfWork;
    private TextView coverInfo, validationInfo, ledgerInfo;
    private LinearLayout trackContainer;
    private Button singleButton, albumButton;
    private CheckBox keepPrivate;
    private ReleaseStore store;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ReleaseStore(this);
        buildUi();
        refreshLedger();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ledgerInfo != null) refreshLedger();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(9, 11, 16));
        LinearLayout root = column();
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("WIRE DISTRO", 28, Color.rgb(242,231,181));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        root.addView(text("Build once. Validate locally. Submit through the Freecords protocol we measured.", 14, Color.LTGRAY));
        spacer(root, 16);

        TextView typeLabel = section("RELEASE TYPE"); root.addView(typeLabel);
        LinearLayout typeRow = row();
        singleButton = button("SINGLE");
        albumButton = button("ALBUM");
        singleButton.setOnClickListener(v -> setReleaseType("SINGLE"));
        albumButton.setOnClickListener(v -> setReleaseType("ALBUM"));
        typeRow.addView(singleButton, weight());
        typeRow.addView(albumButton, weight());
        root.addView(typeRow);
        updateTypeButtons();
        spacer(root, 14);

        root.addView(section("RELEASE"));
        releaseTitle = edit("Release title"); root.addView(releaseTitle);
        spacer(root, 8);
        Button coverButton = button("CHOOSE ARTWORK");
        coverButton.setOnClickListener(v -> pickCover());
        root.addView(coverButton);
        coverInfo = text("No artwork selected", 13, Color.LTGRAY); root.addView(coverInfo);
        spacer(root, 14);

        root.addView(section("ARTIST / RIGHTS"));
        firstName = edit("Legal first name"); root.addView(firstName);
        lastName = edit("Legal last name"); root.addView(lastName);
        artistEmail = edit("Freecords account email"); root.addView(artistEmail);
        typeOfWork = edit("Type of work"); typeOfWork.setText("Original"); root.addView(typeOfWork);
        spacer(root, 14);

        root.addView(section("TIMING"));
        releaseDate = edit("Release date — YYYY-MM-DD"); root.addView(releaseDate);
        keepPrivate = new CheckBox(this);
        keepPrivate.setText("Keep private until release");
        keepPrivate.setTextColor(Color.WHITE);
        root.addView(keepPrivate);
        spacer(root, 14);

        root.addView(section("TRACKS"));
        Button audioButton = button("ADD AUDIO");
        audioButton.setOnClickListener(v -> pickAudio());
        root.addView(audioButton);
        trackContainer = column(); root.addView(trackContainer);
        spacer(root, 14);

        Button validate = button("VALIDATE RELEASE");
        validate.setOnClickListener(v -> showValidation(false));
        root.addView(validate);
        validationInfo = text("", 14, Color.WHITE);
        validationInfo.setPadding(0, dp(8), 0, dp(8));
        root.addView(validationInfo);

        Button prepare = button("PREPARE + OPEN FREECORDS SESSION");
        prepare.setOnClickListener(v -> showValidation(true));
        root.addView(prepare);
        root.addView(text("The actual distribution action happens on the next screen and requires a separate final confirmation.", 12, Color.LTGRAY));
        spacer(root, 20);

        root.addView(section("LOCAL RELEASE LEDGER"));
        ledgerInfo = text("", 13, Color.LTGRAY); root.addView(ledgerInfo);
        setContentView(scroll);
    }

    private void setReleaseType(String type) {
        releaseType = type;
        if ("SINGLE".equals(type) && tracks.size() > 1) {
            TrackEditor first = tracks.get(0);
            tracks.clear();
            tracks.add(first);
            renderTracks();
        }
        updateTypeButtons();
    }

    private void updateTypeButtons() {
        if (singleButton == null) return;
        singleButton.setEnabled(!"SINGLE".equals(releaseType));
        albumButton.setEnabled(!"ALBUM".equals(releaseType));
    }

    private void pickCover() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_COVER);
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, "ALBUM".equals(releaseType));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_COVER && data.getData() != null) {
            Uri uri = data.getData(); persist(uri, data);
            inspectCover(uri);
        } else if (requestCode == REQ_AUDIO) {
            List<Uri> selected = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) for (int n = 0; n < clip.getItemCount(); n++) selected.add(clip.getItemAt(n).getUri());
            else if (data.getData() != null) selected.add(data.getData());
            for (Uri uri : selected) {
                persist(uri, data);
                TrackEditor editor = new TrackEditor(uri);
                inspectAudio(editor);
                if ("SINGLE".equals(releaseType)) tracks.clear();
                tracks.add(editor);
                if ("SINGLE".equals(releaseType)) break;
            }
            if ("SINGLE".equals(releaseType) && !tracks.isEmpty() && releaseTitle.getText().toString().trim().isEmpty()) {
                releaseTitle.setText(tracks.get(0).defaultTitle());
            }
            renderTracks();
        }
    }

    private void persist(Uri uri, Intent data) {
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) { }
    }

    private void inspectCover(Uri uri) {
        coverUri = uri;
        FileInfo f = fileInfo(uri);
        coverName = f.name; coverSize = f.size; coverMime = f.mime;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o); if (in != null) in.close();
            coverWidth = o.outWidth; coverHeight = o.outHeight;
        } catch (Exception e) { coverWidth = coverHeight = -1; }
        coverInfo.setText(coverName + " • " + dim(coverWidth) + "×" + dim(coverHeight) + " • " + sizeText(coverSize));
    }

    private void inspectAudio(TrackEditor t) {
        FileInfo f = fileInfo(t.uri);
        t.fileName = f.name; t.fileSize = f.size; t.mime = f.mime;
        try {
            MediaMetadataRetriever m = new MediaMetadataRetriever();
            m.setDataSource(this, t.uri);
            String ms = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            t.durationSeconds = ms == null ? -1 : Long.parseLong(ms) / 1000.0;
            m.release();
        } catch (Exception ignored) { t.durationSeconds = -1; }
    }

    private FileInfo fileInfo(Uri uri) {
        String name = uri.getLastPathSegment() == null ? "file" : uri.getLastPathSegment();
        long size = -1;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ni >= 0) name = c.getString(ni);
                int si = c.getColumnIndex(OpenableColumns.SIZE); if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Exception ignored) { }
        String mime = getContentResolver().getType(uri); if (mime == null) mime = "application/octet-stream";
        return new FileInfo(name, mime, size);
    }

    private void renderTracks() {
        trackContainer.removeAllViews();
        for (int i = 0; i < tracks.size(); i++) {
            final int index = i;
            TrackEditor t = tracks.get(i);
            LinearLayout box = column();
            box.setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(17,21,29)); bg.setCornerRadius(dp(12));
            box.setBackground(bg);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(0, dp(10), 0, 0); box.setLayoutParams(bp);

            box.addView(text("TRACK " + (i + 1) + " • " + t.fileName + " • " + sizeText(t.fileSize) + (t.durationSeconds > 0 ? " • " + (int)t.durationSeconds + "s" : ""), 13, Color.rgb(217,184,74)));
            t.title = reuseOrEdit(t.title, t.defaultTitle(), "Track title"); box.addView(t.title);
            t.genre = reuseOrEdit(t.genre, "Soundtrack", "Genre"); box.addView(t.genre);
            t.language = reuseOrEdit(t.language, "English", "Language"); box.addView(t.language);
            t.country = reuseOrEdit(t.country, "United States", "Country"); box.addView(t.country);
            t.mood = reuseOrEdit(t.mood, "Serious", "Mood"); box.addView(t.mood);
            t.isrc = reuseOrEdit(t.isrc, "", "ISRC (optional)"); box.addView(t.isrc);
            t.lyrics = reuseOrEdit(t.lyrics, "", "Lyrics"); t.lyrics.setMinLines(4); t.lyrics.setGravity(android.view.Gravity.TOP); box.addView(t.lyrics);
            t.explicit = t.explicit == null ? new CheckBox(this) : t.explicit;
            detach(t.explicit); t.explicit.setText("Explicit"); t.explicit.setTextColor(Color.WHITE); box.addView(t.explicit);

            LinearLayout controls = row();
            Button up = button("↑"); up.setEnabled(i > 0); up.setOnClickListener(v -> { Collections.swap(tracks, index, index - 1); renderTracks(); });
            Button down = button("↓"); down.setEnabled(i < tracks.size() - 1); down.setOnClickListener(v -> { Collections.swap(tracks, index, index + 1); renderTracks(); });
            Button remove = button("REMOVE"); remove.setOnClickListener(v -> { tracks.remove(index); renderTracks(); });
            controls.addView(up, weight()); controls.addView(down, weight()); controls.addView(remove, weight());
            box.addView(controls);
            trackContainer.addView(box);
        }
    }

    private EditText reuseOrEdit(EditText old, String defaultValue, String hint) {
        String value = old == null ? defaultValue : old.getText().toString();
        if (old != null) detach(old);
        EditText e = edit(hint); e.setText(value); return e;
    }

    private void detach(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup)v.getParent()).removeView(v);
    }

    private void showValidation(boolean prepare) {
        try {
            JSONObject release = buildRelease();
            List<String> errors = validate(release);
            if (errors.isEmpty()) {
                validationInfo.setText("✓ Release passes local blocking checks.");
                validationInfo.setTextColor(Color.rgb(160, 230, 160));
                if (prepare) {
                    store.savePrepared(release);
                    startActivity(new Intent(this, FreecordsSessionActivity.class));
                }
            } else {
                StringBuilder b = new StringBuilder(); for (String e : errors) b.append("✕ ").append(e).append('\n');
                validationInfo.setText(b.toString().trim()); validationInfo.setTextColor(Color.rgb(255,160,160));
            }
        } catch (Exception e) {
            validationInfo.setText("✕ Could not prepare release: " + e.getMessage());
            validationInfo.setTextColor(Color.rgb(255,160,160));
        }
    }

    private JSONObject buildRelease() throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", releaseType);
        o.put("title", releaseTitle.getText().toString().trim());
        o.put("firstName", firstName.getText().toString().trim());
        o.put("lastName", lastName.getText().toString().trim());
        o.put("artistEmail", artistEmail.getText().toString().trim());
        o.put("typeOfWork", typeOfWork.getText().toString().trim());
        o.put("releaseDate", releaseDate.getText().toString().trim());
        o.put("visibility", "distributed");
        o.put("keepPrivateUntilRelease", keepPrivate.isChecked());
        o.put("coverUri", coverUri == null ? "" : coverUri.toString());
        o.put("coverFileName", coverName); o.put("coverMimeType", coverMime); o.put("coverFileSize", coverSize);
        o.put("coverWidth", coverWidth); o.put("coverHeight", coverHeight);
        JSONArray a = new JSONArray();
        for (TrackEditor t : tracks) {
            JSONObject j = new JSONObject();
            j.put("uri", t.uri.toString()); j.put("fileName", t.fileName); j.put("mimeType", t.mime); j.put("fileSize", t.fileSize); j.put("durationSeconds", t.durationSeconds);
            j.put("title", val(t.title)); j.put("genre", val(t.genre)); j.put("language", val(t.language)); j.put("country", val(t.country)); j.put("mood", val(t.mood));
            j.put("explicit", t.explicit != null && t.explicit.isChecked()); j.put("lyrics", val(t.lyrics)); j.put("isrcCode", val(t.isrc));
            a.put(j);
        }
        o.put("tracks", a); return o;
    }

    private List<String> validate(JSONObject o) throws Exception {
        List<String> e = new ArrayList<>();
        if (o.getString("title").isEmpty()) e.add("Release title is missing.");
        if (o.getString("firstName").isEmpty() || o.getString("lastName").isEmpty()) e.add("Legal first and last name are required.");
        if (!o.getString("artistEmail").contains("@")) e.add("Freecords email looks invalid.");
        if (!o.getString("releaseDate").matches("\\d{4}-\\d{2}-\\d{2}")) e.add("Release date must be YYYY-MM-DD.");
        if (coverUri == null) e.add("Cover artwork is missing.");
        if (coverWidth > 0 && coverHeight > 0) {
            if (coverWidth != coverHeight) e.add("Cover artwork must be square.");
            if (coverWidth < 1000 || coverHeight < 1000) e.add("Cover artwork is below 1000×1000.");
        }
        if ("SINGLE".equals(releaseType) && tracks.size() != 1) e.add("A single must have exactly one track.");
        if ("ALBUM".equals(releaseType) && tracks.size() < 2) e.add("An album needs at least two tracks.");
        for (int i = 0; i < tracks.size(); i++) {
            TrackEditor t = tracks.get(i); int n = i + 1;
            if (val(t.title).isEmpty()) e.add("Track " + n + " needs a title.");
            if (val(t.genre).isEmpty() || val(t.language).isEmpty() || val(t.country).isEmpty() || val(t.mood).isEmpty()) e.add("Track " + n + " is missing required metadata.");
            if (t.durationSeconds > 0 && t.durationSeconds < 60) e.add("Track " + n + " is shorter than 60 seconds.");
        }
        return e;
    }

    private void refreshLedger() {
        if (ledgerInfo == null) return;
        JSONArray h = store.history();
        if (h.length() == 0) { ledgerInfo.setText("No submissions recorded by this app yet."); return; }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(10, h.length()); i++) {
            JSONObject o = h.optJSONObject(i); if (o == null) continue;
            b.append(o.optString("title")).append(" • ").append(o.optString("type")).append(" • ").append(o.optString("status")).append('\n');
            b.append("ID: ").append(o.optString("id")).append('\n').append(o.optString("submittedAt")).append("\n\n");
        }
        ledgerInfo.setText(b.toString().trim());
    }

    private static String val(EditText e) { return e == null ? "" : e.getText().toString().trim(); }
    private static String dim(int x) { return x > 0 ? String.valueOf(x) : "?"; }
    private static String sizeText(long bytes) { return bytes < 0 ? "size ?" : String.format(Locale.US, "%.1f MB", bytes / 1048576.0); }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private void spacer(LinearLayout root, int d) { Space s = new Space(this); root.addView(s, new LinearLayout.LayoutParams(1, dp(d))); }
    private TextView section(String s) { TextView t = text(s, 13, Color.rgb(217,184,74)); t.setTypeface(null, android.graphics.Typeface.BOLD); t.setPadding(0, dp(5), 0, dp(6)); return t; }
    private TextView text(String s, int sp, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); return b; }
    private EditText edit(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE); e.setSingleLine(!"Lyrics".equals(hint));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(24,30,40)); bg.setCornerRadius(dp(8)); bg.setStroke(dp(1), Color.rgb(60,67,78)); e.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0, dp(4), 0, dp(4)); e.setLayoutParams(p); return e;
    }
    private int dp(int d) { return Math.round(d * getResources().getDisplayMetrics().density); }

    private static final class FileInfo { final String name,mime; final long size; FileInfo(String n,String m,long s){name=n;mime=m;size=s;} }
    private static final class TrackEditor {
        final Uri uri; String fileName="audio", mime="application/octet-stream"; long fileSize=-1; double durationSeconds=-1;
        EditText title,genre,language,country,mood,isrc,lyrics; CheckBox explicit;
        TrackEditor(Uri u){uri=u;}
        String defaultTitle(){ String n=fileName; int dot=n.lastIndexOf('.'); if(dot>0)n=n.substring(0,dot); return n.replace('_',' ').trim(); }
    }
}
