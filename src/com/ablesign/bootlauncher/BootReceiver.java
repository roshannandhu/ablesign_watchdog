package com.ablesign.bootlauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    private static final String ABLESIGN = "tv.ablesign.app";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult result = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(6000);
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.setComponent(new ComponentName(ABLESIGN, ABLESIGN + ".MainActivity"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    context.startActivity(i);
                } catch (Exception e) {
                    // ignore
                } finally {
                    result.finish();
                }
            }
        }).start();
    }
}
