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

        final int selected;
        final String selectedName;
        if ((suites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256) != 0) {
            selected = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;
            selectedName = "NCS_SK_256";
        } else if ((suites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) != 0) {
            selected = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
            selectedName = "NCS_SK_128";
        } else {
            throw new IllegalStateException(
                    "no supported shared-key Aware cipher; suites=0x"
                            + Integer.toHexString(suites));
        }

        WifiAwareDataPathSecurityConfig security =
                new WifiAwareDataPathSecurityConfig.Builder(selected)
                        .setPskPassphrase(passphrase)
                        .build();

        builder.setDataPathSecurityConfig(security);
        HarpLog.i("Aware security=" + selectedName);
    }
}
