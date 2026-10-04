package org.harp.l2;

import android.annotation.TargetApi;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;

/** API 33+ explicit Wi-Fi Aware cipher selection. */
@TargetApi(33)
final class AwareSecurityApi33 {
    private AwareSecurityApi33() {}

    static void apply(
            WifiAwareManager aware,
            WifiAwareNetworkSpecifier.Builder builder,
            String passphrase) {
        Characteristics characteristics = aware.getCharacteristics();
        if (characteristics == null) {
            throw new IllegalStateException("Wi-Fi Aware characteristics unavailable");
        }

        int suites = characteristics.getSupportedCipherSuites();
        if ((suites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) == 0) {
            throw new IllegalStateException(
                    "NCS_SK_128 unsupported; suites=0x" + Integer.toHexString(suites));
        }

        WifiAwareDataPathSecurityConfig security =
                new WifiAwareDataPathSecurityConfig.Builder(
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                        .setPskPassphrase(passphrase)
                        .build();

        builder.setDataPathSecurityConfig(security);
        HarpLog.i("Aware security=NCS_SK_128");
    }
}
