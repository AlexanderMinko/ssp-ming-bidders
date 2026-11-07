package com.ming.bidders.catalog;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ming.bidders.model.entity.Bidder;
import com.ming.bidders.model.entity.Creative;
import com.ming.bidders.repository.BidderRepository;
import com.ming.bidders.repository.CreativeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory bidders + creatives, so the request path never touches Mongo. The first load runs after the
 * migrations (Order 2 vs 1) and fails startup if Mongo is unreachable; scheduled refreshes keep the last good
 * snapshot when a load fails. Readiness turns UP only after both runners finish.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class BidderCatalog implements ApplicationRunner {

    /** Immutable view of all bidders and their creatives; swapped whole on every refresh. */
    private record Snapshot(Map<String, Bidder> biddersByKey, Map<String, List<Creative>> creativesByBidderId) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }

    private final BidderRepository bidders;
    private final CreativeRepository creatives;

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    @Override
    public void run(ApplicationArguments args) {
        snapshot = load();
        log.info("Catalog loaded: {} bidders, {} with creatives",
                snapshot.biddersByKey().size(), snapshot.creativesByBidderId().size());
    }

    @Scheduled(fixedDelayString = "${bidders.catalog.refresh-ms}", initialDelayString = "${bidders.catalog.refresh-ms}")
    public void refresh() {
        try {
            snapshot = load();
        } catch (RuntimeException exception) {
            log.warn("Catalog refresh failed, keeping last good snapshot", exception);
        }
    }

    public Optional<Bidder> bidder(String key) {
        return Optional.ofNullable(snapshot.biddersByKey().get(key));
    }

    public List<Creative> creatives(String bidderId) {
        return snapshot.creativesByBidderId().getOrDefault(bidderId, List.of());
    }

    private Snapshot load() {
        Map<String, Bidder> byKey = bidders.findAll().stream()
                .collect(toMap(Bidder::key, identity()));
        Map<String, List<Creative>> byBidder = creatives.findAll().stream()
                .collect(groupingBy(Creative::bidderId));
        return new Snapshot(Map.copyOf(byKey), Map.copyOf(byBidder));
    }
}
