package com.decisionfabric.adapter.outbound.cache

import com.decisionfabric.application.ports.`in`.RuleManagementUseCase
import com.decisionfabric.application.rule.command.ActivateVersionCommand
import com.decisionfabric.application.rule.command.CreateRuleCommand
import com.decisionfabric.application.rule.command.CreateRuleSetCommand
import com.decisionfabric.domain.rule.RuleSetId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Integration test verifying that publishing [RuleLifecycleEvent.RuleVersionActivated]
 * (which happens inside [RuleManagementUseCase.activateVersion]) causes [InMemoryRuleCache]
 * to reflect the newly-active version after the transaction commits.
 *
 * Uses the Testcontainers JDBC URL configured in application-test.yml
 * (`jdbc:tc:postgresql:...`) which starts a real PostgreSQL container via
 * Testcontainers' Driver.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class InMemoryRuleCacheIntegrationTest {

    @Autowired
    private lateinit var ruleManagement: RuleManagementUseCase

    @Autowired
    private lateinit var cache: InMemoryRuleCache

    private val validDmn =
        "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" " +
                "id=\"d\" name=\"d\" namespace=\"ns\"/>"

    @Test
    fun `activating a rule version updates the cache after commit`() {
        val correlationId = UUID.randomUUID().toString()
        val userId = "integration-test"

        // 1. Create rule set
        val ruleSet = ruleManagement.createRuleSet(
            CreateRuleSetCommand(
                name = "Cache Integration Test Set ${correlationId.take(8)}",
                description = "test",
                userId = userId,
                correlationId = correlationId
            )
        )

        // 2. Create rule (creates version 1 in DRAFT)
        val rule = ruleManagement.createRule(
            CreateRuleCommand(
                ruleSetId = RuleSetId(ruleSet.id.value),
                name = "Cache Test Rule ${correlationId.take(8)}",
                description = "test",
                dmnXml = validDmn,
                userId = userId,
                correlationId = correlationId
            )
        )

        // 3. Activate version 1 — this commits the transaction and fires RuleVersionActivated
        ruleManagement.activateVersion(
            ActivateVersionCommand(
                ruleId = rule.id,
                version = 1,
                userId = userId,
                correlationId = correlationId
            )
        )

        // 4. After AFTER_COMMIT event listener fires, the cache must contain the active version
        val cachedVersions = cache.getActiveVersions(rule.id.value)
        assertThat(cachedVersions)
            .describedAs("cache should contain version 1 after activation")
            .isNotEmpty
        assertThat(cachedVersions.first().version).isEqualTo(1)
    }
}
