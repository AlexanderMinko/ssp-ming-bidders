package com.ming.bidders.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ming.bidders.model.entity.Creative;
import com.ming.bidders.model.entity.CreativeFormat;
import com.ming.bidders.model.entity.VideoSpec;
import com.ming.bidders.model.openrtb.Banner;
import com.ming.bidders.model.openrtb.Imp;
import com.ming.bidders.model.openrtb.Video;

class CreativeSelectorTest {

    /** nextLong() == 0 → nextInt(n) == 0: always the first survivor. */
    private static final RandomGenerator FIRST = () -> 0L;

    private final CreativeSelector selector = new CreativeSelector(FIRST);

    private static Creative banner(String crid, int w, int h, double price) {
        return new Creative(crid, "b-1", crid, CreativeFormat.BANNER, w, h, price,
                List.of("adv.example"), List.of("IAB1"), "<div/>", null);
    }

    private static Creative video(String crid, int w, int h, double price, int durationSec, List<String> mimes) {
        return new Creative(crid, "b-1", crid, CreativeFormat.VIDEO, w, h, price,
                List.of("adv.example"), List.of("IAB1"), "<VAST/>", new VideoSpec(durationSec, mimes));
    }

    private static Imp bannerImp(Integer w, Integer h, Double floor) {
        return new Imp("imp-1", new Banner(w, h), null, floor, "USD");
    }

    private static Imp videoImp(List<String> mimes, Integer maxduration) {
        return new Imp("imp-1", null, new Video(320, 480, mimes, maxduration), null, "USD");
    }

    @Test
    void matchesBannerBySize() {
        var creatives = List.of(banner("a", 300, 250, 1.0));

        assertThat(selector.select(bannerImp(300, 250, null), creatives))
                .hasValueSatisfying(pick -> assertThat(pick.creative().crid()).isEqualTo("a"));
        assertThat(selector.select(bannerImp(320, 50, null), creatives)).isEmpty();
    }

    @Test
    void bannerImpNeverMatchesVideoCreative() {
        var creatives = List.of(video("v", 300, 250, 5.0, 15, List.of("video/mp4")));

        assertThat(selector.select(bannerImp(300, 250, null), creatives)).isEmpty();
    }

    @Test
    void nativeOnlyImpMatchesNothing() {
        var nativeImp = new Imp("imp-1", null, null, null, "USD");

        assertThat(selector.select(nativeImp, List.of(banner("a", 300, 250, 1.0)))).isEmpty();
    }

    @Test
    void bannerWithoutSizeMatchesNothing() {
        assertThat(selector.select(bannerImp(null, null, null), List.of(banner("a", 300, 250, 1.0)))).isEmpty();
    }

    @Test
    void floorFiltersCheaperCreatives() {
        var creatives = List.of(banner("cheap", 300, 250, 0.40), banner("rich", 300, 250, 1.50));

        assertThat(selector.select(bannerImp(300, 250, 1.0), creatives))
                .hasValueSatisfying(pick -> assertThat(pick.creative().crid()).isEqualTo("rich"));
    }

    @Test
    void nullAndZeroFloorBehaveTheSame() {
        var creatives = List.of(banner("cheap", 300, 250, 0.40));

        assertThat(selector.select(bannerImp(300, 250, null), creatives)).isPresent();
        assertThat(selector.select(bannerImp(300, 250, 0.0), creatives)).isPresent();
    }

    @Test
    void videoRespectsMaxDurationAndMimes() {
        var creatives = List.of(video("v", 320, 480, 5.0, 15, List.of("video/mp4")));

        assertThat(selector.select(videoImp(null, 10), creatives)).isEmpty();
        assertThat(selector.select(videoImp(List.of("video/webm"), null), creatives)).isEmpty();
        assertThat(selector.select(videoImp(null, null), creatives)).isPresent();
        assertThat(selector.select(videoImp(List.of("video/webm", "video/mp4"), 30), creatives)).isPresent();
    }

    @Test
    void multiFormatImpConsidersBothFormats() {
        var both = new Imp("imp-1", new Banner(300, 250), new Video(320, 480, List.of("video/mp4"), 30), null, "USD");

        // banner size has no creative, the video does → the video wins
        assertThat(selector.select(both, List.of(video("v", 320, 480, 5.0, 15, List.of("video/mp4")))))
                .hasValueSatisfying(pick -> assertThat(pick.creative().crid()).isEqualTo("v"));
        // and the banner still matches when it is the one with a creative
        assertThat(selector.select(both, List.of(banner("b", 300, 250, 1.0))))
                .hasValueSatisfying(pick -> assertThat(pick.creative().crid()).isEqualTo("b"));
    }

    @Test
    void pickCarriesTheImp() {
        var imp = bannerImp(300, 250, null);

        assertThat(selector.select(imp, List.of(banner("a", 300, 250, 1.0))))
                .hasValueSatisfying(pick -> assertThat(pick.imp()).isSameAs(imp));
    }

    @Test
    void picksRandomlyAmongSurvivors() {
        var seeded = new CreativeSelector(new Random(1));
        var creatives = List.of(banner("x", 300, 250, 1.0), banner("y", 300, 250, 1.0));
        var picked = new HashSet<String>();

        IntStream.range(0, 200).forEach(round -> seeded.select(bannerImp(300, 250, null), creatives)
                .ifPresent(pick -> picked.add(pick.creative().crid())));

        assertThat(picked).containsExactlyInAnyOrder("x", "y");
    }
}
