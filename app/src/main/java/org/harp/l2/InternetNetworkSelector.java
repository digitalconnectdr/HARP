package org.harp.l2;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Selects a validated Internet egress for relay B, never the Aware data path itself.
 * Preference order:
 * 1) direct + validated + NOT_METERED
 * 2) direct + validated
 * 3) other non-VPN validated
 * 4) validated VPN fallback
 */
final class InternetNetworkSelector {
    private InternetNetworkSelector() {}

    static Network choose(ConnectivityManager cm) {
        Network directMetered = null;
        Network fallbackVpn = null;
        Network fallbackOther = null;

        for (Network network : cm.getAllNetworks()) {
            if (!isUsableInternet(cm, network)) continue;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) continue;

            boolean directTransport =
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);

            boolean directNonVpn =
                    directTransport
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);

            if (directNonVpn
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                return network;
            }

            if (directNonVpn && directMetered == null) {
                directMetered = network;
                continue;
            }

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                if (fallbackVpn == null) fallbackVpn = network;
            } else if (fallbackOther == null) {
                fallbackOther = network;
            }
        }

        if (directMetered != null) return directMetered;
        if (fallbackOther != null) return fallbackOther;
        return fallbackVpn;
    }

    static boolean isUsableInternet(ConnectivityManager cm, Network network) {
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) return false;
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    static String describe(ConnectivityManager cm, Network network) {
        if (network == null) return "null";
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return network + " caps=null";

        StringBuilder transports = new StringBuilder();
        addTransport(transports, caps, NetworkCapabilities.TRANSPORT_WIFI, "WIFI");
        addTransport(transports, caps, NetworkCapabilities.TRANSPORT_CELLULAR, "CELLULAR");
        addTransport(transports, caps, NetworkCapabilities.TRANSPORT_ETHERNET, "ETHERNET");
        addTransport(transports, caps, NetworkCapabilities.TRANSPORT_VPN, "VPN");
        addTransport(transports, caps, NetworkCapabilities.TRANSPORT_BLUETOOTH, "BLUETOOTH");

        boolean metered =
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        return network
                + " transport=" + (transports.length() == 0 ? "OTHER" : transports)
                + " validated="
                + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                + " metered=" + metered;
    }

    private static void addTransport(
            StringBuilder out,
            NetworkCapabilities caps,
            int transport,
            String name) {
        if (!caps.hasTransport(transport)) return;
        if (out.length() > 0) out.append('+');
        out.append(name);
    }
}
