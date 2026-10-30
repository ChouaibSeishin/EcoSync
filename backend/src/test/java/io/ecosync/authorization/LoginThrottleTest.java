package io.ecosync.authorization;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LoginThrottleTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-30T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Test void limitsAccountAttemptsAndRecoversAfterWindow() {
        var clock = new MutableClock(); var limiter = new LoginThrottle(clock);
        for (int i=0;i<5;i++) assertThat(limiter.allow("address", "user")).isTrue();
        assertThat(limiter.allow("another-address", "user")).isFalse();
        clock.now = clock.now.plusSeconds(60);
        assertThat(limiter.allow("address", "user")).isTrue();
    }
    @Test void successfulLoginDoesNotResetAddressBudget() {
        var limiter = new LoginThrottle(Clock.systemUTC());
        for (int i=0;i<40;i++) { assertThat(limiter.allow("address", "u"+i)).isTrue(); limiter.succeeded("u"+i); }
        assertThat(limiter.allow("address", "fresh")).isFalse();
    }
}
