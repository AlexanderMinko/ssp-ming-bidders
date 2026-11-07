package com.ming.bidders.engine;

/** Blocks for the simulated latency. A seam so tests don't actually sleep. */
@FunctionalInterface
public interface Sleeper {

    void sleep(long ms);
}
