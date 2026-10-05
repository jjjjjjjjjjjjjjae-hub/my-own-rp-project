package kz.almas.netoptimizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkGuardianService extends Service {
    private static final String CH_MONITOR = "almas_net_monitor";
    private static final String CH_ALERT = "almas_net_alert";
    private static final int NOTIF_MONITOR = 4101;
    private static final int NOTIF_HOTSPOT = 4102;
    private static final long ANALYZE_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long HOTSPOT_CHECK_MS = 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private String lastHotspotSignature = "";

    private final Runnable analyzerLoop = new Runnable() {
        @Override public void run() {
            pool.execute(() -> analyzeNetwork(false));
            handler.postDelayed(this, ANALYZE_INTERVAL_MS);
        }
    };

    private final Runnable hotspotLoop = new Runnable() {
        @Override public void run() {
            pool.execute(NetworkGuardianService.this::checkHotspot);
            handler.postDelayed(this, HOTSPOT_CHECK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(NOTIF_MONITOR, monitorNotification("Phone Analyzer іске қосылды", "Желі өлшенуде..."));
        handler.post(analyzerLoop);
        handler.postDelayed(hotspotLoop, 15000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        super.onDestroy();
    }

    private void analyzeNetwork(boolean force) {
        if (!hasInternet()) {
            updateMonitor("Интернет жоқ", "Қосылған желі тексерілмеді");
            return;
        }
        Measure m = measure();
        if (!m.valid || m.downMbps < 0) {
            updateMonitor("Phone Analyzer", "Жылдамдықты өлшей алмадым");
            return;
        }

        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        float previous = sp.getFloat(MainActivity.KEY_LAST_MBPS, -1f);
        double smoothed = previous > 0 ? previous * 0.60 + m.downMbps * 0.40 : m.downMbps;
        int quality = MainActivity.recommendQuality(smoothed, m.jitter, m.loss);
        sp.edit()
                .putFloat(MainActivity.KEY_LAST_MBPS, (float) smoothed)
                .putInt(MainActivity.KEY_RECOMMENDED_QUALITY, quality)
                .apply();

        String title = String.format(Locale.US, "%.1f Mbps • %dp дейін", smoothed, quality);
        String text = String.format(Locale.US, "Ping %.0f ms • Jitter %.0f ms • Loss %d%%", m.ping, m.jitter, m.loss);
        updateMonitor(title, text);
    }

    private Measure measure() {
        List<Long> times = new ArrayList<>();
        int failures = 0;
        for (int i = 0; i < 4; i++) {
            long t = probe("https://www.google.com/generate_204");
            if (t >= 0) times.add(t); else failures++;
        }
        if (times.isEmpty()) return Measure.invalid();

        double avg = 0;
        for (long t : times) avg += t;
        avg /= times.size();
        double jitter = 0;
        for (int i = 1; i < times.size(); i++) jitter += Math.abs(times.get(i) - times.get(i - 1));
        if (times.size() > 1) jitter /= (times.size() - 1);
        int loss = (int) Math.round(failures * 100.0 / 4.0);
        double down = smallDownloadMbps(180000);
        return new Measure(true, avg, jitter, loss, down);
    }

    private long probe(String urlStr) {
        HttpURLConnection c = null;
        try {
            long start = System.nanoTime();
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setRequestMethod("GET");
            c.setUseCaches(false);
            int code = c.getResponseCode();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return code >= 200 && code < 500 ? ms : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private double smallDownloadMbps(int wantedBytes) {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=" + wantedBytes).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            long start = System.nanoTime();
            in = c.getInputStream();
            byte[] buf = new byte[8192];
            long bytes = 0;
            int n;
            while ((n = in.read(buf)) > 0 && bytes < wantedBytes) bytes += n;
            double sec = (System.nanoTime() - start) / 1_000_000_000.0;
            if (bytes < 10000 || sec <= 0) return -1;
            return (bytes * 8.0 / 1_000_000.0) / sec;
        } catch (Exception e) {
            return -1;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            if (c != null) c.disconnect();
        }
    }

    private boolean hasInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void checkHotspot() {
        if (!isCellularActive()) return;
        String raw = rootOutput("ip neigh show 2>/dev/null");
        if (raw == null || raw.isEmpty()) return;
        StringBuilder sig = new StringBuilder();
        int clients = 0;
        for (String line : raw.split("\\r?\\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 4 || !line.contains("lladdr")) continue;
            String ip = p[0];
            if (!isPrivate(ip)) continue;
            String iface = "";
            for (int i = 0; i < p.length - 1; i++) if ("dev".equals(p[i])) { iface = p[i + 1]; break; }
            if (iface.startsWith("rmnet") || iface.startsWith("ccmni") || iface.startsWith("pdp") || iface.startsWith("lo") || iface.startsWith("tun")) continue;
            clients++;
            sig.append(iface).append(':').append(ip).append(';');
        }
        if (clients == 0) return;
        String s = sig.toString();
        if (s.equals(lastHotspotSignature)) return;
        lastHotspotSignature = s;
        showHotspotNotification(clients);
    }

    private boolean isCellularActive() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private boolean isPrivate(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private String rootOutput(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            int code = p.waitFor();
            return code == 0 ? sb.toString() : "";
        } catch (Exception e) {
            return "";
        } finally {
            if (p != null) p.destroy();
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel monitor = new NotificationChannel(CH_MONITOR, "Almas Network Monitor", NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("Интернет жылдамдығы мен ұсынылатын видео сапасын көрсетеді");
        NotificationChannel alert = new NotificationChannel(CH_ALERT, "Almas Network Alerts", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Hotspot және видео сапасы туралы ескертулер");
        nm.createNotificationChannel(monitor);
        nm.createNotificationChannel(alert);
    }

    private Notification monitorNotification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH_MONITOR)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateMonitor(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_MONITOR, monitorNotification(title, text));
    }

    private void showHotspotNotification(int clients) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("📡 Hotspot Telecom")
                .setContentText(clients + " клиент қосылды. Әр адамға Mbps лимитін таңда.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_HOTSPOT, n);
    }

    private static class Measure {
        final boolean valid;
        final double ping;
        final double jitter;
        final int loss;
        final double downMbps;
        Measure(boolean valid, double ping, double jitter, int loss, double downMbps) {
            this.valid = valid; this.ping = ping; this.jitter = jitter; this.loss = loss; this.downMbps = downMbps;
        }
        static Measure invalid() { return new Measure(false, 0, 0, 100, -1); }
    }
}
