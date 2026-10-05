package kz.almas.netoptimizer;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QualityAccessibilityService extends AccessibilityService {
    private static final String CH_ALERT = "almas_quality_alert";
    private static final int NOTIF_ID = 5201;
    private static final Pattern QUALITY = Pattern.compile("(?i)(144|240|260|360|480|720|1080|1440|2160)\\s*p");
    private long lastWarnAt = 0;
    private int lastWarnQuality = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_ALERT, "Video Quality Warnings", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Интернет көтермейтін жоғары видео сапасы таңдалғанда ескертеді");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED && type != AccessibilityEvent.TYPE_VIEW_SELECTED) return;

        StringBuilder text = new StringBuilder();
        List<CharSequence> list = event.getText();
        if (list != null) for (CharSequence s : list) if (s != null) text.append(s).append(' ');

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            CharSequence t = source.getText();
            CharSequence d = source.getContentDescription();
            if (t != null) text.append(t).append(' ');
            if (d != null) text.append(d).append(' ');
        }

        Matcher m = QUALITY.matcher(text.toString());
        int selected = 0;
        while (m.find()) {
            try { selected = Integer.parseInt(m.group(1)); } catch (Exception ignored) { }
        }
        if (selected == 0) return;

        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        int recommended = sp.getInt(MainActivity.KEY_RECOMMENDED_QUALITY, 0);
        if (recommended == 0 || selected <= recommended) return;

        long now = System.currentTimeMillis();
        if (selected == lastWarnQuality && now - lastWarnAt < 10000) return;
        lastWarnQuality = selected;
        lastWarnAt = now;

        String app = event.getPackageName() == null ? "қолданба" : event.getPackageName().toString();
        warn(recommended, selected, app);
    }

    private void warn(int recommended, int selected, String app) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 11, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "⚠ Интернет сапасы жеткіліксіз болуы мүмкін";
        String body = "Құрметті қолданушы, біздің болжауымыз бойынша интернетіңіз тек "
                + recommended + "p сапасын тұрақты көтере алады. " + selected
                + "p-ға ауыстырсаңыз, видеоның ашылуы нашарлауы мүмкін.";

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_ALERT)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body + "\nҚолданба: " + app))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, b.build());
    }

    @Override
    public void onInterrupt() { }
}
