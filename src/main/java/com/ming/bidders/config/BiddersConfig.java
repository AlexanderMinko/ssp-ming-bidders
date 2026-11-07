package com.ming.bidders.config;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ming.bidders.engine.Sleeper;

/** The two seams the bid path needs from the outside world (randomness, real time) plus catalog scheduling. */
@Configuration
@EnableScheduling
public class BiddersConfig {

    /**
     * One randomness bean for the bid path (latency jitter, bid-rate roll, creative pick), so tests can replace it.
     * Each call delegates to the calling thread's ThreadLocalRandom — never hold a ThreadLocalRandom in a singleton.
     */
    @Bean
    RandomGenerator random() {
        return () -> ThreadLocalRandom.current().nextLong();
    }

    /** Real latency: Thread.sleep on the request's virtual thread. An interrupt ends the sleep early. */
    @Bean
    Sleeper sleeper() {
        return milliseconds -> {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
