package io.tacticl.client.arbiter;

import cidadel.ai.arbiter.pipeline.v1.ArbiterPipelineServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArbiterClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ArbiterClientConfig.class);

    /**
     * Real gRPC channel — only created when pdlc.v2.arbiter.host is configured.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "pdlc.v2.arbiter.host")
    public ManagedChannel arbiterManagedChannel(
            @Value("${pdlc.v2.arbiter.host}") String host,
            @Value("${pdlc.v2.arbiter.port:50051}") int port,
            @Value("${pdlc.v2.arbiter.tls:false}") boolean tls) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (!tls) {
            builder.usePlaintext();
        }
        log.info("Creating arbiter gRPC channel: {}:{} tls={}", host, port, tls);
        return builder.build();
    }

    /**
     * Real gRPC client — registered when pdlc.v2.arbiter.host is configured.
     */
    @Bean
    @ConditionalOnProperty(name = "pdlc.v2.arbiter.host")
    public ArbiterPipelineService arbiterGrpcClient(
            ManagedChannel arbiterManagedChannel,
            @Value("${pdlc.v2.arbiter.registry-base-path:gs://tacticl-artifacts/agent-registry}") String registryBasePath) {
        ArbiterPipelineServiceGrpc.ArbiterPipelineServiceBlockingStub stub =
            ArbiterPipelineServiceGrpc.newBlockingStub(arbiterManagedChannel);
        log.info("Arbiter gRPC client ready (registryBasePath={})", registryBasePath);
        return new ArbiterGrpcClientImpl(stub, registryBasePath);
    }

    /**
     * No-op stub — fallback when arbiter host is not configured.
     */
    @Bean
    @ConditionalOnMissingBean(ArbiterPipelineService.class)
    public ArbiterPipelineService arbiterPipelineServiceStub() {
        log.warn("pdlc.v2.arbiter.host not configured — using ArbiterPipelineServiceStub (no-op)");
        return new ArbiterPipelineServiceStub();
    }
}
