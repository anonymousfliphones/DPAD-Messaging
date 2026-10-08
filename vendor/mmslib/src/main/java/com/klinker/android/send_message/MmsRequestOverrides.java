package com.klinker.android.send_message;

import android.content.Context;
import android.os.Bundle;

/**
 * Hook that lets the host app adjust the {@code configOverrides} bundle handed to
 * {@code SmsManager.downloadMultimediaMessage()}, and opt in to appending the
 * transaction id to an M-Notification-Ind content location.
 *
 * The platform MmsService layers {@code configOverrides} on top of the carrier
 * config with {@code Bundle.putAll()}, so whatever the provider writes here wins
 * over the carrier's User-Agent, UAProf URL and httpParams for that request.
 */
public final class MmsRequestOverrides {

    public interface Provider {
        /** Adds or replaces entries in the bundle passed to the platform for {@code subId}. */
        void apply(Context context, int subId, Bundle configOverrides);

        /** True when the transaction id should be appended to the notification's content location. */
        boolean appendTransactionId(Context context, int subId);
    }

    private static volatile Provider sProvider;

    private MmsRequestOverrides() {
    }

    public static void setProvider(Provider provider) {
        sProvider = provider;
    }

    public static void apply(Context context, int subId, Bundle configOverrides) {
        Provider provider = sProvider;
        if (provider == null) return;
        try {
            provider.apply(context, subId, configOverrides);
        } catch (RuntimeException e) {
            android.util.Log.e("MmsRequestOverrides", "provider failed", e);
        }
    }

    public static boolean appendTransactionId(Context context, int subId) {
        Provider provider = sProvider;
        if (provider == null) return false;
        try {
            return provider.appendTransactionId(context, subId);
        } catch (RuntimeException e) {
            android.util.Log.e("MmsRequestOverrides", "provider failed", e);
            return false;
        }
    }
}
