package io.tacticl.client.arbiter.conversation;

import cidadel.ai.arbiter.conversation.v1.ArbiterConversationServiceGrpc;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the arbiter conversation client on the SHARED arbiter channel
 * ({@code arbiterManagedChannel}, created from {@code pdlc.v2.arbiter.host}).
 *
 * <p>Gated on {@code tacticl.voice.arbiter-conversation.enabled=true} so the
 * conversation brain can be rolled independently of the pipeline path. When off,
 * no client bean exists and the voice plane falls back to its in-JVM engine.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.voice.arbiter-conversation.enabled", havingValue = "true")
public class ArbiterConversationClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ArbiterConversationClientConfig.class);

    /**
     * Per-turn stream cap. Defaults to 300s — the brain may read the repo and run tools before its
     * first token, so a substantive turn easily exceeds the old 60s voice cap. {@code <= 0} ⇒ the
     * client's own default.
     */
    @Value("${tacticl.voice.arbiter-conversation.deadline-seconds:300}")
    private long deadlineSeconds;

    @Bean
    public ConversationServiceClient arbiterConversationClient(ManagedChannel arbiterManagedChannel) {
        log.info("Arbiter conversation gRPC client ready (shared arbiter channel, deadline={}s)", deadlineSeconds);
        return new ArbiterConversationGrpcClient(
            ArbiterConversationServiceGrpc.newStub(arbiterManagedChannel), deadlineSeconds);
    }
}
