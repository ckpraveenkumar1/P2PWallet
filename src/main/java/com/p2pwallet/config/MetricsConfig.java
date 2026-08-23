package com.p2pwallet.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers custom Micrometer metrics for domain events.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter transfersCompletedCounter(MeterRegistry registry) {
        return Counter.builder("p2p_transfers_total")
                .tag("status", "completed")
                .description("Total completed transfers")
                .register(registry);
    }

    @Bean
    public Counter transfersDeclinedCounter(MeterRegistry registry) {
        return Counter.builder("p2p_transfers_total")
                .tag("status", "declined")
                .description("Total declined transfers")
                .register(registry);
    }

    @Bean
    public Counter transfersDeclinedInsufficientFundsCounter(MeterRegistry registry) {
        return Counter.builder("p2p_transfers_declined_insufficient_funds_total")
                .description("Transfers declined due to insufficient funds")
                .register(registry);
    }

    @Bean
    public Counter idempotentReplaysCounter(MeterRegistry registry) {
        return Counter.builder("p2p_transfers_idempotent_replays_total")
                .description("Idempotent transfer replay hits")
                .register(registry);
    }

    @Bean
    public Counter walletsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("p2p_wallets_created_total")
                .description("Total new wallets created")
                .register(registry);
    }
}
