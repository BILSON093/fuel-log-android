package com.codex.fuellog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.umeng.commonsdk.UMConfigure;
import com.umeng.union.UMNativeAD;
import com.umeng.union.UMSplashAD;
import com.umeng.union.UMUnionSdk;
import com.umeng.union.api.UMAdConfig;
import com.umeng.union.api.UMUnionApi;
import com.umeng.union.widget.UMNativeLayout;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class AdManager {
    private static final String TAG = "CheHaoJiAds";
    private static boolean initialized;
    private static boolean splashShown;
    private static boolean floatingRequested;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long SPLASH_SHOW_WINDOW_MS = 1400;
    private static final long SPLASH_LOAD_TIMEOUT_MS = 2500;
    private static final long SPLASH_CLOSE_TIMEOUT_MS = 5000;

    private AdManager() {
    }

    static void init(Activity activity) {
        if (initialized) return;
        if (!AdConfig.hasUmengAppKey()) {
            Log.d(TAG, "Umeng AppKey is empty, ads are disabled");
            return;
        }
        initialized = true;
        UMConfigure.setLogEnabled(false);
        UMConfigure.preInit(activity.getApplicationContext(), AdConfig.UMENG_APP_KEY, AdConfig.UMENG_CHANNEL);
        UMUnionSdk.init(activity.getApplicationContext());
        new Thread(() -> UMConfigure.init(
                activity.getApplicationContext(),
                AdConfig.UMENG_APP_KEY,
                AdConfig.UMENG_CHANNEL,
                UMConfigure.DEVICE_TYPE_PHONE,
                null
        )).start();
    }

    static void showSplash(Activity activity) {
        if (!AdConfig.hasSplash() || splashShown || activity.isFinishing()) {
            if (!AdConfig.hasSplash()) Log.d(TAG, "Splash slot is empty, splash ad skipped");
            return;
        }
        splashShown = true;
        final long requestStarted = System.currentTimeMillis();
        final FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        final Runnable close = () -> removeFromParent(overlay);
        final Runnable loadTimeout = () -> {
            Log.d(TAG, "splash load timeout");
            removeFromParent(overlay);
        };
        MAIN.postDelayed(loadTimeout, SPLASH_LOAD_TIMEOUT_MS);
        UMAdConfig config = new UMAdConfig.Builder().setSlotId(AdConfig.SPLASH_SLOT_ID).build();
        UMUnionSdk.loadSplashAd(config, new UMUnionApi.AdLoadListener<UMSplashAD>() {
            @Override
            public void onSuccess(UMUnionApi.AdType type, UMSplashAD display) {
                MAIN.removeCallbacks(loadTimeout);
                long elapsed = System.currentTimeMillis() - requestStarted;
                if (elapsed > SPLASH_SHOW_WINDOW_MS) {
                    Log.d(TAG, "splash arrived late after " + elapsed + "ms, skipped");
                    removeFromParent(overlay);
                    return;
                }
                if (activity.isFinishing() || !display.isValid()) {
                    removeFromParent(overlay);
                    return;
                }
                if (overlay.getParent() == null) {
                    activity.addContentView(overlay, new ViewGroup.LayoutParams(-1, -1));
                }
                MAIN.postDelayed(close, SPLASH_CLOSE_TIMEOUT_MS);
                display.setAdEventListener(new UMUnionApi.SplashAdListener() {
                    @Override
                    public void onDismissed() {
                        MAIN.removeCallbacks(close);
                        removeFromParent(overlay);
                    }

                    @Override
                    public void onExposed() {
                        Log.d(TAG, "splash exposed");
                    }

                    @Override
                    public void onClicked(View view) {
                        Log.d(TAG, "splash clicked");
                    }

                    @Override
                    public void onError(int code, String message) {
                        Log.d(TAG, "splash error " + code + ": " + message);
                        MAIN.removeCallbacks(close);
                        removeFromParent(overlay);
                    }
                });
                display.disableShake();
                display.show(overlay);
            }

            @Override
            public void onFailure(UMUnionApi.AdType type, String message) {
                Log.d(TAG, "splash load failed: " + message);
                MAIN.removeCallbacks(loadTimeout);
                removeFromParent(overlay);
            }
        }, 3000);
    }

    static View createFeedAd(Activity activity) {
        if (!AdConfig.hasFeed()) {
            Log.d(TAG, "Feed slot is empty, feed ad skipped");
            return null;
        }
        UMNativeLayout layout = new UMNativeLayout(activity);
        layout.setVisibility(View.GONE);
        layout.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        layout.setBackgroundColor(Color.WHITE);
        UMAdConfig config = new UMAdConfig.Builder().setSlotId(AdConfig.FEED_SLOT_ID).build();
        UMUnionSdk.loadFeedAd(config, new UMUnionApi.AdLoadListener<UMNativeAD>() {
            @Override
            public void onSuccess(UMUnionApi.AdType type, UMNativeAD display) {
                if (activity.isFinishing() || !display.isValid()) return;
                display.setAdEventListener(simpleEventListener("feed"));
                new Thread(() -> {
                    Bitmap bitmap = bitmap(display.getImageUrl());
                    MAIN.post(() -> bindFeed(activity, layout, display, bitmap));
                }).start();
            }

            @Override
            public void onFailure(UMUnionApi.AdType type, String message) {
                Log.d(TAG, "feed load failed: " + message);
            }
        });
        return layout;
    }

    static void requestFloating(Activity activity) {
        if (!AdConfig.hasFloating() || floatingRequested || activity.isFinishing()) {
            if (!AdConfig.hasFloating()) Log.d(TAG, "Floating slot is empty, floating ad skipped");
            return;
        }
        floatingRequested = true;
        MAIN.postDelayed(() -> {
            if (activity.isFinishing()) return;
            UMAdConfig config = new UMAdConfig.Builder().setSlotId(AdConfig.FLOATING_SLOT_ID).build();
            UMUnionSdk.getApi().loadFloatingBannerAd(activity, config, new UMUnionApi.AdLoadListener<UMUnionApi.AdDisplay>() {
                @Override
                public void onSuccess(UMUnionApi.AdType type, UMUnionApi.AdDisplay display) {
                    if (activity.isFinishing() || !display.isValid()) return;
                    display.setAdEventListener(simpleEventListener("floating"));
                    display.setAdCloseListener(adType -> Log.d(TAG, "floating closed"));
                    display.show(activity);
                }

                @Override
                public void onFailure(UMUnionApi.AdType type, String message) {
                    Log.d(TAG, "floating load failed: " + message);
                }
            });
        }, 1200);
    }

    private static void bindFeed(Activity activity, UMNativeLayout layout, UMNativeAD ad, Bitmap bitmap) {
        layout.removeAllViews();
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(236, 242, 239));
        if (bitmap != null) image.setImageBitmap(bitmap);
        row.addView(image, new LinearLayout.LayoutParams(dp(activity, 96), dp(activity, 72)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(activity, 12), 0, 0, 0);
        TextView mark = text(activity, "广告", 11, Color.rgb(104, 113, 112), true);
        TextView title = text(activity, safe(ad.getTitle(), "推荐内容"), 16, Color.rgb(26, 33, 35), true);
        TextView desc = text(activity, safe(ad.getContent(), "点击查看详情"), 13, Color.rgb(104, 113, 112), false);
        desc.setMaxLines(2);
        copy.addView(mark);
        copy.addView(title);
        copy.addView(desc);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(row, new FrameLayout.LayoutParams(-1, -2));

        List<View> clickViews = new ArrayList<>();
        clickViews.add(layout);
        clickViews.add(image);
        clickViews.add(title);
        ad.bindView(activity, layout, clickViews);
        layout.setVisibility(View.VISIBLE);
    }

    private static UMUnionApi.AdEventListener simpleEventListener(String name) {
        return new UMUnionApi.AdEventListener() {
            @Override
            public void onExposed() {
                Log.d(TAG, name + " exposed");
            }

            @Override
            public void onClicked(View view) {
                Log.d(TAG, name + " clicked");
            }

            @Override
            public void onError(int code, String message) {
                Log.d(TAG, name + " error " + code + ": " + message);
            }
        };
    }

    private static Bitmap bitmap(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        try (InputStream input = new URL(url).openStream()) {
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.d(TAG, "image load failed: " + e.getMessage());
            return null;
        }
    }

    private static void removeFromParent(View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
    }

    private static TextView text(Activity activity, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
