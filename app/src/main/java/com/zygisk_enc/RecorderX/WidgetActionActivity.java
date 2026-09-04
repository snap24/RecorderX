package com.zygisk_enc.RecorderX;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class WidgetActionActivity extends Activity {

    public static final String ACTION_OPEN_LAST_REC = "com.zygisk_enc.RecorderX.ACTION_OPEN_LAST_REC";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.updateResources(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent() != null ? getIntent().getAction() : null;
        if (ACTION_OPEN_LAST_REC.equals(action)) {
            openLastRecording();
        }

        finish();
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    private void openLastRecording() {
        try {
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RecorderX");
            if (!folder.exists() || folder.listFiles() == null) {
                Toast.makeText(this, R.string.toast_no_recordings, Toast.LENGTH_SHORT).show();
                return;
            }

            File[] files = folder.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".ts");
            });

            if (files == null || files.length == 0) {
                Toast.makeText(this, R.string.toast_no_recordings, Toast.LENGTH_SHORT).show();
                return;
            }

            File lastFile = files[0];
            for (int i = 1; i < files.length; i++) {
                if (files[i].lastModified() > lastFile.lastModified()) {
                    lastFile = files[i];
                }
            }

            Uri videoUri = resolveMediaUri(lastFile);
            if (videoUri == null) {
                videoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", lastFile);
            }

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(videoUri, "video/*");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(viewIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_video_player, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_no_recordings, Toast.LENGTH_SHORT).show();
        }
    }

    private Uri resolveMediaUri(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] projection = new String[]{MediaStore.Video.Media._ID};
            String selection = MediaStore.Video.Media.DATA + "=? OR " + MediaStore.Video.Media.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{file.getAbsolutePath(), file.getName()};
            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    MediaStore.Video.Media._ID + " DESC")) {
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
