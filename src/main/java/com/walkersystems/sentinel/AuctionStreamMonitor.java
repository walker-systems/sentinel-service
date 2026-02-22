package com.walkersystems.sentinel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionStreamMonitor {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private ChatClient chatClient;

    @PostConstruct
    public void startMonitoring() {
        this.chatClient = chatClientBuilder.build();

        log.info("🛡️ Sentinel Stream Monitor starting... listening to 'auction:updates'");

        redisTemplate.listenTo(ChannelTopic.of("auction:updates"))
                .flatMap(message -> analyzeBidWithAI(message.getMessage()))
                .subscribe();
    }

    private Mono<Void> analyzeBidWithAI(String rawJson) {
        return Mono.fromCallable(() -> {
                    AuctionDto auction = objectMapper.readValue(rawJson, AuctionDto.class);

                    log.info("🧠 Sentinel analyzing bid by {}...", auction.highBidder());

                    String prompt = String.format(
                            "You are a fraud detection bot for an auction site. " +
                                    "A user named '%s' just placed a bid. " +
                                    "If the username contains 'bot', 'script', or 'test', reply ONLY with 'FRAUD'. " +
                                    "Otherwise, reply ONLY with 'CLEAN'.",
                            auction.highBidder()
                    );

                    String aiDecision = chatClient.prompt(prompt).call().content();

                    if (aiDecision != null && "FRAUD".equals(aiDecision.trim())) {
                        log.warn("🚨 AI SENTINEL ALERT: Fraudulent activity detected from user '{}'!", auction.highBidder());
                    } else {
                        log.info("✅ AI Sentinel cleared user '{}'.", auction.highBidder());
                    }

                    return "Done"; // Return a dummy string instead of null
                })
                .then() // <--- THIS magically converts Mono<String> into Mono<Void>
                .onErrorResume(e -> {
                    log.error("💥 Failed to process AI hook: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
