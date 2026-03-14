package com.decisionfabric

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class DecisionFabricAiApplicationTest {

    @Test
    fun `application context loads`() {
        // Verifies that Spring context starts without errors
    }
}
