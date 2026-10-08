package org.harp.l2;

import android.content.Context;

/**
 * Process-scoped owner of the active HARP Aware session.
 *
 * MainActivity is only a UI. Keeping the controller here prevents an Activity
 * recreation/destruction from tearing down the NDP while a relay/VPN session
 * still needs it.
 */
final class HarpRuntime {
    private static HarpAwareController controller;

    private HarpRuntime() {}

    static synchronized HarpAwareController controller(Context context) {
        if (controller == null) {
            controller = new HarpAwareController(
                    context.getApplicationContext());
        }
        return controller;
    }

    static synchronized HarpAwareController reset(Context context) {
        if (controller != null) {
            controller.close();
        }
        controller = new HarpAwareController(
                context.getApplicationContext());
        return controller;
    }

    static synchronized void stop() {
        if (controller != null) {
            controller.close();
            controller = null;
        }
    }
}
