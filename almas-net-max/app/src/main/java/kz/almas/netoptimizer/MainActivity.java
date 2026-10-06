package kz.almas.netoptimizer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 100;
    static final String PREFS = "almas_net_prefs";
    private static final String KEY_BACKUP_APN_ID = "backup_apn_id";
    private static final String KEY_BACKUP_APN_NAME = "backup_apn_name";
    static final String KEY_RECOMMENDED_QUALITY = "recommended_quality";
    static final String KEY_LAST_MBPS = "last_mbps";
    private static final String KEY_HOTSPOT_LIMIT = "hotspot_limit_mbps";

    private TextView tvNetwork, tvScore, tvTest, tvApn, tvHotspot, tvAnalyzer;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private int lastSignalScore = 50;
    private int bestDbmSeen = -140;
    private boolean hotspotDialogOpen = false;
    private String lastPromptSignature = "";
    private AppUpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvNetwork = findViewById(R.id.tvNetwork);
        tvScore = findViewById(R.id.tvScore);
        tvTest = findViewById(R.id.tvTest);
        tvApn = findViewById(R.id.tvApn);
        tvHotspot = findViewById(R.id.tvHotspot);
        tvAnalyzer = findViewById(R.id.tvAnalyzer);

        Button btnUpdate = findViewById(R.id.btnUpdate);
        Button btnHotspot = findViewById(R.id.btnHotspot);
        Button btnHotspotOff = findViewById(R.id.btnHotspotOff);
        Button btnAnalyzer = findViewById(R.id.btnAnalyzer);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnTest = findViewById(R.id.btnTest);
        Button btnSignal = findViewById(R.id.btnSignal);
        Button btnApn = findViewById(R.id.btnApn);
        Button btnMaxApn = findViewById(R.id.btnMaxApn);
        Button btnRestoreApn = findViewById(R.id.btnRestoreApn);

        updateManager = new AppUpdateManager(this);
        btnUpdate.setOnClickListener(v -> updateManager.checkForUpdates(true));
        btnHotspot.setOnClickListener(v -> promptHotspotLimit(false));
        btnHotspotOff.setOnClickListener(v -> removeHotspotLimit());
        btnAnalyzer.setOnClickListener(v -> runAnalyzerNow());
        btnAccessibility.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Accessibility баптауын аша алмадым", Toast.LENGTH_SHORT).show();
            }
        });
        btnTest.setOnClickListener(v -> runNetworkTest());
        btnSignal.setOnClickListener(v -> {
            showTransport();
            refreshSignal();
        });
        btnApn.setOnClickListener(v -> readApnWithRoot());
        btnMaxApn.setOnClickListener(v -> optimizeApnAutomatically());
        btnRestoreApn.setOnClickListener(v -> restoreBackupApn());

        requestNeededPermissions();
        showTransport();
        refreshSignal();
        startGuardianService();
        refreshAnalyzerCard();
        updateManager.checkForUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAnalyzerCard();
        checkHotspotAndPrompt();
        if (updateManager != null) updateManager.resumePendingInstall();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) updateManager.destroy();
        pool.shutdownNow();
        super.onDestroy();
    }

    private void requestNeededPermissions() {
        List<String> wanted = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                wanted.add(Manifest.permission.READ_PHONE_STATE);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!wanted.isEmpty()) requestPermissions(wanted.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            showTransport();
            refreshSignal();
            startGuardianService();
        }
    }

    private void startGuardianService() {
        try {
            Intent i = new Intent(this, NetworkGuardianService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            tvAnalyzer.setText("Phone Analyzer фондық қызметі іске қосылмады: " + e.getClass().getSimpleName());
        }
    }

    private void refreshAnalyzerCard() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        float mbps = sp.getFloat(KEY_LAST_MBPS, -1f);
        int q = sp.getInt(KEY_RECOMMENDED_QUALITY, 0);
        if (mbps < 0 || q == 0) {
            tvAnalyzer.setText("📱 PHONE ANALYZER\nҚолданба желіні бақылауды бастады. Алғашқы өлшем дайындалуда...");
        } else {
            tvAnalyzer.setText(String.format(Locale.US,
                    "📱 PHONE ANALYZER\nБолжамды тұрақты жылдамдық: %.1f Mbps\nҰсынылатын максимум: %dp\nЖоғары сапа таңдалса Accessibility арқылы ескерту беріледі.",
                    mbps, q));
        }
    }

    private void runAnalyzerNow() {
        tvAnalyzer.setText("📱 PHONE ANALYZER\nЖылдамдық пен тұрақтылық өлшенуде...");
        pool.execute(() -> {
            BenchmarkResult r = benchmarkNetwork(true);
            if (!r.valid || r.downMbps < 0) {
                runOnUiThread(() -> tvAnalyzer.setText("📱 PHONE ANALYZER\nИнтернет жылдамдығын өлшей алмадым."));
                return;
            }
            int q = recommendQuality(r.downMbps, r.jitter, r.loss);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putFloat(KEY_LAST_MBPS, (float) r.downMbps)
                    .putInt(KEY_RECOMMENDED_QUALITY, q)
                    .apply();
            runOnUiThread(() -> {
                tvAnalyzer.setText(String.format(Locale.US,
                        "📱 PHONE ANALYZER\nЖылдамдық: %.1f Mbps\nJitter: %.0f ms | Loss: %d%%\nҰсынылатын максимум: %dp",
                        r.downMbps, r.jitter, r.loss, q));
                tvTest.setText(r.details());
            });
        });
    }

    static int recommendQuality(double mbps, double jitter, int loss) {
        double effective = mbps;
        if (jitter > 80) effective *= 0.60;
        else if (jitter > 40) effective *= 0.75;
        else if (jitter > 20) effective *= 0.88;
        if (loss >= 8) effective *= 0.55;
        else if (loss >= 4) effective *= 0.72;
        else if (loss >= 2) effective *= 0.85;

        if (effective < 0.7) return 260;
        if (effective < 1.5) return 360;
        if (effective < 3.0) return 480;
        if (effective < 6.0) return 720;
        if (effective < 14.0) return 1080;
        if (effective < 24.0) return 1440;
        return 2160;
    }

    private void checkHotspotAndPrompt() {
        if (hotspotDialogOpen) return;
        pool.execute(() -> {
            if (!isCellularActive()) return;
            HotspotState hs = findHotspotState();
            if (hs == null || hs.clients.isEmpty()) return;
            String signature = hs.iface + ":" + hs.clients.toString();
            if (signature.equals(lastPromptSignature)) return;
            lastPromptSignature = signature;
            runOnUiThread(() -> promptHotspotLimit(true));
        });
    }

    private void promptHotspotLimit(boolean automatic) {
        if (hotspotDialogOpen) return;
        hotspotDialogOpen = true;
        pool.execute(() -> {
            HotspotState hs = findHotspotState();
            runOnUiThread(() -> {
                if (hs == null || hs.clients.isEmpty()) {
                    hotspotDialogOpen = false;
                    tvHotspot.setText("📡 HOTSPOT TELECOM\nҚосылған hotspot клиенттері табылмады. Хотспотты қосып, адамды қосқаннан кейін қайта бас.");
                    if (!automatic) Toast.makeText(this, "Hotspot клиенті табылмады", Toast.LENGTH_SHORT).show();
                    return;
                }

                EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                float old = getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(KEY_HOTSPOT_LIMIT, 5f);
                input.setText(String.format(Locale.US, "%.1f", old));
                input.setSelectAllOnFocus(true);

                new AlertDialog.Builder(this)
                        .setTitle("📡 Hotspot Telecom")
                        .setMessage("Қосылған адам саны: " + hs.clients.size()
                                + "\nИнтерфейс: " + hs.iface
                                + "\n\nӘР АДАМҒА қанша Mbps берейін?\nМысалы: 3 = әр клиентке 3 Mbps")
                        .setView(input)
                        .setPositiveButton("ҚОЛДАНУ", (d, w) -> {
                            hotspotDialogOpen = false;
                            double mbps;
                            try { mbps = Double.parseDouble(input.getText().toString().replace(',', '.')); }
                            catch (Exception e) { mbps = 0; }
                            if (mbps < 0.2 || mbps > 500) {
                                Toast.makeText(this, "0.2–500 Mbps аралығын жаз", Toast.LENGTH_LONG).show();
                                return;
                            }
                            applyHotspotLimit(hs, mbps);
                        })
                        .setNegativeButton("БАС ТАРТУ", (d, w) -> hotspotDialogOpen = false)
                        .setOnCancelListener(d -> hotspotDialogOpen = false)
                        .show();
            });
        });
    }

    private HotspotState findHotspotState() {
        CmdResult r = root("ip neigh show 2>/dev/null");
        if (!r.ok() || empty(r.output)) return null;
        Map<String, List<String>> byIface = new LinkedHashMap<>();
        for (String line : r.output.split("\\r?\\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 3) continue;
            String ip = p[0];
            if (!isPrivateIpv4(ip)) continue;
            String iface = "";
            for (int i = 0; i < p.length - 1; i++) if ("dev".equals(p[i])) { iface = p[i + 1]; break; }
            if (empty(iface) || isUpstreamIface(iface)) continue;
            if (!line.contains("lladdr")) continue;
            byIface.computeIfAbsent(iface, k -> new ArrayList<>()).add(ip);
        }
        String bestIface = null;
        List<String> best = null;
        for (Map.Entry<String, List<String>> e : byIface.entrySet()) {
            if (best == null || e.getValue().size() > best.size()) {
                bestIface = e.getKey();
                best = e.getValue();
            }
        }
        return best == null ? null : new HotspotState(bestIface, best);
    }

    private boolean isPrivateIpv4(String ip) {
        if (ip == null || !ip.matches("[0-9.]+")) return false;
        return ip.startsWith("10.") || ip.startsWith("192.168.") ||
                ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private boolean isUpstreamIface(String iface) {
        String s = iface.toLowerCase(Locale.US);
        return s.startsWith("rmnet") || s.startsWith("ccmni") || s.startsWith("pdp") ||
                s.startsWith("lo") || s.startsWith("dummy") || s.startsWith("tun") ||
                s.startsWith("ip6") || s.startsWith("sit") || s.startsWith("bond");
    }

    private void applyHotspotLimit(HotspotState hs, double mbps) {
        tvHotspot.setText("📡 HOTSPOT TELECOM\nЛимит қолданылуда... Root рұқсатын бер.");
        pool.execute(() -> {
            if (!hs.iface.matches("[A-Za-z0-9_.-]+")) {
                setHotspotText("Қауіпсіздік тексерісі: hotspot интерфейсі жарамсыз.");
                return;
            }
            CmdResult id = root("id");
            if (!id.ok() || !id.output.contains("uid=0")) {
                setHotspotText("Root рұқсаты жоқ. Magisk сұранысына Allow бер.");
                return;
            }
            CmdResult tcCheck = root("command -v tc || which tc || ls /system/bin/tc 2>/dev/null");
            if (!tcCheck.ok() || empty(tcCheck.output)) {
                setHotspotText("Бұл ROM/kernel ішінде tc жоқ. Hotspot жылдамдығын ядро деңгейінде шектеу мүмкін емес.");
                return;
            }

            String dev = hs.iface;
            root("tc qdisc del dev " + dev + " root 2>/dev/null; tc qdisc del dev " + dev + " ingress 2>/dev/null");
            CmdResult base = root("tc qdisc add dev " + dev + " root handle 1: htb default 999 && " +
                    "tc class add dev " + dev + " parent 1: classid 1:1 htb rate 1000mbit ceil 1000mbit && " +
                    "tc class add dev " + dev + " parent 1:1 classid 1:999 htb rate 1000mbit ceil 1000mbit && " +
                    "tc qdisc add dev " + dev + " handle ffff: ingress");
            if (!base.ok()) {
                setHotspotText("tc HTB іске қосылмады. Kernel HTB/ingress қолдауын тексер.\n" + shortError(base.output));
                return;
            }

            int minor = 10;
            int okDown = 0;
            int okUp = 0;
            String rate = String.format(Locale.US, "%.3fmbit", mbps);
            for (String ip : hs.clients) {
                if (!isPrivateIpv4(ip)) continue;
                int idNum = minor++;
                CmdResult down = root("tc class add dev " + dev + " parent 1:1 classid 1:" + idNum +
                        " htb rate " + rate + " ceil " + rate + " burst 64k && " +
                        "tc filter add dev " + dev + " protocol ip parent 1: prio 1 u32 match ip dst " + ip + "/32 flowid 1:" + idNum);
                if (down.ok()) okDown++;

                CmdResult up = root("tc filter add dev " + dev + " parent ffff: protocol ip prio 1 u32 match ip src " + ip +
                        "/32 police rate " + rate + " burst 128k drop flowid :1");
                if (up.ok()) okUp++;
            }

            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(KEY_HOTSPOT_LIMIT, (float) mbps).apply();
            setHotspotText(String.format(Locale.US,
                    "✅ HOTSPOT TELECOM ҚОСУЛЫ\nКлиент: %d\nӘр адамға: %.1f Mbps\nDownload лимиті: %d/%d\nUpload лимиті: %d/%d\nИнтерфейс: %s",
                    hs.clients.size(), mbps, okDown, hs.clients.size(), okUp, hs.clients.size(), dev));
        });
    }

    private void removeHotspotLimit() {
        tvHotspot.setText("📡 HOTSPOT TELECOM\nЛимит алынып жатыр...");
        pool.execute(() -> {
            HotspotState hs = findHotspotState();
            if (hs == null) {
                setHotspotText("Hotspot интерфейсі табылмады. Егер хотспот өшірулі болса, лимит те белсенді емес.");
                return;
            }
            CmdResult r = root("tc qdisc del dev " + hs.iface + " root 2>/dev/null; tc qdisc del dev " + hs.iface + " ingress 2>/dev/null; true");
            setHotspotText(r.code == 0 ? "✅ Hotspot жылдамдық лимиті алынды." : "Лимитті алу қатесі: " + shortError(r.output));
        });
    }

    private void setHotspotText(String s) {
        runOnUiThread(() -> tvHotspot.setText("📡 HOTSPOT TELECOM\n" + s));
    }

    private void showTransport() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        String type = "Белгісіз";
        boolean validated = false;
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "Мобильді интернет";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "Wi‑Fi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "Ethernet";
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        tvNetwork.setText("Қосылу: " + type + "\nИнтернет: " + (validated ? "бар ✓" : "тексерілмеді / жоқ"));
    }

    private void refreshSignal() {
        pool.execute(() -> {
            StringBuilder out = new StringBuilder();
            int bestDbm = -140;
            String radio = "Белгісіз";
            try {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                String operator = tm.getNetworkOperatorName();
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> tvNetwork.setText("Сигнал үшін Location рұқсатын бер."));
                    return;
                }
                try { radio = networkTypeName(tm.getDataNetworkType()); } catch (Exception ignored) { }
                List<CellInfo> cells = tm.getAllCellInfo();
                if (cells != null) {
                    for (CellInfo c : cells) {
                        if (c instanceof CellInfoLte) {
                            bestDbm = Math.max(bestDbm, ((CellInfoLte)c).getCellSignalStrength().getDbm());
                            if ("Белгісіз".equals(radio)) radio = "4G/LTE";
                        } else if (Build.VERSION.SDK_INT >= 29 && c instanceof CellInfoNr) {
                            bestDbm = Math.max(bestDbm, ((CellSignalStrengthNr)((CellInfoNr)c).getCellSignalStrength()).getDbm());
                            radio = "5G/NR";
                        } else if (c instanceof CellInfoWcdma) {
                            bestDbm = Math.max(bestDbm, ((CellInfoWcdma)c).getCellSignalStrength().getDbm());
                            if ("Белгісіз".equals(radio)) radio = "3G/H/H+";
                        } else if (c instanceof CellInfoGsm) {
                            bestDbm = Math.max(bestDbm, ((CellInfoGsm)c).getCellSignalStrength().getDbm());
                            if ("Белгісіз".equals(radio)) radio = "2G/GSM";
                        }
                    }
                }
                if (bestDbm <= -140) {
                    lastSignalScore = 50;
                    out.append("Оператор: ").append(empty(operator) ? "—" : operator).append("\nЖелі: ").append(radio)
                            .append("\nСигнал: дерек жоқ\nLocation қосулы екенін тексер.");
                } else {
                    if (bestDbm > bestDbmSeen) bestDbmSeen = bestDbm;
                    lastSignalScore = scoreSignal(bestDbm);
                    out.append("Оператор: ").append(empty(operator) ? "—" : operator).append("\nЖелі: ").append(radio)
                            .append("\nСигнал: ").append(bestDbm).append(" dBm")
                            .append("\nБаға: ").append(signalLabel(bestDbm))
                            .append("\nОсы сессиядағы ең жақсысы: ").append(bestDbmSeen).append(" dBm");
                }
            } catch (Exception e) { out.append("Сигналды оқу қатесі: ").append(e.getClass().getSimpleName()); }
            String text = out.toString();
            runOnUiThread(() -> tvNetwork.setText(text));
        });
    }

    private String networkTypeName(int t) {
        switch (t) {
            case TelephonyManager.NETWORK_TYPE_GPRS: return "2G/GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "2G/EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G/UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "3G/H";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "3G/HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA: return "3G/HSPA";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G/H+";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G/LTE";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G/NR";
            default: return "Белгісіз";
        }
    }

    private boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private int scoreSignal(int dbm) {
        if (dbm >= -80) return 100;
        if (dbm <= -120) return 10;
        return Math.max(10, Math.min(100, 100 - ((-80 - dbm) * 90 / 40)));
    }
    private String signalLabel(int dbm) {
        if (dbm >= -85) return "Өте жақсы";
        if (dbm >= -95) return "Жақсы";
        if (dbm >= -105) return "Орташа";
        if (dbm >= -115) return "Нашар";
        return "Өте нашар";
    }

    private void runNetworkTest() {
        tvTest.setText("Тест жүріп жатыр...");
        pool.execute(() -> {
            BenchmarkResult r = benchmarkNetwork(true);
            if (!r.valid) {
                runOnUiThread(() -> { tvTest.setText("Интернетке тест қосыла алмады."); tvScore.setText("Network Score: 0/100"); });
                return;
            }
            int overall = Math.max(0, Math.min(100, (int)Math.round(lastSignalScore * 0.25 + r.score * 0.75)));
            runOnUiThread(() -> { tvTest.setText(r.details()); tvScore.setText("Network Score: " + overall + "/100\n" + scoreLabel(overall)); });
        });
    }

    private BenchmarkResult benchmarkNetwork(boolean withDownload) {
        List<Long> times = new ArrayList<>();
        int probes = 7, failures = 0;
        for (int i = 0; i < probes; i++) {
            long ms = probe("https://www.google.com/generate_204");
            if (ms >= 0) times.add(ms); else failures++;
        }
        if (times.isEmpty()) return BenchmarkResult.invalid();
        double avg = 0; for (long t : times) avg += t; avg /= times.size();
        double jitter = 0; for (int i = 1; i < times.size(); i++) jitter += Math.abs(times.get(i) - times.get(i - 1));
        if (times.size() > 1) jitter /= (times.size() - 1);
        int loss = (int)Math.round(failures * 100.0 / probes);
        double down = withDownload ? smallDownloadMbps(750000) : -1;
        int latencyScore = avg <= 45 ? 100 : avg >= 450 ? 5 : (int)(100 - ((avg - 45) * 95 / 405));
        int jitterScore = jitter <= 8 ? 100 : jitter >= 120 ? 5 : (int)(100 - ((jitter - 8) * 95 / 112));
        int lossScore = Math.max(0, 100 - loss * 5);
        int speedScore = down < 0 ? 60 : down >= 50 ? 100 : down >= 20 ? 90 : down >= 10 ? 80 : down >= 5 ? 65 : down >= 2 ? 50 : down >= 1 ? 35 : 20;
        int score = (int)Math.round(latencyScore * 0.35 + jitterScore * 0.25 + lossScore * 0.25 + speedScore * 0.15);
        return new BenchmarkResult(true, avg, jitter, loss, down, Math.max(0, Math.min(100, score)));
    }

    private long probe(String urlStr) {
        HttpURLConnection c = null;
        try {
            long start = System.nanoTime();
            c = (HttpURLConnection)new URL(urlStr).openConnection();
            c.setConnectTimeout(3000); c.setReadTimeout(3000); c.setRequestMethod("GET"); c.setUseCaches(false);
            int code = c.getResponseCode();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return (code >= 200 && code < 500) ? ms : -1;
        } catch (Exception e) { return -1; }
        finally { if (c != null) c.disconnect(); }
    }

    private double smallDownloadMbps(int wantedBytes) {
        HttpURLConnection c = null; InputStream in = null;
        try {
            URL u = new URL("https://speed.cloudflare.com/__down?bytes=" + wantedBytes);
            c = (HttpURLConnection)u.openConnection(); c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setUseCaches(false);
            long start = System.nanoTime(); in = c.getInputStream(); byte[] buf = new byte[16384]; long bytes = 0; int n;
            while ((n = in.read(buf)) > 0 && bytes < wantedBytes) bytes += n;
            double sec = (System.nanoTime() - start) / 1_000_000_000.0;
            if (bytes < 10000 || sec <= 0) return -1;
            return (bytes * 8.0 / 1_000_000.0) / sec;
        } catch (Exception e) { return -1; }
        finally { try { if (in != null) in.close(); } catch (Exception ignored) { } if (c != null) c.disconnect(); }
    }

    private String scoreLabel(int s) {
        if (s >= 90) return "Өте тұрақты";
        if (s >= 75) return "Жақсы";
        if (s >= 55) return "Орташа";
        if (s >= 35) return "Нашар";
        return "Өте нашар";
    }

    private boolean isCellularActive() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork(); NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }
    private boolean hasValidatedCellular() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork(); NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void readApnWithRoot() {
        tvApn.setText("Root рұқсаты сұралуы мүмкін...");
        pool.execute(() -> {
            CmdResult r = root("content query --uri content://telephony/carriers/preferapn --projection _id:name:apn:type:protocol:roaming_protocol:numeric");
            String result = r.ok() && !empty(r.output) ? "Қазіргі APN (root):\n" + r.output.trim() + "\n\nMAX APN AUTO тұрақтылық тестін өзі жасайды."
                    : "Root берілмеді немесе APN провайдеріне қолжетім жоқ.\n" + shortError(r.output);
            runOnUiThread(() -> tvApn.setText(result));
        });
    }

    private void optimizeApnAutomatically() {
        tvApn.setText("MAX APN: дайындалуда... Root сұранысын рұқсат ет.");
        pool.execute(() -> {
            if (!isCellularActive()) { setApnText("MAX APN үшін Wi‑Fi-ды өшіріп, мобильді интернетті қос."); return; }
            CmdResult idResult = root("id");
            if (!idResult.ok() || !idResult.output.contains("uid=0")) { setApnText("Root рұқсаты жоқ. Magisk сұранысына Allow бер."); return; }
            TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            String numeric; try { numeric = tm.getNetworkOperator(); } catch (Exception e) { numeric = ""; }
            if (empty(numeric)) { setApnText("Оператор MCC/MNC анықталмады."); return; }
            CmdResult currentRaw = root("content query --uri content://telephony/carriers/preferapn --projection _id:name:apn:type:protocol:roaming_protocol:numeric");
            ApnProfile current = firstProfile(currentRaw.output);
            if (current == null || current.id < 0) { setApnText("Қазіргі APN ID оқылмады."); return; }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_BACKUP_APN_ID, current.id).putString(KEY_BACKUP_APN_NAME, current.label()).apply();
            String safeNumeric = numeric.replace("'", "");
            CmdResult allRaw = root("content query --uri content://telephony/carriers --projection _id:name:apn:type:protocol:roaming_protocol:numeric --where \"numeric='" + safeNumeric + "'\"");
            List<ApnProfile> candidates = parseProfiles(allRaw.output);
            Map<Long, ApnProfile> usable = new LinkedHashMap<>(); usable.put(current.id, current);
            for (ApnProfile p : candidates) {
                if (p.id < 0 || empty(p.apn)) continue;
                String type = p.type == null ? "" : p.type.toLowerCase(Locale.US);
                if (!type.isEmpty() && !type.contains("default")) continue;
                usable.put(p.id, p); if (usable.size() >= 6) break;
            }
            BenchmarkResult baseline = benchmarkNetwork(true);
            if (!baseline.valid) { setApnText("Қазіргі APN-да интернет тесті өтпеді. APN өзгертілмеді."); return; }
            ApnProfile best = current; BenchmarkResult bestResult = baseline;
            for (ApnProfile p : usable.values()) {
                if (p.id == current.id) continue;
                setApnText("MAX APN тексерілуде: " + p.label() + "\nҚазіргі үздік: " + best.label() + " — " + bestResult.score + "/100");
                if (!setPreferredApn(p.id)) continue;
                restartMobileData(); waitForCellular(12000);
                BenchmarkResult test = benchmarkNetwork(true);
                if (test.valid && test.score > bestResult.score + 1) { best = p; bestResult = test; }
            }
            boolean applied = setPreferredApn(best.id); restartMobileData(); waitForCellular(12000);
            BenchmarkResult finalTest = benchmarkNetwork(false);
            if (!applied || !finalTest.valid) {
                setPreferredApn(current.id); restartMobileData(); setApnText("Жаңа APN тұрақты қосылмады. Backup қайтарылды:\n" + current.label()); return;
            }
            ApnProfile chosen = best; BenchmarkResult chosenResult = bestResult;
            runOnUiThread(() -> {
                tvApn.setText("✅ MAX APN ДАЙЫН\nОператор: " + tm.getNetworkOperatorName() + "\nТаңдалды: " + chosen.label() + "\nAPN Score: " + chosenResult.score + "/100\n" + chosenResult.details());
                tvTest.setText(chosenResult.details()); tvScore.setText("APN Stability: " + chosenResult.score + "/100\n" + scoreLabel(chosenResult.score));
            });
        });
    }

    private void restoreBackupApn() {
        tvApn.setText("Backup APN қайтарылуда...");
        pool.execute(() -> {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE); long id = sp.getLong(KEY_BACKUP_APN_ID, -1); String name = sp.getString(KEY_BACKUP_APN_NAME, "");
            if (id < 0) { setApnText("Сақталған APN backup жоқ."); return; }
            if (!setPreferredApn(id)) { setApnText("Backup APN қайтарылмады. Root рұқсатын тексер."); return; }
            restartMobileData(); waitForCellular(12000); setApnText("✅ Backup APN қайтарылды:\n" + name);
        });
    }

    private boolean setPreferredApn(long id) { return root("content insert --uri content://telephony/carriers/preferapn --bind apn_id:i:" + id).ok(); }
    private void restartMobileData() { root("svc data disable; sleep 1; svc data enable"); }
    private void waitForCellular(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (hasValidatedCellular()) return;
            try { Thread.sleep(700); } catch (InterruptedException e) { return; }
        }
    }

    private CmdResult root(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream())); StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            int code = p.waitFor(); return new CmdResult(code, sb.toString());
        } catch (Exception e) { return new CmdResult(-1, e.getClass().getSimpleName() + ": " + e.getMessage()); }
        finally { if (p != null) p.destroy(); }
    }

    private ApnProfile firstProfile(String raw) { List<ApnProfile> list = parseProfiles(raw); return list.isEmpty() ? null : list.get(0); }
    private List<ApnProfile> parseProfiles(String raw) {
        List<ApnProfile> out = new ArrayList<>(); if (raw == null) return out;
        for (String line : raw.split("\\r?\\n")) {
            if (!line.contains("Row:")) continue;
            long id = parseLongField(line, "_id", -1); String name = field(line, "name", "apn"); String apn = field(line, "apn", "type"); String type = field(line, "type", "protocol"); String protocol = field(line, "protocol", "roaming_protocol");
            if (id >= 0) out.add(new ApnProfile(id, clean(name), clean(apn), clean(type), clean(protocol)));
        }
        return out;
    }
    private String field(String line, String key, String nextKey) {
        Matcher m = Pattern.compile("(?:^|, |\\s)" + Pattern.quote(key) + "=(.*?)(?=, " + Pattern.quote(nextKey) + "=|$)").matcher(line);
        return m.find() ? m.group(1) : "";
    }
    private long parseLongField(String line, String key, long def) {
        Matcher m = Pattern.compile(Pattern.quote(key) + "=([0-9]+)").matcher(line); if (!m.find()) return def;
        try { return Long.parseLong(m.group(1)); } catch (Exception e) { return def; }
    }
    private String clean(String s) { if (s == null) return ""; s = s.trim(); return "NULL".equalsIgnoreCase(s) ? "" : s; }
    private String shortError(String s) { if (s == null) return ""; s = s.trim(); return s.length() > 220 ? s.substring(0, 220) : s; }
    private void setApnText(String text) { runOnUiThread(() -> tvApn.setText(text)); }

    @Override
    protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); }

    private static class HotspotState {
        final String iface; final List<String> clients;
        HotspotState(String iface, List<String> clients) { this.iface = iface; this.clients = clients; }
    }
    private static class CmdResult {
        final int code; final String output;
        CmdResult(int code, String output) { this.code = code; this.output = output == null ? "" : output; }
        boolean ok() { return code == 0; }
    }
    private static class ApnProfile {
        final long id; final String name, apn, type, protocol;
        ApnProfile(long id, String name, String apn, String type, String protocol) { this.id=id; this.name=name; this.apn=apn; this.type=type; this.protocol=protocol; }
        String label() { String n = emptyStatic(name) ? "APN" : name; String a = emptyStatic(apn) ? "—" : apn; String p = emptyStatic(protocol) ? "" : " / " + protocol; return n + " — " + a + p; }
        private static boolean emptyStatic(String s) { return s == null || s.isEmpty(); }
    }
    static class BenchmarkResult {
        final boolean valid; final double ping, jitter; final int loss; final double downMbps; final int score;
        BenchmarkResult(boolean valid, double ping, double jitter, int loss, double downMbps, int score) { this.valid=valid; this.ping=ping; this.jitter=jitter; this.loss=loss; this.downMbps=downMbps; this.score=score; }
        static BenchmarkResult invalid() { return new BenchmarkResult(false,0,0,100,-1,0); }
        String details() {
            String down = downMbps < 0 ? "өлшенбеді" : String.format(Locale.US, "%.1f Mbps", downMbps);
            return String.format(Locale.US, "Ping: %.0f ms\nJitter: %.0f ms\nLoss: %d%%\nDownload: %s", ping, jitter, loss, down);
        }
    }
}
