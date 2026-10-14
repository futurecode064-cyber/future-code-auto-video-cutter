package com.futurecode.autovideocutter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline video splitting and joining. No network or broad storage permissions required. */
public final class MainActivity extends Activity {
    private static final int PICK_SPLIT = 40;
    private static final int PICK_MERGE = 41;
    private static final int BG = Color.rgb(8, 28, 41);
    private static final int CARD = Color.rgb(18, 46, 61);
    private static final int PALE = Color.rgb(196, 211, 220);
    private static final int TEXT = Color.WHITE;
    private static final int ACCENT = Color.rgb(252, 153, 69);
    private static final int DARK_BUTTON = Color.rgb(32, 66, 83);
    private static final String ALBUM = "Future Code Auto Video Cutter";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<VideoEntry> mergeVideos = new ArrayList<>();
    private final ArrayList<Uri> exported = new ArrayList<>();
    private VideoEntry splitVideo;
    private boolean splitTab = true;
    private boolean busy = false;
    private boolean cancelRequested = false;
    private boolean copying = false;
    private int jobIndex = 0;
    private int jobCount = 0;
    private boolean splittingJob = false;
    private long segmentLengthMs = 0;
    private long sourceDurationMs = 0;
    private String namePrefix;
    private File workingFile;
    private Transformer transformer;
    private LinearLayout splitPanel, mergePanel, mergeList, exportedList;
    private TextView selectedLabel, partsLabel, mergeSummary, messageLabel, progressLabel, outputTitle;
    private EditText minutesEdit;
    private Button splitPicker, mergePicker, splitStart, mergeStart, cancelButton, splitTabButton, mergeTabButton;
    private ProgressBar bar;
    private final ProgressHolder progressHolder = new ProgressHolder();

    private static final class VideoEntry {
        final Uri uri;
        final String name;
        volatile long durationMs = -1;
        VideoEntry(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        createUi();
    }

    private void createUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(25), dp(18), dp(35));
        scroll.addView(root);
        setContentView(scroll);

        TextView small = text("FUTURE CODE AI STUDIO", 12, ACCENT, true);
        root.addView(small);
        addSpace(root, 6);
        TextView heading = text("AUTO VIDEO CUTTER", 27, TEXT, true);
        root.addView(heading);
        addSpace(root, 5);
        root.addView(text("Offline • Automatic Split • Video Merge", 13, PALE, false));
        addSpace(root, 23);

        LinearLayout tabs = horizontal();
        splitTabButton = button("✂  CUT VIDEO", true);
        mergeTabButton = button("▣  MERGE VIDEO", false);
        tabs.addView(splitTabButton, weight());
        addGap(tabs, 8);
        tabs.addView(mergeTabButton, weight());
        root.addView(tabs);
        addSpace(root, 17);

        splitPanel = card();
        splitPanel.addView(text("01  /  AUTO VIDEO SPLITTER", 17, TEXT, true));
        addSpace(splitPanel, 7);
        splitPanel.addView(text("Video တစ်ခုကို မိနစ်သတ်မှတ်ပြီး အပိုင်းများ အလိုအလျောက်ဖြတ်ပါ။", 13, PALE, false));
        addSpace(splitPanel, 18);
        splitPicker = button("+  Choose a video", false);
        splitPanel.addView(splitPicker, full());
        addSpace(splitPanel, 11);
        selectedLabel = text("ဗီဒီယို မရွေးရသေးပါ", 14, PALE, false);
        splitPanel.addView(selectedLabel);
        addSpace(splitPanel, 18);
        splitPanel.addView(text("Length of each part (minutes)", 14, TEXT, true));
        addSpace(splitPanel, 8);
        minutesEdit = new EditText(this);
        minutesEdit.setText("5");
        minutesEdit.setSingleLine(true);
        minutesEdit.setTextColor(TEXT);
        minutesEdit.setHintTextColor(PALE);
        minutesEdit.setTextSize(19);
        minutesEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minutesEdit.setPadding(dp(13), 0, dp(13), 0);
        minutesEdit.setBackground(round(DARK_BUTTON, 12));
        splitPanel.addView(minutesEdit, new LinearLayout.LayoutParams(-1, dp(52)));
        addSpace(splitPanel, 11);
        LinearLayout presets = horizontal();
        for (int value : new int[]{1, 3, 5, 10, 15}) {
            Button preset = button(value + "m", false);
            preset.setTextSize(12);
            preset.setPadding(0, 0, 0, 0);
            presets.addView(preset, weight());
            addGap(presets, 5);
            preset.setOnClickListener(v -> { minutesEdit.setText(String.valueOf(value)); updateParts(); });
        }
        splitPanel.addView(presets);
        addSpace(splitPanel, 15);
        partsLabel = text("ဥပမာ 2 hours ÷ 5 minutes = 24 parts", 13, ACCENT, true);
        splitPanel.addView(partsLabel);
        addSpace(splitPanel, 16);
        splitStart = button("START AUTO CUT  →", true);
        splitPanel.addView(splitStart, full());
        root.addView(splitPanel);

