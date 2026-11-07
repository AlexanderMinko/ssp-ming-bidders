package com.ming.bidders.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.ming.bidders.model.entity.Creative;
import com.ming.bidders.model.openrtb.Imp;
import com.ming.bidders.model.openrtb.Video;

import lombok.RequiredArgsConstructor;

/**
 * Picks a creative for one imp the way a DSP would: a creative fits when the imp offers its format at its exact
 * size (and, for video, within maxduration and mimes) and it is priced at or above the floor; then a random pick
 * among those that fit. A multi-format imp (banner + video) considers both. An imp offering neither format, or
 * no size, matches nothing.
 */
@Component
@RequiredArgsConstructor
public class CreativeSelector {

    private final RandomGenerator random;

    public Optional<Pick> select(Imp imp, List<Creative> creatives) {
        double floor = Objects.requireNonNullElse(imp.bidfloor(), 0.0);
        var survivors = creatives.stream()
                .filter(creative -> fits(imp, creative))
                .filter(creative -> creative.priceCpm() >= floor)
                .toList();
        if (survivors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Pick(imp, survivors.get(random.nextInt(survivors.size()))));
    }

    private static boolean fits(Imp imp, Creative creative) {
        return switch (creative.format()) {
            case BANNER -> Objects.nonNull(imp.banner())
                    && sizeMatches(imp.banner().w(), imp.banner().h(), creative);
            case VIDEO -> Objects.nonNull(imp.video())
                    && sizeMatches(imp.video().w(), imp.video().h(), creative)
                    && videoLimitsMatch(imp.video(), creative);
        };
    }

    private static boolean sizeMatches(Integer w, Integer h, Creative creative) {
        return Objects.nonNull(w) && Objects.nonNull(h) && w == creative.w() && h == creative.h();
    }

    private static boolean videoLimitsMatch(Video video, Creative creative) {
        if (Objects.isNull(creative.video())) {
            return false;
        }
        boolean durationOk = Objects.isNull(video.maxduration())
                || creative.video().durationSec() <= video.maxduration();
        boolean mimeOk = CollectionUtils.isEmpty(video.mimes())
                || CollectionUtils.containsAny(video.mimes(), CollectionUtils.emptyIfNull(creative.video().mimes()));
        return durationOk && mimeOk;
    }
}
