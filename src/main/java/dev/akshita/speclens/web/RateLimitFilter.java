package dev.akshita.speclens.web;

import java.io.IOException;
import java.time.Clock;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate-limits the endpoints that call Gemini (ask, upload, story generation), per client
 * IP and globally. Behind Render's proxy the client IP comes from X-Forwarded-For, which
 * Spring trusts because server.forward-headers-strategy is set (see application.yml).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Pattern LIMITED_PATH = Pattern.compile("^/api/projects/\\d+/(ask|stories|documents)$");

	private final RateLimiter limiter;

	public RateLimitFilter(RateLimitProperties properties) {
		this.limiter = new RateLimiter(properties, Clock.systemUTC());
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !LIMITED_PATH.matcher(request.getRequestURI()).matches();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		RateLimiter.Decision decision = limiter.tryAcquire(request.getRemoteAddr());
		if (decision.allowed()) {
			chain.doFilter(request, response);
			return;
		}
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		// Messages are fixed strings without quotes, so no JSON escaping is needed.
		response.getWriter().write("""
				{"type":"about:blank","title":"Too Many Requests","status":429,"detail":"%s"}"""
				.formatted(decision.reason()));
	}

}
