package com.ablesign.bootlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class WatchdogAccessibilityService extends AccessibilityService {
    private static final String TAG = "WatchdogA11y";
    private static final String ABLESIGN = "tv.ablesign.app";
    private static boolean launched = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate launched=" + launched);
        if (!launched) {
            launched = true;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    launchAbleSign("onCreate");
                }
            }, 12000);
        }
    }

    @Override
    public void onServiceConnected() {
        Log.d(TAG, "onServiceConnected launched=" + launched);
        // Also try from here in case onCreate path was blocked
        if (!launched) {
            launched = true;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    launchAbleSign("onServiceConnected");
                }
            }, 3000);
        }
    }

    private void launchAbleSign(String from) {
        Log.d(TAG, "launchAbleSign from=" + from);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(new ComponentName(ABLESIGN, ABLESIGN + ".MainActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
            Log.d(TAG, "startActivity ok from=" + from);
        } catch (Exception e) {
            Log.e(TAG, "startActivity failed from=" + from + ": " + e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
