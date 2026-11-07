package com.ming.bidders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MongoContainerConfig.class)
class SspMingBiddersApplicationTests {

    @Test
    void contextLoads() {
    }
}
