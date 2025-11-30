package com.jamplifier.investments.util;

public final class ConfigKeys {

    private ConfigKeys() {
    }

    // Storage
    public static final String STORAGE_TYPE = "storage-type";

    // Interest settings
    public static final String INTEREST_RATE_PERCENT = "interest.rate-percent";
    public static final String INTEREST_INTERVAL_MINUTES = "interest.interval-minutes";

    // Max investments per permission
    public static final String MAX_INVEST_PERMISSIONS = "max-invest-permissions";

    // Auto-collect
    public static final String AUTOCOLLECT_ENABLED = "autocollect.enabled";
    public static final String AUTOCOLLECT_PERMISSION = "autocollect.permission";
}
