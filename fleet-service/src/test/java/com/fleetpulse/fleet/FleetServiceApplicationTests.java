package com.fleetpulse.fleet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FleetServiceApplicationTests {

    @Test
    void contextLoads() {
        // just making sure the Spring context wires up cleanly
    }
}
