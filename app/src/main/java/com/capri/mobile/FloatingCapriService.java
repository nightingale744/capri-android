
package com.capri.mobile;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.Random;

public class FloatingCapriService extends Service {

    private static final String CHANNEL_ID = "capri_floating";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_STOP =
            "com.capri.mobile.STOP_FLOATING";

    private WindowManager windowManager;
    private ImageView capriFace;
    private WindowManager.LayoutParams params;

    private boolean expanded = false;

    private float startX;
    private float startY;
    private float touchX;
    private float touchY;
    private long touchTime;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Random random = new Random();

    private Bitmap normalFace;
    private Bitmap laughFace;
    private Bitmap smugFace;

    private final Runnable idleExpression = new Runnable() {
        @Override
        public void run() {
            if (capriFace == null) return;

            int expression = random.nextInt(2);

            if (expression == 0 && laughFace != null) {
                capriFace.setImageBitmap(laughFace);
            } else if (smugFace != null) {
                capriFace.setImageBitmap(smugFace);
            }

            handler.postDelayed(() -> {
                if (capriFace != null && normalFace != null) {
                    capriFace.setImageBitmap(normalFace);
                }

                scheduleNextExpression();

            }, 1800);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startCapriForeground();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        loadFaces();
        createFloatingFace();
        scheduleNextExpression();
    }

    private void loadFaces() {
        normalFace = loadBitmap("capri/capri-face.png");
        laughFace = loadBitmap("capri/face-laugh.png");
        smugFace = loadBitmap("capri/face-smug.png");
    }

    private Bitmap loadBitmap(String path) {
        try (InputStream input = getAssets().open(path)) {
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            android.util.Log.e("Capri", "Could not load " + path, e);
            return null;
        }
    }

    private void scheduleNextExpression() {
        int delay = 5000 + random.nextInt(6000);
        handler.postDelayed(idleExpression, delay);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Capri Floating Assistant",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("Controls for floating Capri");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private void startCapriForeground() {
        Intent stopIntent = new Intent(this, FloatingCapriService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("Capri is floating")
                .setContentText("Tap Stop to close the floating assistant.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop",
                        stopPendingIntent
                )
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createFloatingFace() {
        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        capriFace = new ImageView(this);

        if (normalFace == null) {
            stopSelf();
            return;
        }

        capriFace.setImageBitmap(normalFace);
        capriFace.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int size = dp(76);

        params = new WindowManager.LayoutParams(
                size,
                size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(20);
        params.y = dp(150);

        capriFace.setOnTouchListener(this::handleTouch);

        windowManager.addView(capriFace, params);
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = params.x;
                startY = params.y;
                touchX = event.getRawX();
                touchY = event.getRawY();
                touchTime = System.currentTimeMillis();
                return true;

            case MotionEvent.ACTION_MOVE:
                params.x = (int) (startX +
                        event.getRawX() - touchX);

                params.y = (int) (startY +
                        event.getRawY() - touchY);

                windowManager.updateViewLayout(capriFace, params);
                return true;

            case MotionEvent.ACTION_UP:
                float distanceX = Math.abs(
                        event.getRawX() - touchX
                );

                float distanceY = Math.abs(
                        event.getRawY() - touchY
                );

                long duration =
                        System.currentTimeMillis() - touchTime;

                if (distanceX < dp(10) &&
                        distanceY < dp(10) &&
                        duration < 500) {
                    toggleSize();
                }

                return true;
        }

        return true;
    }

    private void toggleSize() {
        expanded = !expanded;

        int size = expanded ? dp(160) : dp(76);

        params.width = size;
        params.height = size;

        windowManager.updateViewLayout(capriFace, params);
    }

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (windowManager != null && capriFace != null) {
            try {
                windowManager.removeView(capriFace);
            } catch (Exception ignored) {
            }
        }

        if (normalFace != null) normalFace.recycle();
        if (laughFace != null) laughFace.recycle();
        if (smugFace != null) smugFace.recycle();

        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
