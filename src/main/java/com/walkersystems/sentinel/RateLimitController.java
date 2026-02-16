package com.walkersystems.sentinel;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    @GetMapping("/check")
    public Mono<ResponseEntity<Map<String, Boolean>>> checkRateLimit(
            @RequestHeader(value = "X-User-ID", defaultValue = "guest") String userId,
            @RequestParam(defaultValue = "10") int capacity,
            @RequestParam(defaultValue = "1") int rate,
            @RequestParam(defaultValue = "1") int cost
    ) {
        return rateLimiterService.isAllowed(userId, capacity, rate, cost)
                .map(allowed -> {
                    if (allowed) {
                        return ResponseEntity.ok(Map.of("allowed", true));
                    } else {
                        return ResponseEntity.status(429).body(Map.of("allowed", false));
                    }
                });
    }

}
