package com.codex.fuellog;

final class AdConfig {
    static final String UMENG_APP_KEY = "6a11e0269a7f376488e55f57";
    static final String UMENG_CHANNEL = "official";
    static final String SPLASH_SLOT_ID = "100010252";
    static final String FEED_SLOT_ID = "100010253";
    static final String FLOATING_SLOT_ID = "100010254";

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
