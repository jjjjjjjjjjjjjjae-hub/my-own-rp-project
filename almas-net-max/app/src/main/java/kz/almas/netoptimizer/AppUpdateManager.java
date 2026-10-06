package kz.almas.netoptimizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppUpdateManager {
    private static final String UPDATE_URL =
            "https://raw.githubusercontent.com/jjjjjjjjjjjjjjae-hub/my-own-rp-project/main/generated/almas-telekom-update.json";
    private static final String PREFS = "almas_update_prefs";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_EXPECTED_SHA = "expected_sha";
    private static final String KEY_PENDING_URI = "pending_uri";

    private final Activity activity;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final DownloadManager downloadManager;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            long wantedId = prefs().getLong(KEY_DOWNLOAD_ID, -2L);
            if (completedId == wantedId) handleDownloadedApk(completedId);
        }
    };

    public AppUpdateManager(Activity activity) {
        this.activity = activity;
        this.downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        registerReceiver();
    }

    public void checkForUpdates(boolean manual) {
        pool.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(UPDATE_URL + "?t=" + System.currentTimeMillis());
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setUseCaches(false);
                c.setRequestProperty("User-Agent", "ALMAS-TELEKOM/" + currentVersionName());
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

                String json = readAll(c.getInputStream());
                JSONObject o = new JSONObject(json);
                long remoteCode = o.getLong("versionCode");
                String remoteName = o.optString("versionName", String.valueOf(remoteCode));
                String apkUrl = o.getString("apkUrl");
                String notes = o.optString("notes", "Жаңа нұсқа дайын.");
                String sha256 = o.optString("sha256", "").trim().toLowerCase(Locale.US);

                if (remoteCode > currentVersionCode()) {
                    activity.runOnUiThread(() -> showUpdateDialog(remoteName, notes, apkUrl, sha256));
                } else if (manual) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "ALMAS TELEKOM жаңартылған • v" + currentVersionName(),
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (manual) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Жаңартуды тексере алмадым: " + e.getClass().getSimpleName(),
                            Toast.LENGTH_LONG).show());
                }
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private void showUpdateDialog(String versionName, String notes, String apkUrl, String sha256) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("ALMAS TELEKOM v" + versionName)
                .setMessage(notes + "\n\nAPK автоматты жүктеледі. Android орнатуды растауды сұрайды.")
                .setPositiveButton("ЖҮКТЕУ ЖӘНЕ ЖАҢАРТУ", (d, w) -> downloadUpdate(versionName, apkUrl, sha256))
                .setNegativeButton("КЕЙІН", null)
                .show();
    }

    private void downloadUpdate(String versionName, String apkUrl, String sha256) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("ALMAS TELEKOM v" + versionName);
            request.setDescription("Жаңарту жүктелуде...");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            String fileName = "ALMAS-TELEKOM-v" + versionName.replaceAll("[^0-9A-Za-z._-]", "_") + ".apk";
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = downloadManager.enqueue(request);
            prefs().edit()
                    .putLong(KEY_DOWNLOAD_ID, id)
                    .putString(KEY_EXPECTED_SHA, sha256)
                    .remove(KEY_PENDING_URI)
                    .apply();
            Toast.makeText(activity, "Жаңарту жүктеле бастады", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Жүктеу қатесі: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    public void resumePendingInstall() {
        String pending = prefs().getString(KEY_PENDING_URI, "");
        if (!pending.isEmpty() && canInstallPackages()) {
            prefs().edit().remove(KEY_PENDING_URI).apply();
            launchInstaller(Uri.parse(pending));
            return;
        }

        long id = prefs().getLong(KEY_DOWNLOAD_ID, -1L);
        if (id <= 0) return;
        try (Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) handleDownloadedApk(id);
            }
        } catch (Exception ignored) {}
    }

    private void handleDownloadedApk(long id) {
        Uri uri = downloadManager.getUriForDownloadedFile(id);
        if (uri == null) {
            Toast.makeText(activity, "Жүктелген APK табылмады", Toast.LENGTH_LONG).show();
            return;
        }

        String expected = prefs().getString(KEY_EXPECTED_SHA, "");
        if (!expected.isEmpty()) {
            pool.execute(() -> {
                try {
                    String actual = sha256(uri);
                    if (!expected.equalsIgnoreCase(actual)) {
                        downloadManager.remove(id);
                        prefs().edit().remove(KEY_DOWNLOAD_ID).remove(KEY_EXPECTED_SHA).apply();
                        activity.runOnUiThread(() -> Toast.makeText(activity,
                                "Қауіпсіздік тексеруі өтпеді: APK SHA-256 сәйкес емес",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    activity.runOnUiThread(() -> prepareInstall(uri));
                } catch (Exception e) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "APK тексеру қатесі", Toast.LENGTH_LONG).show());
                }
            });
        } else {
            prepareInstall(uri);
        }
    }

    private void prepareInstall(Uri uri) {
        prefs().edit()
                .putString(KEY_PENDING_URI, uri.toString())
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_EXPECTED_SHA)
                .apply();

        if (!canInstallPackages()) {
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(i);
                    Toast.makeText(activity,
                            "«Осы көзден орнатуға рұқсат беру» параметрін қосыңыз",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(activity, "APK орнатуға рұқсат қажет", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        prefs().edit().remove(KEY_PENDING_URI).apply();
        launchInstaller(uri);
    }

    private void launchInstaller(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(activity,
                    "Android орнатқышын аша алмадым: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls();
    }

    private long currentVersionCode() throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
        }
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
    }

    private String currentVersionName() {
        try {
            String v = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            return v == null ? "?" : v;
        } catch (Exception e) {
            return "?";
        }
    }

    private String sha256(Uri uri) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("No stream");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
        in.close();
        return sb.toString();
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void registerReceiver() {
        try {
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(downloadReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(downloadReceiver, f);
            }
            receiverRegistered = true;
        } catch (Exception ignored) {}
    }

    public void destroy() {
        if (receiverRegistered) {
            try { activity.unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        pool.shutdownNow();
    }
}
