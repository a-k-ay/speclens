package dev.akshita.speclens.web;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-window request counters: per client per minute, per client per UTC day, and for
 * everyone together per UTC day. In memory, which is fine for a single instance; several
 * instances would need a shared store such as Redis.
 *
 * Methods are synchronized: the demo's traffic is tiny, and correctness (no lost updates
 * between check and increment) matters more than throughput here.
 */
public class RateLimiter {

	private static final long MINUTE_MS = 60_000;
	private static final long DAY_MS = 86_400_000;
	private static final int MAX_TRACKED_CLIENTS = 10_000;

	/** Why a request was refused, and how long until the window resets. */
	public record Decision(boolean allowed, String reason, long retryAfterSeconds) {

		static final Decision ALLOWED = new Decision(true, null, 0);

	}

	private static final class Window {

		long index;
		int count;

	}

	private final RateLimitProperties limits;
	private final Clock clock;
	private final Map<String, Window> perMinute = new HashMap<>();
	private final Map<String, Window> perDay = new HashMap<>();
	private final Window global = new Window();

	public RateLimiter(RateLimitProperties limits, Clock clock) {
		this.limits = limits;
		this.clock = clock;
	}

	public synchronized Decision tryAcquire(String client) {
		long now = clock.millis();
		long minute = now / MINUTE_MS;
		long day = now / DAY_MS;
		evictIfLarge(minute, day);

		Window m = current(perMinute.computeIfAbsent(client, c -> new Window()), minute);
		Window d = current(perDay.computeIfAbsent(client, c -> new Window()), day);
		Window g = current(global, day);

		// Check every limit before counting, so a refused request doesn't use up quota.
		if (g.count >= limits.globalPerDay()) {
			return refuse("The demo has reached its daily question limit. Please try again tomorrow.",
					secondsUntil(now, day + 1, DAY_MS));
		}
		if (d.count >= limits.perIpPerDay()) {
			return refuse("You have reached today's limit of " + limits.perIpPerDay() + " requests.",
					secondsUntil(now, day + 1, DAY_MS));
		}
		if (m.count >= limits.perIpPerMinute()) {
			return refuse("Too many requests. Please wait a minute and try again.",
					secondsUntil(now, minute + 1, MINUTE_MS));
		}
		m.count++;
		d.count++;
		g.count++;
		return Decision.ALLOWED;
	}

	private static Window current(Window w, long index) {
		if (w.index != index) {
			w.index = index;
			w.count = 0;
		}
		return w;
	}

	/** Drop counters from past windows so memory stays bounded. */
	private void evictIfLarge(long minute, long day) {
		if (perMinute.size() > MAX_TRACKED_CLIENTS) {
			perMinute.values().removeIf(w -> w.index != minute);
		}
		if (perDay.size() > MAX_TRACKED_CLIENTS) {
			perDay.values().removeIf(w -> w.index != day);
		}
	}

	private static Decision refuse(String reason, long retryAfterSeconds) {
		return new Decision(false, reason, Math.max(1, retryAfterSeconds));
	}

	private static long secondsUntil(long now, long nextWindowIndex, long windowMs) {
		return (nextWindowIndex * windowMs - now + 999) / 1000;
	}

}
