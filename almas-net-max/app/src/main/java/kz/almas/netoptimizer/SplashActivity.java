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

    private View introLayer;
    private View introBeam;
    private TextView introAlmas;
    private TextView introTelekom;
    private TextView introFeatures;

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

        introLayer = findViewById(R.id.introLayer);
        introBeam = findViewById(R.id.introBeam);
        introAlmas = findViewById(R.id.introAlmas);
        introTelekom = findViewById(R.id.introTelekom);
        introFeatures = findViewById(R.id.introFeatures);

        ringOuter = findViewById(R.id.ringOuter);
        ringInner = findViewById(R.id.ringInner);
        progress = findViewById(R.id.progressLine);
        logo = findViewById(R.id.splashLogo);
        title = findViewById(R.id.splashTitle);
        subtitle = findViewById(R.id.splashSubtitle);
        status = findViewById(R.id.splashStatus);
        online = findViewById(R.id.splashOnline);

        setupInitialState();
        startTransformIntro();
    }

    private void setupInitialState() {
        introLayer.setAlpha(1f);

        introBeam.setScaleX(0f);
        introBeam.setAlpha(0f);

        introAlmas.setAlpha(0f);
        introAlmas.setTranslationX(-110f);
        introAlmas.setScaleX(1.22f);

        introTelekom.setAlpha(0f);
        introTelekom.setTranslationX(110f);
        introTelekom.setScaleX(1.22f);

        introFeatures.setAlpha(0f);
        introFeatures.setTranslationY(20f);

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

    private void startTransformIntro() {
        introBeam.animate()
                .alpha(1f)
                .scaleX(1f)
                .setDuration(420)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        introAlmas.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .setStartDelay(180)
                .setDuration(520)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .start();

        introTelekom.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .setStartDelay(270)
                .setDuration(520)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .start();

        introFeatures.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(720)
                .setDuration(360)
                .start();

        handler.postDelayed(() -> {
            introFeatures.setText("HOTSPOT  •  SPEED CONTROL  •  NETWORK AI");
            introFeatures.animate()
                    .alpha(0.45f)
                    .setDuration(110)
                    .withEndAction(() -> introFeatures.animate().alpha(1f).setDuration(140).start())
                    .start();
        }, 1060);

        handler.postDelayed(this::transformIntoCore, 1420);
    }

    private void transformIntoCore() {
        introFeatures.animate().alpha(0f).translationY(-14f).setDuration(220).start();

        introAlmas.animate()
                .translationX(42f)
                .scaleX(0.82f)
                .scaleY(0.82f)
                .alpha(0f)
                .setDuration(360)
                .start();

        introTelekom.animate()
                .translationX(-42f)
                .scaleX(0.82f)
                .scaleY(0.82f)
                .alpha(0f)
                .setDuration(360)
                .start();

        introBeam.animate()
                .scaleX(0.08f)
                .alpha(0.9f)
                .setDuration(330)
                .withEndAction(() -> {
                    introLayer.animate()
                            .alpha(0f)
                            .setDuration(260)
                            .withEndAction(() -> {
                                introLayer.setVisibility(View.GONE);
                                startBootSequence();
                            })
                            .start();
                })
                .start();
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
                .setDuration(700)
                .setInterpolator(new OvershootInterpolator(0.9f))
                .start();

        ringInner.animate()
                .alpha(0.68f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(80)
                .setDuration(660)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();

        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setStartDelay(90)
                .setDuration(820)
                .setInterpolator(new OvershootInterpolator(1.25f))
                .withEndAction(this::impactPulse)
                .start();

        title.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .setStartDelay(600)
                .setDuration(440)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        subtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(760)
                .setDuration(390)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        status.animate()
                .alpha(1f)
                .setStartDelay(430)
                .setDuration(220)
                .start();

        progress.animate()
                .scaleX(1f)
                .setStartDelay(330)
                .setDuration(2050)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        handler.postDelayed(() -> setStatus("SCANNING NETWORK NODES"), 430);
        handler.postDelayed(() -> setStatus("AUTHENTICATING LINK"), 830);
        handler.postDelayed(() -> setStatus("OPTIMIZING CHANNEL"), 1240);
        handler.postDelayed(() -> setStatus("SECURE ROUTE ESTABLISHED"), 1640);
        handler.postDelayed(this::showOnline, 2010);
        handler.postDelayed(this::openMain, 2520);
    }

    private void setStatus(String text) {
        status.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            status.setText(text);
            status.setTranslationX(-14f);
            status.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(190)
                    .start();
        }).start();
    }

    private void impactPulse() {
        logo.animate()
                .scaleX(1.10f)
                .scaleY(1.10f)
                .setDuration(140)
                .withEndAction(() -> logo.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start())
                .start();

        ringInner.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .alpha(0.18f)
                .setDuration(400)
                .withEndAction(() -> ringInner.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(0.55f)
                        .setDuration(280)
                        .start())
                .start();
    }

    private void showOnline() {
        status.animate().alpha(0f).setDuration(150).start();
        online.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(390)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .start();

        logo.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(190)
                .withEndAction(() -> logo.animate().scaleX(1f).scaleY(1f).setDuration(190).start())
                .start();
    }

    private void openMain() {
        if (isFinishing()) return;

        if (outerSpin != null) outerSpin.cancel();
        if (innerSpin != null) innerSpin.cancel();

        title.animate().alpha(0f).translationY(-12f).setDuration(180).start();
        subtitle.animate().alpha(0f).translationY(-8f).setDuration(180).start();
        online.animate().alpha(0f).translationY(-8f).setDuration(170).start();
        ringOuter.animate().alpha(0f).scaleX(1.3f).scaleY(1.3f).setDuration(250).start();
        ringInner.animate().alpha(0f).scaleX(1.2f).scaleY(1.2f).setDuration(230).start();

        logo.animate()
                .alpha(0f)
                .scaleX(1.22f)
                .scaleY(1.22f)
                .setDuration(250)
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