        mergePanel = card();
        mergePanel.addView(text("02  /  VIDEO MERGER", 17, TEXT, true));
        addSpace(mergePanel, 7);
        mergePanel.addView(text("Video 1 + Video 2 + Video 3 → Video တစ်ခုတည်းအဖြစ် ပေါင်းဆက်ပါ။", 13, PALE, false));
        addSpace(mergePanel, 17);
        mergePicker = button("+  Add multiple videos", false);
        mergePanel.addView(mergePicker, full());
        addSpace(mergePanel, 12);
        mergeSummary = text("Video အနည်းဆုံး ၂ ခု ရွေးပါ", 13, PALE, false);
        mergePanel.addView(mergeSummary);
        addSpace(mergePanel, 12);
        mergeList = vertical();
        mergePanel.addView(mergeList);
        addSpace(mergePanel, 12);
        mergeStart = button("MERGE & EXPORT  →", true);
        mergePanel.addView(mergeStart, full());
        root.addView(mergePanel);
        mergePanel.setVisibility(View.GONE);

        addSpace(root, 17);
        LinearLayout progressPanel = card();
        progressPanel.addView(text("EXPORT STATUS", 14, TEXT, true));
        addSpace(progressPanel, 9);
        messageLabel = text("Ready to edit. No internet needed.", 13, PALE, false);
        progressPanel.addView(messageLabel);
        addSpace(progressPanel, 11);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(1000);
        bar.updateProgressUi(0);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progressPanel.addView(bar, new LinearLayout.LayoutParams(-1, dp(11)));
        addSpace(progressPanel, 6);
        progressLabel = text("0%", 12, PALE, false);
        progressPanel.addView(progressLabel);
        addSpace(progressPanel, 10);
        cancelButton = button("CANCEL EXPORT", false);
        cancelButton.setVisibility(View.GONE);
        progressPanel.addView(cancelButton, full());
        root.addView(progressPanel);

        addSpace(root, 17);
        LinearLayout outputs = card();
        outputTitle = text("SAVED VIDEOS", 14, TEXT, true);
        outputs.addView(outputTitle);
        addSpace(outputs, 8);
        outputs.addView(text("Movies / " + ALBUM, 12, PALE, false));
        addSpace(outputs, 10);
        exportedList = vertical();
        outputs.addView(exportedList);
        root.addView(outputs);
        addSpace(root, 15);
        root.addView(text("✓ Fully offline   ✓ No subscription   ✓ No upload", 12, PALE, false));
        addSpace(root, 8);
        root.addView(text("Android 10+ • MP4 output • Keep app open during export", 11, PALE, false));

        splitTabButton.setOnClickListener(v -> showTab(true));
        mergeTabButton.setOnClickListener(v -> showTab(false));
        splitPicker.setOnClickListener(v -> pick(PICK_SPLIT));
        mergePicker.setOnClickListener(v -> pick(PICK_MERGE));
        splitStart.setOnClickListener(v -> startSplit());
        mergeStart.setOnClickListener(v -> startMerge());
        cancelButton.setOnClickListener(v -> confirmCancel());
        minutesEdit.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateParts(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        renderMergeList();
        renderSaved();
    }

