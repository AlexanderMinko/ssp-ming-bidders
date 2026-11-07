package com.ming.bidders.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.bidders.catalog.BidderCatalog;
import com.ming.bidders.metrics.BidderMetrics;
import com.ming.bidders.model.entity.Bidder;
import com.ming.bidders.model.openrtb.BidRequest;
import com.ming.bidders.model.openrtb.Imp;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BidEngineTest {

    private static final RandomGenerator ZERO = () -> 0L;   // nextDouble() == 0.0 → passes any bidRate > 0

    private final BidderCatalog catalog = mock(BidderCatalog.class);
    private final CreativeSelector selector = mock(CreativeSelector.class);
    private final List<Long> slept = new ArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private BidEngine engine(RandomGenerator random) {
        return engine(random, selector);
    }

    private BidEngine engine(RandomGenerator random, CreativeSelector creativeSelector) {
        return new BidEngine(catalog, creativeSelector, Mappers.getMapper(BidMapper.class),
                new BidderMetrics(registry), random, slept::add);
    }

    private void given(Bidder bidder) {
        when(catalog.bidder("alpha")).thenReturn(Optional.of(bidder));
        when(catalog.creatives(bidder.id())).thenReturn(List.of(TestData.bannerCreative("cr-1", 1.0)));
    }

    private void everyImpMatches() {
        when(selector.select(any(Imp.class), anyList())).thenAnswer(invocation ->
                Optional.of(new Pick(invocation.getArgument(0), TestData.bannerCreative("cr-1", 1.0))));
    }

    @Test
    void unknownBidderReturnsUnknown() {
        when(catalog.bidder("nope")).thenReturn(Optional.empty());

        assertThat(engine(ZERO).bid("nope", TestData.request(TestData.bannerImp("1"))))
                .isInstanceOf(BidOutcome.UnknownBidder.class);
        assertThat(registry.counter("bidders.requests", "bidder", "unknown", "outcome", "UNKNOWN", "reason", "none")
                .count()).isEqualTo(1.0);
        assertThat(slept).isEmpty();
    }

    @Test
    void bidRateZeroNeverBids() {
        given(TestData.bidder(0, 0, 0.0));
        everyImpMatches();

        assertThat(engine(ZERO).bid("alpha", TestData.request(TestData.bannerImp("1"))))
                .isEqualTo(new BidOutcome.NoBid(NoBidReason.BID_RATE));
    }

    @Test
    void bidRateOneAlwaysBids() {
        given(TestData.bidder(0, 0, 1.0));
        everyImpMatches();
        RandomGenerator almostOne = () -> -1L;   // nextDouble() just below 1.0

        assertThat(engine(almostOne).bid("alpha", TestData.request(TestData.bannerImp("1"))))
                .isInstanceOf(BidOutcome.Bid.class);
    }

    @Test
    void latencyIsSimulatedEvenWhenItExceedsTmax() {
        given(TestData.bidder(600, 0, 1.0));
        everyImpMatches();

        engine(ZERO).bid("alpha", new BidRequest("req-1", List.of(TestData.bannerImp("1")), 100));

        assertThat(slept).containsExactly(600L);
    }

    @Test
    void noMatchingCreativeReturnsNoMatch() {
        given(TestData.bidder(0, 0, 1.0));
        when(selector.select(any(Imp.class), anyList())).thenReturn(Optional.empty());

        assertThat(engine(ZERO).bid("alpha", TestData.request(TestData.bannerImp("1"))))
                .isEqualTo(new BidOutcome.NoBid(NoBidReason.NO_MATCH));
    }

    @Test
    void bidsEveryMatchedImp() {
        given(TestData.bidder(0, 0, 1.0));
        everyImpMatches();

        var outcome = engine(ZERO).bid("alpha", TestData.request(TestData.bannerImp("1"), TestData.bannerImp("2")));

        assertThat(outcome).isInstanceOfSatisfying(BidOutcome.Bid.class,
                bid -> assertThat(bid.response().seatbid().get(0).bid()).hasSize(2));
        assertThat(registry.counter("bidders.requests", "bidder", "alpha", "outcome", "BID", "reason", "none")
                .count()).isEqualTo(1.0);
    }

    @Test
    void nullImpEntriesAreSkipped() {
        // Real selector: a mocked one never sees the null (any(Imp.class) does not match null).
        given(TestData.bidder(0, 0, 1.0));
        var engine = engine(ZERO, new CreativeSelector(ZERO));

        var mixed = engine.bid("alpha", new BidRequest("req-1", Arrays.asList(null, TestData.bannerImp("2")), 300));
        var onlyNull = engine.bid("alpha", new BidRequest("req-1", Arrays.asList((Imp) null), 300));

        assertThat(mixed).isInstanceOfSatisfying(BidOutcome.Bid.class,
                bid -> assertThat(bid.response().seatbid().get(0).bid()).extracting("impid").containsExactly("2"));
        assertThat(onlyNull).isEqualTo(new BidOutcome.NoBid(NoBidReason.NO_MATCH));
    }

    @Test
    void emptyImpListReturnsNoMatch() {
        given(TestData.bidder(0, 0, 1.0));

        assertThat(engine(ZERO).bid("alpha", new BidRequest("req-1", List.of(), 300)))
                .isEqualTo(new BidOutcome.NoBid(NoBidReason.NO_MATCH));
        assertThat(engine(ZERO).bid("alpha", new BidRequest("req-1", null, 300)))
                .isEqualTo(new BidOutcome.NoBid(NoBidReason.NO_MATCH));
    }

    // --- simulateLatency ---------------------------------------------------------------------------------------

    @Test
    void latencySleepsWithinJitterBounds() {
        var engine = engine(new Random(7));

        IntStream.range(0, 500).forEach(round -> engine.simulateLatency(TestData.bidder(30, 10, 1.0)));

        assertThat(slept).allSatisfy(milliseconds -> assertThat(milliseconds).isBetween(20L, 40L));
        assertThat(slept).contains(20L, 40L);   // both ends of the range are reachable
    }

    @Test
    void negativeLatencyClampsToZero() {
        RandomGenerator minimum = () -> 0L;   // nextInt(bound) == 0 → the lowest offset, -jitter

        long milliseconds = engine(minimum).simulateLatency(TestData.bidder(5, 50, 1.0));

        assertThat(milliseconds).isZero();
        assertThat(slept).containsExactly(0L);
    }

    @Test
    void zeroJitterSleepsExactLatency() {
        assertThat(engine(new Random(7)).simulateLatency(TestData.bidder(30, 0, 1.0))).isEqualTo(30L);
    }
}
