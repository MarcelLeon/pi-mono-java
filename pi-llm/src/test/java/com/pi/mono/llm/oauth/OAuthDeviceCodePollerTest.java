package com.pi.mono.llm.oauth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthDeviceCodePollerTest {

    @Test
    void waitsBeforeFirstPollWhenRequested() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        List<Long> pollTimes = new ArrayList<>();

        OAuthDeviceCodePoller<String> poller = new OAuthDeviceCodePoller<>(clock, sleeper);

        String token = poller.poll(new OAuthDeviceCodePoller.PollOptions<>(
            2,
            30,
            true,
            () -> {
                pollTimes.add(clock.millis());
                return OAuthDeviceCodePoller.PollResult.complete("token");
            }
        ));

        assertEquals("token", token);
        assertEquals(List.of(2000L), pollTimes);
        assertEquals(List.of(2000L), sleeper.delaysMs);
    }

    @Test
    void increasesIntervalByFiveSecondsAfterSlowDownWithoutServerInterval() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        List<Long> pollTimes = new ArrayList<>();
        AtomicInteger attempt = new AtomicInteger();

        OAuthDeviceCodePoller<String> poller = new OAuthDeviceCodePoller<>(clock, sleeper);

        String token = poller.poll(new OAuthDeviceCodePoller.PollOptions<>(
            2,
            900,
            false,
            () -> {
                pollTimes.add(clock.millis());
                if (attempt.getAndIncrement() == 0) {
                    return OAuthDeviceCodePoller.PollResult.slowDown();
                }
                return OAuthDeviceCodePoller.PollResult.complete("token");
            }
        ));

        assertEquals("token", token);
        assertEquals(List.of(0L, 7000L), pollTimes);
        assertEquals(List.of(7000L), sleeper.delaysMs);
    }

    @Test
    void honorsServerProvidedSlowDownInterval() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        List<Long> pollTimes = new ArrayList<>();
        AtomicInteger attempt = new AtomicInteger();

        OAuthDeviceCodePoller<String> poller = new OAuthDeviceCodePoller<>(clock, sleeper);

        String token = poller.poll(new OAuthDeviceCodePoller.PollOptions<>(
            2,
            900,
            false,
            () -> {
                pollTimes.add(clock.millis());
                if (attempt.getAndIncrement() == 0) {
                    return OAuthDeviceCodePoller.PollResult.slowDown(30);
                }
                return OAuthDeviceCodePoller.PollResult.complete("token");
            }
        ));

        assertEquals("token", token);
        assertEquals(List.of(0L, 30000L), pollTimes);
        assertEquals(List.of(30000L), sleeper.delaysMs);
    }

    static class MutableClock extends Clock {
        private long millis;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        void advance(long millis) {
            this.millis += millis;
        }
    }

    static class RecordingSleeper implements OAuthDeviceCodePoller.Sleeper {
        private final MutableClock clock;
        private final List<Long> delaysMs = new ArrayList<>();

        RecordingSleeper(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public void sleep(long millis) {
            delaysMs.add(millis);
            clock.advance(millis);
        }
    }
}
