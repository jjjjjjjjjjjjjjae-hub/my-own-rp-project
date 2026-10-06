package kz.almas.netoptimizer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private View ringOuter;
    private View ringInner;
    private View progress;
    private ImageView logo;
    private TextView title;
    private TextView subtitle;

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

        logo.setAlpha(0f);
        logo.setScaleX(0.62f);
        logo.setScaleY(0.62f);

        ringOuter.setAlpha(0f);
        ringOuter.setScaleX(0.55f);
        ringOuter.setScaleY(0.55f);

        ringInner.setAlpha(0f);
        ringInner.setScaleX(0.65f);
        ringInner.setScaleY(0.65f);

        title.setAlpha(0f);
        title.setTranslationY(20f);
        subtitle.setAlpha(0f);
        subtitle.setTranslationY(16f);

        progress.setScaleX(0f);
        progress.setPivotX(0f);

        startIntro();
    }

    private void startIntro() {
        DecelerateInterpolator decelerate = new DecelerateInterpolator(1.8f);
        AccelerateDecelerateInterpolator smooth = new AccelerateDecelerateInterpolator();

        ringOuter.animate()
                .alpha(0.30f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(650)
                .setInterpolator(decelerate)
                .start();

        ringInner.animate()
                .alpha(0.55f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(90)
                .setDuration(620)
                .setInterpolator(decelerate)
                .start();

        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(360f)
                .setDuration(820)
                .setInterpolator(decelerate)
                .withEndAction(this::pulseLogo)
                .start();

        title.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(430)
                .setDuration(460)
                .setInterpolator(smooth)
                .start();

        subtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(610)
                .setDuration(430)
                .setInterpolator(smooth)
                .start();

        progress.animate()
                .scaleX(1f)
                .setStartDelay(500)
                .setDuration(1250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::openMain)
                .start();
    }

    private void pulseLogo() {
        logo.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(260)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> logo.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start())
                .start();

        ringOuter.animate()
                .alpha(0.12f)
                .scaleX(1.18f)
                .scaleY(1.18f)
                .setDuration(650)
                .start();
    }

    private void openMain() {
        if (isFinishing()) return;

        title.animate().alpha(0f).translationY(-10f).setDuration(180).start();
        subtitle.animate().alpha(0f).translationY(-8f).setDuration(180).start();
        logo.animate()
                .alpha(0f)
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(220)
                .withEndAction(() -> {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                })
                .start();
    }
}
