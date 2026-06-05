package com.example.server.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 指数退避重试（带 jitter）
 */
public final class RetryHelper {

    private RetryHelper() {}

    @FunctionalInterface
    public interface RetryableCall<T> {
        T execute() throws Exception;
    }

    public static <T> T executeWithBackoff(
            int maxAttempts,
            long baseDelayMs,
            long maxDelayMs,
            RetryableCall<T> call,
            java.util.function.Predicate<Exception> retryable
    ) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return call.execute();
            } catch (Exception e) {
                last = e;
                boolean canRetry = retryable == null || retryable.test(e);
                if (!canRetry || attempt >= maxAttempts - 1) {
                    throw e;
                }
                long delay = computeDelay(baseDelayMs, maxDelayMs, attempt);
                System.err.println("⚠️ [Retry] attempt " + (attempt + 1) + "/" + maxAttempts
                        + " failed: " + e.getMessage() + ", sleep " + delay + "ms");
                Thread.sleep(delay);
            }
        }
        throw last;
    }

    public static long computeDelay(long baseDelayMs, long maxDelayMs, int attempt) {
        long exp = baseDelayMs * (1L << Math.min(attempt, 10));
        long capped = Math.min(exp, maxDelayMs);
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, capped / 4));
        return capped + jitter;
    }

    public static boolean isRetryableHttpOrNetwork(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return e instanceof java.io.IOException
                || msg.contains("timeout")
                || msg.contains("Timeout")
                || msg.contains("HTTP 5");
    }
}
