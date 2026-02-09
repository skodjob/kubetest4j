/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.wait;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Wait utils
 */
public class Wait {
    private static final Logger LOGGER = LoggerFactory.getLogger(Wait.class);

    private Wait() {
        // Private constructor to prevent instantiation
    }

    /**
     * For every poll (happening once each {@code pollIntervalMs}) checks if supplier {@code ready} is true.
     * If yes, the wait is closed. Otherwise, waits another {@code pollIntervalMs} and tries again.
     * Once the wait timeout (specified by {@code timeoutMs} is reached and supplier wasn't true until that time,
     * throws {@link WaitException}.
     *
     * @param description    information about on what we are waiting
     * @param pollIntervalMs poll interval in milliseconds
     * @param timeoutMs      timeout specified in milliseconds
     * @param ready          {@link BooleanSupplier} containing code, which should be executed each poll,
     *                       verifying readiness of the particular thing
     */
    public static void until(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready) {
        until(description, pollIntervalMs, timeoutMs, ready, () -> {
        });
    }

    /**
     * For every poll (happening once each {@code pollIntervalMs}) checks if supplier {@code ready} is true.
     * If yes, the wait is closed. Otherwise, waits another {@code pollIntervalMs} and tries again.
     * Once the wait timeout (specified by {@code timeoutMs} is reached and supplier wasn't true until that time,
     * runs the {@code onTimeout} (f.e. print of logs, showing the actual value that was checked inside {@code ready}),
     * and finally throws {@link WaitException}.
     *
     * @param description    information about on what we are waiting
     * @param pollIntervalMs poll interval in milliseconds
     * @param timeoutMs      timeout specified in milliseconds
     * @param ready          {@link BooleanSupplier} containing code, which should be executed each poll,
     *                       verifying readiness of the particular thing
     * @param onTimeout      {@link Runnable} executed once timeout is reached and
     *                       before the {@link WaitException} is thrown.
     */
    public static void until(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready,
                             Runnable onTimeout) {
        LOGGER.info("Waiting for: {}", description);
        long deadline = System.currentTimeMillis() + timeoutMs;

        String exceptionMessage = null;
        String previousExceptionMessage = null;

        // in case we are polling every 1s, we want to print exception after x tries, not on the first try
        // for minutes poll interval will 2 be enough
        int exceptionAppearanceCount = Duration.ofMillis(pollIntervalMs).toMinutes() > 0
            ? 2 : Math.max((int) (timeoutMs / pollIntervalMs) / 4, 2);
        int exceptionCount = 0;
        int newExceptionAppearance = 0;

        StringWriter stackTraceError = new StringWriter();

        while (true) {
            boolean result;
            try {
                result = ready.getAsBoolean();
            } catch (Exception e) {
                exceptionMessage = e.getMessage();

                if (++exceptionCount == exceptionAppearanceCount && exceptionMessage != null
                    && exceptionMessage.equals(previousExceptionMessage)) {

                    if (e.getCause() instanceof TimeoutException) {
                        LOGGER.warn("While waiting for: {} exception occurred: {}",
                            description, e.getCause().getClass().getCanonicalName());
                        exceptionMessage = e.getCause().getClass().getCanonicalName();
                    } else {
                        String canonicalNameForException = e.getCause() != null ?
                            e.getCause().getClass().getCanonicalName() : null;

                        LOGGER.warn("While waiting for: {} exception occurred: {}/{}",
                            description, canonicalNameForException, exceptionMessage);
                    }

                    // log the stacktrace
                    e.printStackTrace(new PrintWriter(stackTraceError));
                } else if (exceptionMessage != null && !exceptionMessage.equals(previousExceptionMessage)
                    && ++newExceptionAppearance == 2) {
                    previousExceptionMessage = exceptionMessage;
                }

                result = false;
            }
            long timeLeft = deadline - System.currentTimeMillis();
            if (result) {
                return;
            }
            if (timeLeft <= 0) {
                if (exceptionCount > 1) {
                    if (exceptionMessage.equals("null")) {
                        LOGGER.error("Latest exception while waiting for: {} was: {}",
                            description, exceptionMessage);
                    } else {
                        LOGGER.error("Latest exception while waiting for: {} had message: {}",
                            description, exceptionMessage);
                    }

                    if (!stackTraceError.toString().isEmpty()) {
                        // printing handled stacktrace
                        LOGGER.error(String.valueOf(stackTraceError));
                    }
                }
                onTimeout.run();
                WaitException waitException = new WaitException("Timeout after " + timeoutMs
                    + " ms waiting for " + description);
                LOGGER.error(waitException.getMessage(), waitException);
                throw waitException;
            }
            long sleepTime = Math.min(pollIntervalMs, timeLeft);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * For every poll (happening once each {@code pollIntervalMs}) checks if supplier {@code ready} is true.
     * If yes, the wait is closed. Otherwise, waits another {@code pollIntervalMs} and tries again.
     * Once the wait timeout (specified by {@code timeoutMs} is reached and supplier wasn't true until that time,
     * runs the {@code onTimeout} (f.e. print of logs, showing the actual value that was checked inside {@code ready}),
     * and finally throws {@link WaitException}.
     *
     * @param description    information about on what we are waiting
     * @param pollIntervalMs poll interval in milliseconds
     * @param timeoutMs      timeout specified in milliseconds
     * @param ready          {@link BooleanSupplier} containing code, which should be executed each poll,
     *                       verifying readiness of the particular thing
     * @return completable future for waiting
     */
    public static CompletableFuture<Void> untilAsync(String description, long pollIntervalMs,
                                                     long timeoutMs, BooleanSupplier ready) {
        LOGGER.info("Waiting for {}", description);
        long deadline = System.currentTimeMillis() + timeoutMs;
        CompletableFuture<Void> future = new CompletableFuture<>();
        Executor delayed = CompletableFuture.delayedExecutor(pollIntervalMs, TimeUnit.MILLISECONDS, EXECUTOR);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                boolean result;
                try {
                    result = ready.getAsBoolean();
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    return;
                }
                long timeLeft = deadline - System.currentTimeMillis();
                if (!future.isDone()) {
                    if (!result) {
                        if (timeLeft >= 0) {
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("{} not ready, will try again ({}ms till timeout)",
                                    description, timeLeft);
                            }
                            delayed.execute(this);
                        } else {
                            future.completeExceptionally(new TimeoutException(
                                String.format("Waiting for %s timeout %s exceeded", description, timeoutMs)));
                        }
                    } else {
                        future.complete(null);
                    }
                }
            }
        };
        r.run();
        return future;
    }
}
