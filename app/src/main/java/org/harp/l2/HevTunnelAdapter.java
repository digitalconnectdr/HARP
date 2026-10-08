package org.harp.l2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflection boundary for the optional hev-socks5-tunnel Android AAR.
 *
 * Keeping this class reflection-only means Stage-0/1 can still compile without
 * the native AAR. Once hev-socks5-tunnel.aar is placed in app/libs and packaged
 * by Gradle, the same code can start Stage-2 without a source-level dependency.
 */
final class HevTunnelAdapter {
    private static final String CLASS_NAME = "hev.htproxy.TProxyService";

    private HevTunnelAdapter() {}

    static boolean isPackaged() {
        try {
            Class.forName(
                    CLASS_NAME,
                    false,
                    HevTunnelAdapter.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static boolean start(String configPath, int tunFd) throws Exception {
        if (configPath == null || configPath.isEmpty()) {
            throw new IllegalArgumentException("configPath is empty");
        }
        if (tunFd < 0) {
            throw new IllegalArgumentException("invalid tun fd");
        }
        Object result = invoke(
                "TProxyStartService",
                new Class<?>[]{String.class, int.class},
                new Object[]{configPath, tunFd});
        return Boolean.TRUE.equals(result);
    }

    static boolean stop() throws Exception {
        Object result = invoke(
                "TProxyStopService",
                new Class<?>[0],
                new Object[0]);
        return Boolean.TRUE.equals(result);
    }

    static boolean isRunning() throws Exception {
        Object result = invoke(
                "TProxyIsRunning",
                new Class<?>[0],
                new Object[0]);
        return Boolean.TRUE.equals(result);
    }

    static long[] stats() throws Exception {
        Object result = invoke(
                "TProxyGetStats",
                new Class<?>[0],
                new Object[0]);
        if (!(result instanceof long[])) {
            throw new IllegalStateException("HEV stats returned unexpected type");
        }
        long[] stats = (long[]) result;
        return stats.clone();
    }

    private static Object invoke(
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args) throws Exception {
        final Class<?> clazz;
        try {
            clazz = Class.forName(CLASS_NAME);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "HEV Android AAR is not packaged", e);
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException(
                    "HEV JNI library could not be loaded", e);
        }

        final Method method;
        try {
            method = clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "HEV JNI contract mismatch: " + methodName, e);
        }

        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "HEV JNI method not accessible: " + methodName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(
                    "HEV JNI invocation failed: " + methodName, cause);
        }
    }
}
