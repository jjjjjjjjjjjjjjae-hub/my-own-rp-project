package kz.almas.netoptimizer;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View ringOuter;
    private View ringInner;
    private View progress;
    private ImageView logo;
    private TextView title;
    private TextView subtitle;
    private TextView status;
    private TextView online;
    private ObjectAnimator outerSpin;
    private ObjectAnimator innerSpin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ringOuter = findViewById(R.id.ringOuter);
        ringInner = findViewById(R.id.ringInner);
        progress = findViewById(R.id.progressLine);
        logo = findViewById(R.id.splashLogo);
        title = findViewById(R.id.splashTitle);
        subtitle = findViewById(R.id.splashSubtitle);
        status = findViewById(R.id.splashStatus);
        online = findViewById(R.id.splashOnline);

        setupInitialState();
        startBootSequence();
    }

    private void setupInitialState() {
        logo.setAlpha(0f);
        logo.setScaleX(0.16f);
        logo.setScaleY(0.16f);
        logo.setRotation(-210f);

        ringOuter.setAlpha(0f);
        ringOuter.setScaleX(0.25f);
        ringOuter.setScaleY(0.25f);

        ringInner.setAlpha(0f);
        ringInner.setScaleX(0.35f);
        ringInner.setScaleY(0.35f);

        title.setAlpha(0f);
        title.setTranslationY(28f);
        title.setScaleX(1.12f);

        subtitle.setAlpha(0f);
        subtitle.setTranslationY(18f);

        status.setAlpha(0f);
        online.setAlpha(0f);
        online.setScaleX(0.84f);
        online.setScaleY(0.84f);

        progress.setScaleX(0f);
        progress.setPivotX(0f);
    }

    private void startBootSequence() {
        outerSpin = ObjectAnimator.ofFloat(ringOuter, View.ROTATION, 0f, 360f);
        outerSpin.setDuration(2800);
        outerSpin.setRepeatCount(ValueAnimator.INFINITE);
        outerSpin.setInterpolator(new LinearInterpolator());
        outerSpin.start();

        innerSpin = ObjectAnimator.ofFloat(ringInner, View.ROTATION, 360f, 0f);
        innerSpin.setDuration(2100);
        innerSpin.setRepeatCount(ValueAnimator.INFINITE);
        innerSpin.setInterpolator(new LinearInterpolator());
        innerSpin.start();

        ringOuter.animate()
                .alpha(0.42f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(780)
                .setInterpolator(new OvershootInterpolator(0.9f))
                .start();

        ringInner.animate()
                .alpha(0.68f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(110)
                .setDuration(720)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();

        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setStartDelay(120)
                .setDuration(950)
                .setInterpolator(new OvershootInterpolator(1.25f))
                .withEndAction(this::impactPulse)
                .start();

        title.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .setStartDelay(720)
                .setDuration(520)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        subtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(930)
                .setDuration(460)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        status.animate()
                .alpha(1f)
                .setStartDelay(520)
                .setDuration(240)
                .start();

        progress.animate()
                .scaleX(1f)
                .setStartDelay(430)
                .setDuration(2450)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        handler.postDelayed(() -> setStatus("SCANNING NETWORK NODES"), 520);
        handler.postDelayed(() -> setStatus("AUTHENTICATING LINK"), 1050);
        handler.postDelayed(() -> setStatus("OPTIMIZING CHANNEL"), 1530);
        handler.postDelayed(() -> setStatus("SECURE ROUTE ESTABLISHED"), 2020);
        handler.postDelayed(this::showOnline, 2450);
        handler.postDelayed(this::openMain, 3050);
    }

    private void setStatus(String text) {
        status.animate().alpha(0f).setDuration(100).withEndAction(() -> {
            status.setText(text);
            status.setTranslationX(-14f);
            status.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(210)
                    .start();
        }).start();
    }

    private void impactPulse() {
        logo.animate()
                .scaleX(1.10f)
                .scaleY(1.10f)
                .setDuration(150)
                .withEndAction(() -> logo.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(210)
                        .start())
                .start();

        ringInner.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .alpha(0.18f)
                .setDuration(430)
                .withEndAction(() -> ringInner.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(0.55f)
                        .setDuration(300)
                        .start())
                .start();
    }

    private void showOnline() {
        status.animate().alpha(0f).setDuration(160).start();
        online.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .start();

        logo.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(200)
                .withEndAction(() -> logo.animate().scaleX(1f).scaleY(1f).setDuration(200).start())
                .start();
    }

    private void openMain() {
        if (isFinishing()) return;

        if (outerSpin != null) outerSpin.cancel();
        if (innerSpin != null) innerSpin.cancel();

        title.animate().alpha(0f).translationY(-12f).setDuration(190).start();
        subtitle.animate().alpha(0f).translationY(-8f).setDuration(190).start();
        online.animate().alpha(0f).translationY(-8f).setDuration(180).start();
        ringOuter.animate().alpha(0f).scaleX(1.3f).scaleY(1.3f).setDuration(260).start();
        ringInner.animate().alpha(0f).scaleX(1.2f).scaleY(1.2f).setDuration(240).start();

        logo.animate()
                .alpha(0f)
                .scaleX(1.22f)
                .scaleY(1.22f)
                .setDuration(260)
                .withEndAction(() -> {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (outerSpin != null) outerSpin.cancel();
        if (innerSpin != null) innerSpin.cancel();
        super.onDestroy();
    }
}