    private void showTab(boolean split) {
        if (busy) return;
        splitTab = split;
        splitPanel.setVisibility(split ? View.VISIBLE : View.GONE);
        mergePanel.setVisibility(split ? View.GONE : View.VISIBLE);
        splitTabButton.setBackground(round(split ? ACCENT : DARK_BUTTON, 12));
        mergeTabButton.setBackground(round(split ? DARK_BUTTON : ACCENT, 12));
        splitTabButton.setTextColor(split ? BG : TEXT);
        mergeTabButton.setTextColor(split ? TEXT : BG);
    }

    private void pick(int requestCode) {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (requestCode == PICK_MERGE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, requestCode); }
        catch (Exception e) { showMessage("File picker ဖွင့်မရပါ။ " + e.getMessage()); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || busy) return;
        ArrayList<Uri> chosen = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) chosen.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) chosen.add(data.getData());
        if (chosen.isEmpty()) return;
        if (request == PICK_SPLIT) {
            Uri uri = chosen.get(0);
            retainPermission(uri);
            splitVideo = new VideoEntry(uri, nameFor(uri));
            selectedLabel.setText(splitVideo.name + "\nReading video duration…");
            loadDuration(splitVideo, true);
        } else if (request == PICK_MERGE) {
            for (Uri uri : chosen) {
                retainPermission(uri);
                boolean exists = false;
                for (VideoEntry entry : mergeVideos) if (entry.uri.equals(uri)) exists = true;
                if (exists) continue;
                VideoEntry entry = new VideoEntry(uri, nameFor(uri));
                mergeVideos.add(entry);
                loadDuration(entry, false);
            }
            renderMergeList();
        }
    }

    private void retainPermission(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { /* The immediate read grant is enough for this session. */ }
    }

    private String nameFor(Uri uri) {
        String name = "Video";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {}
        return name == null || name.trim().isEmpty() ? "Video" : name;
    }

    private void loadDuration(VideoEntry entry, boolean forSplit) {
        io.execute(() -> {
            long length = -1;
            MediaMetadataRetriever reader = new MediaMetadataRetriever();
            try {
                reader.setDataSource(this, entry.uri);
                String value = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (value != null) length = Long.parseLong(value);
            } catch (Exception ignored) { }
            finally { try { reader.release(); } catch (Exception ignored) {} }
            entry.durationMs = length;
            runOnUiThread(() -> {
                if (forSplit && splitVideo == entry) {
                    selectedLabel.setText(entry.name + "\nDuration: " + formatTime(entry.durationMs));
                    updateParts();
                } else if (!forSplit && mergeVideos.contains(entry)) renderMergeList();
            });
        });
    }

    private long inputSegmentMs() {
        try {
            double mins = Double.parseDouble(minutesEdit.getText().toString().trim());
            if (!Double.isFinite(mins) || mins < 0.05 || mins > 10080) return -1;
            return Math.max(1L, Math.round(mins * 60000d));
        } catch (Exception e) { return -1; }
    }

    private void updateParts() {
        if (partsLabel == null || minutesEdit == null) return;
        long each = inputSegmentMs();
        if (each < 0) { partsLabel.setText("Enter a valid number of minutes (0.05–10080)."); return; }
        if (splitVideo == null) { partsLabel.setText("Example: 2 hours ÷ 5 min = 24 parts"); return; }
        if (splitVideo.durationMs <= 0) { partsLabel.setText("Unable to read duration. Choose a supported video."); return; }
        long count = ClipPlan.count(splitVideo.durationMs, each);
        partsLabel.setText(formatTime(splitVideo.durationMs) + "  →  " + count + " video parts");
    }

    private void renderMergeList() {
        if (mergeList == null) return;
        mergeList.removeAllViews();
        long total = 0;
        boolean unknown = false;
        for (int idx = 0; idx < mergeVideos.size(); idx++) {
            final int i = idx;
            VideoEntry item = mergeVideos.get(idx);
            if (item.durationMs > 0) total += item.durationMs;
            else unknown = true;
            LinearLayout line = horizontal();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(6), dp(7), dp(0), dp(7));
            TextView name = text((idx + 1) + ". " + item.name + "\n" + formatTime(item.durationMs), 12, TEXT, false);
            name.setMaxLines(2);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            Button up = tiny("↑");
            Button down = tiny("↓");
            Button remove = tiny("×");
            up.setEnabled(!busy && i > 0);
            down.setEnabled(!busy && i < mergeVideos.size() - 1);
            remove.setEnabled(!busy);
            line.addView(up); line.addView(down); line.addView(remove);
            up.setOnClickListener(v -> { Collections.swap(mergeVideos, i, i - 1); renderMergeList(); });
            down.setOnClickListener(v -> { Collections.swap(mergeVideos, i, i + 1); renderMergeList(); });
            remove.setOnClickListener(v -> { mergeVideos.remove(i); renderMergeList(); });
            mergeList.addView(line);
        }
        if (mergeVideos.isEmpty()) mergeList.addView(text("No videos selected yet.", 12, PALE, false));
        mergeSummary.setText(mergeVideos.size() + " videos" + (unknown ? " • duration checking…" : " • total " + formatTime(total)));
    }

    private void startSplit() {
        if (busy) return;
        if (splitVideo == null) { showMessage("အရင် Video တစ်ခု ရွေးပါ။"); return; }
        if (splitVideo.durationMs <= 0) { showMessage("ဗီဒီယိုကြာချိန် မဖတ်နိုင်ပါ။ MP4 တစ်ခု စမ်းကြည့်ပါ။"); return; }
        long ms = inputSegmentMs();
        if (ms < 0) { showMessage("မိနစ်အရေအတွက် မှန်ကန်စွာထည့်ပါ။"); return; }
        long count = ClipPlan.count(splitVideo.durationMs, ms);
        if (count > 10000) { showMessage("Parts 10,000 ထက်များနေပါသည်။ မိနစ်ကို တိုးပေးပါ။"); return; }
        splittingJob = true;
        segmentLengthMs = ms;
        sourceDurationMs = splitVideo.durationMs;
        jobCount = (int) count;
        namePrefix = safeStem(splitVideo.name);
        prepareJob();
        startPart();
    }

    private void startMerge() {
        if (busy) return;
        if (mergeVideos.size() < 2) { showMessage("အနည်းဆုံး Video ၂ ခု ရွေးပေးပါ။"); return; }
        for (VideoEntry v : mergeVideos) {
            if (v.durationMs <= 0) { showMessage("Video ကြာချိန် စစ်ဆေးပြီးမှ ဆက်လုပ်ပါ။"); return; }
        }
        splittingJob = false;
        jobCount = 1;
        namePrefix = "Merged_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new java.util.Date());
        prepareJob();
        List<EditedMediaItem> items = new ArrayList<>();
        for (VideoEntry e : mergeVideos) items.add(new EditedMediaItem.Builder(MediaItem.fromUri(e.uri)).build());
        EditedMediaItemSequence seq = EditedMediaItemSequence.withAudioAndVideoFrom(items);
        Composition composition = new Composition.Builder(seq).build();
        startExport(composition, namePrefix + ".mp4");
    }

    private void prepareJob() {
        busy = true;
        copying = false;
        cancelRequested = false;
        jobIndex = 0;
        exported.clear();
        renderSaved();
        setBusyControls();
        bar.updateProgressUi(0);
        progressLabel.setText("0%");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startPart() {
        if (cancelRequested) { finishJob("Export cancelled.", false); return; }
        if (jobIndex >= jobCount) { finishJob("All " + jobCount + " parts saved successfully!", true); return; }
        long[] bounds = ClipPlan.bounds(sourceDurationMs, segmentLengthMs, jobIndex);
        long startMs = bounds[0];
        long endMs = bounds[1];
        MediaItem input = new MediaItem.Builder()
                .setUri(splitVideo.uri)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build())
                .build();
        EditedMediaItem edit = new EditedMediaItem.Builder(input).build();
        String filename = String.format(Locale.US, "%s_Part_%03d.mp4", namePrefix, jobIndex + 1);
        startExport(edit, filename);
    }

    private void startExport(EditedMediaItem edit, String filename) { beginTransform(filename, t -> t.start(edit, workingFile.getAbsolutePath())); }
    private void startExport(Composition composition, String filename) { beginTransform(filename, t -> t.start(composition, workingFile.getAbsolutePath())); }
    private interface TransformStart { void run(Transformer t); }

    private void beginTransform(String filename, TransformStart start) {
        try {
            File base = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (base == null) base = getCacheDir();
            if (!base.exists() && !base.mkdirs()) throw new IllegalStateException("Cannot create temporary folder");
            workingFile = new File(base, "processing_" + System.currentTimeMillis() + ".mp4");
            if (workingFile.exists() && !workingFile.delete()) throw new IllegalStateException("Temporary file already exists");
            messageLabel.setText("Processing " + (jobIndex + 1) + " / " + jobCount + "  •  " + filename);
            transformer = new Transformer.Builder(this)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            transformer = null;
                            if (cancelRequested) { deleteTemp(); finishJob("Export cancelled.", false); return; }
                            saveResultAsync(filename);
                        }
                        @Override public void onError(Composition composition, ExportResult result, ExportException e) {
                            transformer = null;
                            deleteTemp();
                            finishJob("Export error: " + readableError(e), false);
                        }
                    }).build();
            start.run(transformer);
            pollProgress();
        } catch (Exception e) {
            transformer = null;
            deleteTemp();
            finishJob("Unable to start: " + readableError(e), false);
        }
    }

    private void pollProgress() {
        ui.postDelayed(() -> {
            if (!busy || transformer == null || cancelRequested) return;
            try {
                int state = transformer.getProgress(progressHolder);
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    int overall = (int) (((jobIndex + progressHolder.progress / 100f) / jobCount) * 1000);
                    updateProgressUi(overall);
                }
            } catch (Exception ignored) {}
            if (busy && transformer != null) pollProgress();
        }, 500);
    }

    private void saveResultAsync(String filename) {
        copying = true;
        messageLabel.setText("Saving " + filename + " to phone storage…");
        final File file = workingFile;
        io.execute(() -> {
            Uri saved = null;
            String error = null;
            try { saved = saveToMovies(file, filename); }
            catch (Exception e) { error = readableError(e); }
            final Uri out = saved;
            final String failure = error;
            runOnUiThread(() -> {
                copying = false;
                deleteTemp();
                if (out != null) {
                    exported.add(out);
                    jobIndex++;
                    updateProgressUi((int) ((long) jobIndex * 1000 / jobCount));
                    renderSaved();
                }
                if (cancelRequested) finishJob("Export cancelled. " + exported.size() + " video(s) saved.", false);
                else if (failure != null) finishJob("Save error: " + failure, false);
                else if (jobCount == 1) finishJob(splittingJob ? "Video part saved successfully!" : "Merged video saved successfully!", true);
                else startPart();
            });
        });
    }

    private Uri saveToMovies(File file, String filename) throws Exception {
        ContentValues metadata = new ContentValues();
        metadata.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        metadata.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        metadata.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + ALBUM);
        metadata.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri out = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, metadata);
        if (out == null) throw new IllegalStateException("Cannot create an output file in Movies");
        try {
            try (InputStream in = new FileInputStream(file);
                 OutputStream stream = getContentResolver().openOutputStream(out, "w")) {
                if (stream == null) throw new IllegalStateException("Cannot write output video");
                byte[] buffer = new byte[262144];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelRequested) throw new InterruptedException("Cancelled");
                    stream.write(buffer, 0, n);
                }
                stream.flush();
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(out, completed, null, null);
            return out;
        } catch (Exception e) {
            try { getContentResolver().delete(out, null, null); } catch (Exception ignored) {}
            throw e;
        }
    }

    private void deleteTemp() {
        if (workingFile != null) {
            try { if (workingFile.exists()) workingFile.delete(); } catch (Exception ignored) {}
            workingFile = null;
        }
    }

    private void updateProgressUi(int progress) {
        progress = Math.min(1000, Math.max(0, progress));
        bar.updateProgressUi(progress);
        progressLabel.setText(String.format(Locale.US, "%.1f%%", progress / 10.0));
    }

    private void finishJob(String msg, boolean success) {
        busy = false;
        cancelRequested = false;
        transformer = null;
        copying = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setBusyControls();
        if (success) updateProgressUi(1000);
        messageLabel.setText(msg);
        renderSaved();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void setBusyControls() {
        splitPicker.setEnabled(!busy);
        mergePicker.setEnabled(!busy);
        splitStart.setEnabled(!busy);
        mergeStart.setEnabled(!busy);
        splitTabButton.setEnabled(!busy);
        mergeTabButton.setEnabled(!busy);
        minutesEdit.setEnabled(!busy);
        cancelButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        cancelButton.setEnabled(busy && !cancelRequested);
        renderMergeList();
    }

    private void confirmCancel() {
        if (!busy || cancelRequested) return;
        new AlertDialog.Builder(this)
                .setTitle("Cancel export?")
                .setMessage("ပြီးသွားသော Video Parts များကို သိမ်းထားပါမည်။")
                .setNegativeButton("Continue", null)
                .setPositiveButton("Cancel export", (dialog, which) -> cancelExport())
                .show();
    }

    private void cancelExport() {
        if (!busy) return;
        cancelRequested = true;
        cancelButton.setEnabled(false);
        messageLabel.setText("Cancelling…");
        if (transformer != null) {
            transformer.cancel();
            transformer = null;
            deleteTemp();
            finishJob("Export cancelled. " + exported.size() + " video(s) saved.", false);
        } else if (!copying) {
            deleteTemp();
            finishJob("Export cancelled.", false);
        }
        // During a MediaStore copy the worker notices cancelRequested and cleans pending output.
    }

    private void renderSaved() {
        if (exportedList == null) return;
        exportedList.removeAllViews();
        outputTitle.setText("SAVED VIDEOS  •  " + exported.size());
        if (exported.isEmpty()) {
            exportedList.addView(text("Export မလုပ်ရသေးပါ။", 12, PALE, false));
            return;
        }
        for (int i = 0; i < exported.size(); i++) {
            Uri uri = exported.get(i);
            Button open = button("▶  Open saved video " + (i + 1), false);
            exportedList.addView(open, full());
            addSpace(exportedList, 6);
            open.setOnClickListener(v -> {
                Intent view = new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(uri, "video/mp4");
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(view); }
                catch (Exception e) { showMessage("Video player app မတွေ့ပါ။ Gallery ထဲကနေ ဖွင့်ပါ။"); }
            });
        }
    }

    private static String safeStem(String filename) {
        String clean = filename.replaceFirst("(?i)\\.[a-z0-9]{2,5}$", "")
                .replaceAll("[^a-zA-Z0-9_-]+", "_");
        if (clean.length() > 40) clean = clean.substring(0, 40);
        if (clean.isEmpty() || clean.replace("_", "").isEmpty()) clean = "Video";
        return clean + "_" + System.currentTimeMillis();
    }

    private static String readableError(Throwable e) {
        String message = e.getMessage();
        return (message == null || message.isEmpty()) ? e.getClass().getSimpleName() : message;
    }

    private static String formatTime(long ms) {
        if (ms < 0) return "Checking…";
        long seconds = ms / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    private void showMessage(String text) { new AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show(); }
    private int dp(float d) { return (int) (d * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, dp(48)); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(48), 1f); }
    private void addGap(LinearLayout l, int d) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(dp(d), 1)); }
    private void addSpace(LinearLayout l, int d) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(d))); }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setPadding(dp(17), dp(18), dp(17), dp(19));
        l.setBackground(round(CARD, 17));
        return l;
    }
    private TextView text(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private Button button(String text, boolean emphasis) {
        Button v = new Button(this);
        v.setAllCaps(false);
        v.setText(text);
        v.setTextSize(13);
        v.setTextColor(emphasis ? BG : TEXT);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setBackground(round(emphasis ? ACCENT : DARK_BUTTON, 12));
        v.setPadding(dp(9), 0, dp(9), 0);
        return v;
    }
    private Button tiny(String s) {
        Button b = button(s, false);
        b.setTextSize(18);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(33), dp(37));
        p.leftMargin = dp(4);
        b.setLayoutParams(p);
        return b;
    }

    @Override public void onBackPressed() {
        if (busy) confirmCancel(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (transformer != null) {
            try { transformer.cancel(); } catch (Exception ignored) {}
            transformer = null;
        }
        cancelRequested = true;
        if (!copying) deleteTemp();
        io.shutdown();
        super.onDestroy();
    }
}
