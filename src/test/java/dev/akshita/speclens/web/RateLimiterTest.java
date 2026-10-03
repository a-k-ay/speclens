package dev.akshita.speclens.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

	/** A clock the test can move forward. */
	static final class TestClock extends Clock {

		Instant now = Instant.parse("2026-10-03T10:00:00Z");

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

		void advance(Duration d) {
			now = now.plus(d);
		}

	}

	private final TestClock clock = new TestClock();

	@Test
	void perMinuteLimitResetsInTheNextMinute() {
		RateLimiter limiter = new RateLimiter(new RateLimitProperties(3, 100, 1000), clock);

		for (int i = 0; i < 3; i++) {
			assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		}
		RateLimiter.Decision refused = limiter.tryAcquire("1.1.1.1");
		assertThat(refused.allowed()).isFalse();
		assertThat(refused.retryAfterSeconds()).isBetween(1L, 60L);

		clock.advance(Duration.ofMinutes(1));
		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
	}

	@Test
	void clientsAreCountedSeparately() {
		RateLimiter limiter = new RateLimiter(new RateLimitProperties(1, 100, 1000), clock);

		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();
		assertThat(limiter.tryAcquire("2.2.2.2").allowed()).isTrue();
	}

	@Test
	void dailyLimitHoldsAcrossMinutes() {
		RateLimiter limiter = new RateLimiter(new RateLimitProperties(10, 2, 1000), clock);

		limiter.tryAcquire("1.1.1.1");
		clock.advance(Duration.ofMinutes(5));
		limiter.tryAcquire("1.1.1.1");
		clock.advance(Duration.ofMinutes(5));

		RateLimiter.Decision refused = limiter.tryAcquire("1.1.1.1");
		assertThat(refused.allowed()).isFalse();
		assertThat(refused.reason()).contains("today's limit");
	}

	@Test
	void globalLimitStopsEveryoneAndRefusalsDontUseQuota() {
		RateLimiter limiter = new RateLimiter(new RateLimitProperties(10, 10, 2), clock);

		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		assertThat(limiter.tryAcquire("2.2.2.2").allowed()).isTrue();
		assertThat(limiter.tryAcquire("3.3.3.3").reason()).contains("daily question limit");

		// Next UTC day, everyone may ask again.
		clock.advance(Duration.ofDays(1));
		assertThat(limiter.tryAcquire("3.3.3.3").allowed()).isTrue();
	}

}
