package com.ming.bidders.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.ming.bidders.MongoContainerConfig;

import io.micrometer.tracing.Tracer;

/**
 * Spec §8: with TRACING_ENABLED=true the bidder must have a real Tracer, so the exchange's traceparent is
 * picked up and bidder spans join the auction trace. Boot 4 ships that auto-config in its own module.
 */
@SpringBootTest(properties = "TRACING_ENABLED=true")
@Import(MongoContainerConfig.class)
class TracingIT {

    @Autowired ObjectProvider<Tracer> tracer;

    @Test
    void tracingEnabledProvidesARealTracer() {
        assertThat(tracer.getIfAvailable()).isNotNull().isNotSameAs(Tracer.NOOP);
    }
}
