package kz.almas.netoptimizer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.Button;
import android.widget.TextView;

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
    private static final String PREFS = "almas_net_prefs";
    private static final String KEY_BACKUP_APN_ID = "backup_apn_id";
    private static final String KEY_BACKUP_APN_NAME = "backup_apn_name";

    private TextView tvNetwork, tvScore, tvTest, tvApn;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private int lastSignalScore = 50;
    private int bestDbmSeen = -140;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvNetwork = findViewById(R.id.tvNetwork);
        tvScore = findViewById(R.id.tvScore);
        tvTest = findViewById(R.id.tvTest);
        tvApn = findViewById(R.id.tvApn);
        Button btnTest = findViewById(R.id.btnTest);
        Button btnSignal = findViewById(R.id.btnSignal);
        Button btnApn = findViewById(R.id.btnApn);
        Button btnMaxApn = findViewById(R.id.btnMaxApn);
        Button btnRestoreApn = findViewById(R.id.btnRestoreApn);

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
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 23 &&
                (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            showTransport();
            refreshSignal();
        }
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

                if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> tvNetwork.setText("Сигнал үшін Location рұқсатын бер."));
                    return;
                }

                try {
                    radio = networkTypeName(tm.getDataNetworkType());
                } catch (Exception ignored) {
                }

                List<CellInfo> cells = tm.getAllCellInfo();
                if (cells != null) {
                    for (CellInfo c : cells) {
                        if (c instanceof CellInfoLte) {
                            CellSignalStrengthLte s = ((CellInfoLte) c).getCellSignalStrength();
                            bestDbm = Math.max(bestDbm, s.getDbm());
                            if ("Белгісіз".equals(radio)) radio = "4G/LTE";
                        } else if (Build.VERSION.SDK_INT >= 29 && c instanceof CellInfoNr) {
                            CellSignalStrengthNr s = (CellSignalStrengthNr) ((CellInfoNr) c).getCellSignalStrength();
                            bestDbm = Math.max(bestDbm, s.getDbm());
                            radio = "5G/NR";
                        } else if (c instanceof CellInfoWcdma) {
                            CellSignalStrengthWcdma s = ((CellInfoWcdma) c).getCellSignalStrength();
                            bestDbm = Math.max(bestDbm, s.getDbm());
                            if ("Белгісіз".equals(radio)) radio = "3G/H/H+";
                        } else if (c instanceof CellInfoGsm) {
                            CellSignalStrengthGsm s = ((CellInfoGsm) c).getCellSignalStrength();
                            bestDbm = Math.max(bestDbm, s.getDbm());
                            if ("Белгісіз".equals(radio)) radio = "2G/GSM";
                        }
                    }
                }

                if (bestDbm <= -140) {
                    lastSignalScore = 50;
                    out.append("Оператор: ").append(empty(operator) ? "—" : operator)
                            .append("\nЖелі: ").append(radio)
                            .append("\nСигнал: дерек жоқ\nLocation қосулы екенін тексер.");
                } else {
                    if (bestDbm > bestDbmSeen) bestDbmSeen = bestDbm;
                    lastSignalScore = scoreSignal(bestDbm);
                    out.append("Оператор: ").append(empty(operator) ? "—" : operator)
                            .append("\nЖелі: ").append(radio)
                            .append("\nСигнал: ").append(bestDbm).append(" dBm")
                            .append("\nБаға: ").append(signalLabel(bestDbm))
                            .append("\nОсы сессиядағы ең жақсысы: ").append(bestDbmSeen).append(" dBm");
                }
            } catch (Exception e) {
                out.append("Сигналды оқу қатесі: ").append(e.getClass().getSimpleName());
            }
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

    private boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

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
                runOnUiThread(() -> {
                    tvTest.setText("Интернетке тест қосыла алмады.");
                    tvScore.setText("Network Score: 0/100");
                });
                return;
            }

            int overall = (int) Math.round(lastSignalScore * 0.25 + r.score * 0.75);
            overall = Math.max(0, Math.min(100, overall));
            final int finalOverall = overall;
            runOnUiThread(() -> {
                tvTest.setText(r.details());
                tvScore.setText("Network Score: " + finalOverall + "/100\n" + scoreLabel(finalOverall));
            });
        });
    }

    private BenchmarkResult benchmarkNetwork(boolean withDownload) {
        List<Long> times = new ArrayList<>();
        int probes = 7;
        int failures = 0;

        for (int i = 0; i < probes; i++) {
            long ms = probe("https://www.google.com/generate_204");
            if (ms >= 0) times.add(ms); else failures++;
        }
        if (times.isEmpty()) return BenchmarkResult.invalid();

        double avg = 0;
        for (long t : times) avg += t;
        avg /= times.size();

        double jitter = 0;
        for (int i = 1; i < times.size(); i++) {
            jitter += Math.abs(times.get(i) - times.get(i - 1));
        }
        if (times.size() > 1) jitter /= (times.size() - 1);

        int loss = (int) Math.round(failures * 100.0 / probes);
        double down = withDownload ? smallDownloadMbps() : -1;

        int latencyScore = avg <= 45 ? 100 : avg >= 450 ? 5 : (int) (100 - ((avg - 45) * 95 / 405));
        int jitterScore = jitter <= 8 ? 100 : jitter >= 120 ? 5 : (int) (100 - ((jitter - 8) * 95 / 112));
        int lossScore = Math.max(0, 100 - loss * 5);
        int speedScore;
        if (down < 0) speedScore = 60;
        else if (down >= 50) speedScore = 100;
        else if (down >= 20) speedScore = 90;
        else if (down >= 10) speedScore = 80;
        else if (down >= 5) speedScore = 65;
        else if (down >= 2) speedScore = 50;
        else if (down >= 1) speedScore = 35;
        else speedScore = 20;

        int score = (int) Math.round(latencyScore * 0.35 + jitterScore * 0.25 + lossScore * 0.25 + speedScore * 0.15);
        return new BenchmarkResult(true, avg, jitter, loss, down, Math.max(0, Math.min(100, score)));
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
            return (code >= 200 && code < 500) ? ms : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private double smallDownloadMbps() {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            URL u = new URL("https://speed.cloudflare.com/__down?bytes=750000");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            long start = System.nanoTime();
            in = c.getInputStream();
            byte[] buf = new byte[16384];
            long bytes = 0;
            int n;
            while ((n = in.read(buf)) > 0 && bytes < 750000) bytes += n;
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

    private String scoreLabel(int s) {
        if (s >= 90) return "Өте тұрақты";
        if (s >= 75) return "Жақсы";
        if (s >= 55) return "Орташа";
        if (s >= 35) return "Нашар";
        return "Өте нашар";
    }

    private boolean isCellularActive() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private boolean hasValidatedCellular() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null
                && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void readApnWithRoot() {
        tvApn.setText("Root рұқсаты сұралуы мүмкін...");
        pool.execute(() -> {
            CmdResult r = root("content query --uri content://telephony/carriers/preferapn --projection _id:name:apn:type:protocol:roaming_protocol:numeric");
            String result;
            if (r.ok() && !empty(r.output)) {
                result = "Қазіргі APN (root):\n" + r.output.trim()
                        + "\n\nMAX APN AUTO тұрақтылық тестін өзі жасайды.";
            } else {
                result = "Root берілмеді немесе APN провайдеріне қолжетім жоқ.\n" + shortError(r.output);
            }
            String finalResult = result;
            runOnUiThread(() -> tvApn.setText(finalResult));
        });
    }

    private void optimizeApnAutomatically() {
        tvApn.setText("MAX APN: дайындалуда... Root сұранысын рұқсат ет.");
        pool.execute(() -> {
            if (!isCellularActive()) {
                setApnText("MAX APN үшін Wi‑Fi-ды өшіріп, мобильді интернетті қос. Әйтпесе APN тесті Wi‑Fi-ды өлшеп қояды.");
                return;
            }

            CmdResult idResult = root("id");
            if (!idResult.ok() || !idResult.output.contains("uid=0")) {
                setApnText("Root рұқсаты жоқ. Magisk сұранысына Allow бер.");
                return;
            }

            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String numeric;
            try {
                numeric = tm.getNetworkOperator();
            } catch (Exception e) {
                numeric = "";
            }
            if (empty(numeric)) {
                setApnText("Оператор MCC/MNC анықталмады. SIM желіге тіркелгенін тексер.");
                return;
            }

            CmdResult currentRaw = root("content query --uri content://telephony/carriers/preferapn --projection _id:name:apn:type:protocol:roaming_protocol:numeric");
            ApnProfile current = firstProfile(currentRaw.output);
            if (current == null || current.id < 0) {
                setApnText("Қазіргі APN ID оқылмады. Бұл ROM APN provider-ін root-қа да шектеп тұр.");
                return;
            }

            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit()
                    .putLong(KEY_BACKUP_APN_ID, current.id)
                    .putString(KEY_BACKUP_APN_NAME, current.label())
                    .apply();

            String safeNumeric = numeric.replace("'", "");
            String query = "content query --uri content://telephony/carriers --projection _id:name:apn:type:protocol:roaming_protocol:numeric --where \"numeric='" + safeNumeric + "'\"";
            CmdResult allRaw = root(query);
            List<ApnProfile> candidates = parseProfiles(allRaw.output);

            Map<Long, ApnProfile> usable = new LinkedHashMap<>();
            usable.put(current.id, current);
            for (ApnProfile p : candidates) {
                if (p.id < 0 || empty(p.apn)) continue;
                String type = p.type == null ? "" : p.type.toLowerCase(Locale.US);
                if (!type.isEmpty() && !type.contains("default")) continue;
                usable.put(p.id, p);
                if (usable.size() >= 6) break;
            }

            if (usable.size() == 1) {
                setApnText("Оператор: " + tm.getNetworkOperatorName()
                        + "\nҚазіргі APN: " + current.label()
                        + "\nБасқа жарамды default APN табылмады. Осы APN қалады.\nИнтернет тесті жүріп жатыр...");
                BenchmarkResult only = benchmarkNetwork(true);
                if (only.valid) {
                    setApnText("MAX APN аяқталды.\nҚазіргі APN: " + current.label()
                            + "\n" + only.details() + "\nAPN Score: " + only.score + "/100");
                } else {
                    setApnText("APN өзгертілмеді. Интернет тесті өтпеді.");
                }
                return;
            }

            setApnText("MAX APN: " + usable.size() + " профиль табылды.\nBackup: " + current.label()
                    + "\nАлдымен қазіргі APN тестіленуде...");

            BenchmarkResult baseline = benchmarkNetwork(true);
            if (!baseline.valid) {
                setApnText("Қазіргі APN-да интернет тесті өтпеді. APN өзгертілмеді.");
                return;
            }

            ApnProfile best = current;
            BenchmarkResult bestResult = baseline;
            int index = 0;

            for (ApnProfile p : usable.values()) {
                index++;
                if (p.id == current.id) continue;

                setApnText("MAX APN: " + index + "/" + usable.size()
                        + "\nТексерілуде: " + p.label()
                        + "\nҚазіргі үздік: " + best.label() + " — " + bestResult.score + "/100");

                if (!setPreferredApn(p.id)) continue;
                restartMobileData();
                waitForCellular(12000);

                BenchmarkResult test = benchmarkNetwork(true);
                if (test.valid && test.score > bestResult.score + 1) {
                    best = p;
                    bestResult = test;
                }
            }

            boolean applied = setPreferredApn(best.id);
            restartMobileData();
            waitForCellular(12000);

            BenchmarkResult finalTest = benchmarkNetwork(false);
            if (!applied || !finalTest.valid) {
                setPreferredApn(current.id);
                restartMobileData();
                setApnText("Жаңа APN тұрақты қосылмады. Қауіпсіздік үшін backup қайтарылды:\n" + current.label());
                return;
            }

            final ApnProfile chosen = best;
            final BenchmarkResult chosenResult = bestResult;
            runOnUiThread(() -> {
                tvApn.setText("✅ MAX APN ДАЙЫН\nОператор: " + tm.getNetworkOperatorName()
                        + "\nТаңдалды: " + chosen.label()
                        + "\nAPN Score: " + chosenResult.score + "/100\n"
                        + chosenResult.details()
                        + (chosen.id == current.id
                        ? "\n\nҚазіргі APN ең жақсы болып шықты — өзгеріс қажет емес."
                        : "\n\nBackup сақталды: " + current.label()));
                tvTest.setText(chosenResult.details());
                tvScore.setText("APN Stability: " + chosenResult.score + "/100\n" + scoreLabel(chosenResult.score));
            });
        });
    }

    private void restoreBackupApn() {
        tvApn.setText("Backup APN қайтарылуда... Root сұранысын рұқсат ет.");
        pool.execute(() -> {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            long id = sp.getLong(KEY_BACKUP_APN_ID, -1);
            String name = sp.getString(KEY_BACKUP_APN_NAME, "");

            if (id < 0) {
                setApnText("Сақталған APN backup жоқ. Алдымен MAX APN AUTO іске қос.");
                return;
            }
            if (!setPreferredApn(id)) {
                setApnText("Backup APN қайтарылмады. Root рұқсатын тексер.");
                return;
            }

            restartMobileData();
            waitForCellular(12000);
            setApnText("✅ Backup APN қайтарылды:\n" + name);
        });
    }

    private boolean setPreferredApn(long id) {
        CmdResult r = root("content insert --uri content://telephony/carriers/preferapn --bind apn_id:i:" + id);
        return r.ok();
    }

    private void restartMobileData() {
        root("svc data disable; sleep 1; svc data enable");
    }

    private void waitForCellular(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (hasValidatedCellular()) return;
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private CmdResult root(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            int code = p.waitFor();
            return new CmdResult(code, sb.toString());
        } catch (Exception e) {
            return new CmdResult(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
    }

    private ApnProfile firstProfile(String raw) {
        List<ApnProfile> list = parseProfiles(raw);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<ApnProfile> parseProfiles(String raw) {
        List<ApnProfile> out = new ArrayList<>();
        if (raw == null) return out;

        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            if (!line.contains("Row:")) continue;
            long id = parseLongField(line, "_id", -1);
            String name = field(line, "name", "apn");
            String apn = field(line, "apn", "type");
            String type = field(line, "type", "protocol");
            String protocol = field(line, "protocol", "roaming_protocol");
            if (id >= 0) {
                out.add(new ApnProfile(id, clean(name), clean(apn), clean(type), clean(protocol)));
            }
        }
        return out;
    }

    private String field(String line, String key, String nextKey) {
        Pattern p = Pattern.compile("(?:^|, |\\s)" + Pattern.quote(key)
                + "=(.*?)(?=, " + Pattern.quote(nextKey) + "=|$)");
        Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : "";
    }

    private long parseLongField(String line, String key, long def) {
        Pattern p = Pattern.compile(Pattern.quote(key) + "=([0-9]+)");
        Matcher m = p.matcher(line);
        if (!m.find()) return def;
        try {
            return Long.parseLong(m.group(1));
        } catch (Exception e) {
            return def;
        }
    }

    private String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        return "NULL".equalsIgnoreCase(s) ? "" : s;
    }

    private String shortError(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 180 ? s.substring(0, 180) : s;
    }

    private void setApnText(String text) {
        runOnUiThread(() -> tvApn.setText(text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }

    private static class CmdResult {
        final int code;
        final String output;

        CmdResult(int code, String output) {
            this.code = code;
            this.output = output == null ? "" : output;
        }

        boolean ok() {
            return code == 0;
        }
    }

    private static class ApnProfile {
        final long id;
        final String name;
        final String apn;
        final String type;
        final String protocol;

        ApnProfile(long id, String name, String apn, String type, String protocol) {
            this.id = id;
            this.name = name;
            this.apn = apn;
            this.type = type;
            this.protocol = protocol;
        }

        String label() {
            String n = name == null || name.isEmpty() ? "APN" : name;
            String a = apn == null || apn.isEmpty() ? "—" : apn;
            String p = protocol == null || protocol.isEmpty() ? "" : " / " + protocol;
            return n + " — " + a + p;
        }
    }

    private static class BenchmarkResult {
        final boolean valid;
        final double ping;
        final double jitter;
        final int loss;
        final double downMbps;
        final int score;

        BenchmarkResult(boolean valid, double ping, double jitter, int loss, double downMbps, int score) {
            this.valid = valid;
            this.ping = ping;
            this.jitter = jitter;
            this.loss = loss;
            this.downMbps = downMbps;
            this.score = score;
        }

        static BenchmarkResult invalid() {
            return new BenchmarkResult(false, 0, 0, 100, -1, 0);
        }

        String details() {
            String down = downMbps < 0
                    ? "өлшенбеді"
                    : String.format(Locale.US, "%.1f Mbps", downMbps);
            return String.format(Locale.US,
                    "Ping: %.0f ms\nJitter: %.0f ms\nLoss: %d%%\nDownload: %s",
                    ping, jitter, loss, down);
        }
    }
}
