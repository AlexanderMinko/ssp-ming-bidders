package com.ming.bidders.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.ming.bidders.MongoContainerConfig;

@SpringBootTest
@Import(MongoContainerConfig.class)
class MongoMigrationIT {

    @Autowired MongoTemplate mongo;
    @Autowired MongoMigrationRunner runner;

    @Test
    void seedsBiddersAndCreatives() {
        assertThat(mongo.getCollection("bidders").countDocuments()).isEqualTo(3);
        // >= 7, not == 7: BidderCatalogIT shares this context and may have inserted its own test creative.
        assertThat(mongo.getCollection("creatives").countDocuments()).isGreaterThanOrEqualTo(7);
        assertThat(mongo.findById("cr-alpha-300x250-paused", Document.class, "creatives"))
                .as("no status flag any more; the former paused creative is simply not seeded").isNull();

        var alpha = mongo.findOne(Query.query(Criteria.where("key").is("alpha")), Document.class, "bidders");
        assertThat(alpha).isNotNull();
        assertThat(alpha.get("bidRate", Number.class).doubleValue()).isEqualTo(1.0);
    }

    @Test
    void keepsExchangeMacrosInSeedData() {
        // Liquibase expands ${...} changelog parameters; the exchange macros must survive untouched.
        var alpha = mongo.findOne(Query.query(Criteria.where("key").is("alpha")), Document.class, "bidders");
        assertThat(alpha.getString("nurlTemplate")).contains("${AUCTION_PRICE}");

        var creative = mongo.findOne(Query.query(Criteria.where("crid").is("alpha-300x250-hi")), Document.class, "creatives");
        assertThat(creative.getString("adm")).contains("${AUCTION_PRICE}");
    }

    @Test
    void createsIndexes() {
        var bidderIndexes = mongo.getCollection("bidders").listIndexes().into(new java.util.ArrayList<>());
        assertThat(bidderIndexes).anySatisfy(index -> {
            assertThat(index.get("key", Document.class)).isEqualTo(new Document("key", 1));
            assertThat(index.getBoolean("unique", false)).isTrue();
        });

        var creativeIndexes = mongo.getCollection("creatives").listIndexes().into(new java.util.ArrayList<>());
        assertThat(creativeIndexes).anySatisfy(index -> assertThat(index.get("key", Document.class))
                .isEqualTo(new Document("bidderId", 1).append("format", 1).append("w", 1).append("h", 1)));
    }

    @Test
    void rerunAppliesNothing() throws Exception {
        long changelogBefore = mongo.getCollection("DATABASECHANGELOG").countDocuments();

        runner.run(null);

        assertThat(mongo.getCollection("DATABASECHANGELOG").countDocuments()).isEqualTo(changelogBefore);
        assertThat(mongo.getCollection("bidders").countDocuments()).isEqualTo(3);
    }
}
