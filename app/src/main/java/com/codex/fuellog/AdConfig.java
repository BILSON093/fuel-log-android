package com.codex.fuellog;

final class AdConfig {
    static final String UMENG_APP_KEY = "";
    static final String UMENG_CHANNEL = "";
    static final String SPLASH_SLOT_ID = "";
    static final String FEED_SLOT_ID = "";
    static final String FLOATING_SLOT_ID = "";

    private AdConfig() {
    }

    static boolean hasUmengAppKey() {
        return !isBlank(UMENG_APP_KEY);
    }

    static boolean hasSplash() {
        return hasUmengAppKey() && !isBlank(SPLASH_SLOT_ID);
    }

    static boolean hasFeed() {
        return hasUmengAppKey() && !isBlank(FEED_SLOT_ID);
    }

    static boolean hasFloating() {
        return hasUmengAppKey() && !isBlank(FLOATING_SLOT_ID);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
