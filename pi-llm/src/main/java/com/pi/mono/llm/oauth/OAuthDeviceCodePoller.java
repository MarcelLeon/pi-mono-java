package com.pi.mono.llm.oauth;

import java.time.Clock;
import java.util.Objects;

/**
 * OAuth device-code polling helper shared by provider auth flows.
 */
public class OAuthDeviceCodePoller<T> {

    private static final long MINIMUM_INTERVAL_MS = 1000;
    private static final long SLOW_DOWN_INTERVAL_INCREMENT_MS = 5000;

    private final Clock clock;
    private final Sleeper sleeper;

    public OAuthDeviceCodePoller() {
        this(Clock.systemUTC(), Thread::sleep);
    }

    OAuthDeviceCodePoller(Clock clock, Sleeper sleeper) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public T poll(PollOptions<T> options) throws InterruptedException {
        Objects.requireNonNull(options, "options");
        long intervalMs = toIntervalMs(options.intervalSeconds());
        long deadline = clock.millis() + Math.max(0, options.expiresInSeconds()) * 1000L;

        if (options.waitBeforeFirstPoll()) {
            long remainingMs = deadline - clock.millis();
            if (remainingMs > 0) {
                sleeper.sleep(Math.min(intervalMs, remainingMs));
            }
        }

        while (clock.millis() < deadline) {
            PollResult<T> result = options.poller().poll();
            if (result.status() == PollStatus.COMPLETE) {
                return result.value();
            }
            if (result.status() == PollStatus.FAILED) {
                throw new IllegalStateException(result.message());
            }
            if (result.status() == PollStatus.SLOW_DOWN) {
                intervalMs = result.intervalSeconds() != null && result.intervalSeconds() > 0
                    ? toIntervalMs(result.intervalSeconds())
                    : Math.max(MINIMUM_INTERVAL_MS, intervalMs + SLOW_DOWN_INTERVAL_INCREMENT_MS);
            }

            long remainingMs = deadline - clock.millis();
            if (remainingMs > 0) {
                sleeper.sleep(Math.min(intervalMs, remainingMs));
            }
        }

        throw new IllegalStateException("OAuth device-code flow timed out");
    }

    private long toIntervalMs(long intervalSeconds) {
        return Math.max(MINIMUM_INTERVAL_MS, intervalSeconds * 1000L);
    }

    @FunctionalInterface
    public interface DeviceCodePoller<T> {
        PollResult<T> poll();
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public record PollOptions<T>(
        long intervalSeconds,
        long expiresInSeconds,
        boolean waitBeforeFirstPoll,
        DeviceCodePoller<T> poller
    ) {
        public PollOptions {
            Objects.requireNonNull(poller, "poller");
        }
    }

    public enum PollStatus {
        PENDING,
        SLOW_DOWN,
        FAILED,
        COMPLETE
    }

    public record PollResult<T>(
        PollStatus status,
        T value,
        String message,
        Long intervalSeconds
    ) {
        public static <T> PollResult<T> pending() {
            return new PollResult<>(PollStatus.PENDING, null, null, null);
        }

        public static <T> PollResult<T> slowDown() {
            return new PollResult<>(PollStatus.SLOW_DOWN, null, null, null);
        }

        public static <T> PollResult<T> slowDown(long intervalSeconds) {
            return new PollResult<>(PollStatus.SLOW_DOWN, null, null, intervalSeconds);
        }

        public static <T> PollResult<T> failed(String message) {
            return new PollResult<>(PollStatus.FAILED, null, message == null ? "" : message, null);
        }

        public static <T> PollResult<T> complete(T value) {
            return new PollResult<>(PollStatus.COMPLETE, value, null, null);
        }
    }
}
