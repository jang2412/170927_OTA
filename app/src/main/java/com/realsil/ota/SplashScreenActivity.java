package com.realsil.ota;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SplashScreenActivity extends Activity {
    /**
     * Splash screen duration time in milliseconds
     */
    private static final int SPLASH_DELAY = 1500;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splashscreen);

        // Jump to DfuActivity after DELAY milliseconds
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(SplashScreenActivity.this, DfuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
            }
        }, SPLASH_DELAY);
    }

    @Override
    public void onBackPressed() {
        // Do nothing. Protect from exiting the application when splash screen is showing.
    }
}