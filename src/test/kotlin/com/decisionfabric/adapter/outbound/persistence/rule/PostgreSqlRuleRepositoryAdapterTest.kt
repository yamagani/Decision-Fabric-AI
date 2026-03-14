package com.decisionfabric.adapter.outbound.persistence.rule

import com.decisionfabric.domain.rule.DmnXmlContent
import com.decisionfabric.domain.rule.Rule
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSet
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleSetStatus
import com.decisionfabric.domain.rule.RuleStatus
import com.decisionfabric.domain.rule.RuleVersionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgreSqlRuleRepositoryAdapter::class, RuleJpaMapper::class)
class PostgreSqlRuleRepositoryAdapterTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { "true" }
        }
    }

    @Autowired
    private lateinit var adapter: PostgreSqlRuleRepositoryAdapter

    private val validDmn = "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" id=\"d\" name=\"d\" namespace=\"ns\"/>"

    private fun aRuleSet(name: String = "Test Set"): RuleSet =
        RuleSet.create(RuleSetId.generate(), name, "desc", "user1")

    private fun aRule(ruleSetId: RuleSetId, name: String = "Test Rule"): Rule =
        Rule.create(RuleId.generate(), ruleSetId, name, "desc", DmnXmlContent(validDmn), "user1")

    @Nested
    inner class RuleSetTests {
        @Test
        fun `saveRuleSet and findRuleSetById roundtrip`() {
            val ruleSet = aRuleSet()
            adapter.saveRuleSet(ruleSet)
            val found = adapter.findRuleSetById(ruleSet.id)
            assertThat(found).isNotNull()
            assertThat(found!!.name).isEqualTo("Test Set")
        }

        @Test
        fun `existsRuleSetByName is case-insensitive`() {
            val ruleSet = aRuleSet("MySet")
            adapter.saveRuleSet(ruleSet)
            assertThat(adapter.existsRuleSetByName("myset")).isTrue()
            assertThat(adapter.existsRuleSetByName("MYSET")).isTrue()
        }

        @Test
        fun `deactivated ruleSet not returned in active-only listing`() {
            val ruleSet = adapter.saveRuleSet(aRuleSet("ToDeactivate"))
            val deactivated = ruleSet.deactivate()
            adapter.saveRuleSet(deactivated)

            val result = adapter.findAllRuleSets(0, 100, includeInactive = false)
            assertThat(result.content.map { it.id }).doesNotContain(ruleSet.id)
        }
    }

    @Nested
    inner class RuleTests {
        private var ruleSetId: RuleSetId = RuleSetId.generate()

        @BeforeEach
        fun setUp() {
            val rs = adapter.saveRuleSet(aRuleSet())
            ruleSetId = rs.id
        }

        @Test
        fun `saveRule and findRuleById roundtrip`() {
            val rule = aRule(ruleSetId)
            adapter.saveRule(rule)
            val found = adapter.findRuleById(rule.id)
            assertThat(found).isNotNull()
            assertThat(found!!.versions).hasSize(1)
            assertThat(found.versions[0].status).isEqualTo(RuleVersionStatus.DRAFT)
        }

        @Test
        fun `activating version persists ACTIVE status`() {
            val rule = adapter.saveRule(aRule(ruleSetId))
            val activated = rule.activateVersion(1, "user1")
            adapter.saveRule(activated)

            val found = adapter.findRuleById(rule.id)!!
            assertThat(found.findVersion(1)!!.status).isEqualTo(RuleVersionStatus.ACTIVE)
            assertThat(found.findVersion(1)!!.activatedBy).isEqualTo("user1")
        }

        @Test
        fun `existsRuleByNameInSet enforces case-insensitive uniqueness within set`() {
            val rule = aRule(ruleSetId, "My Rule")
            adapter.saveRule(rule)
            assertThat(adapter.existsRuleByNameInSet(ruleSetId, "my rule")).isTrue()
            assertThat(adapter.existsRuleByNameInSet(ruleSetId, "MY RULE")).isTrue()
            assertThat(adapter.existsRuleByNameInSet(ruleSetId, "another rule")).isFalse()
        }

        @Test
        fun `existsRuleByNameInSet excludes specified rule from check`() {
            val rule = adapter.saveRule(aRule(ruleSetId, "My Rule"))
            // When updating, should not find its own name as a conflict
            assertThat(adapter.existsRuleByNameInSet(ruleSetId, "My Rule", excludeId = rule.id)).isFalse()
        }

        @Test
        fun `deleteRuleVersion removes version from DB`() {
            val rule = adapter.saveRule(aRule(ruleSetId))
            val discarded = rule.discardVersion(1)
            adapter.saveRule(discarded)

            adapter.deleteRuleVersion(rule.id, 1)

            val found = adapter.findRuleById(rule.id)!!
            assertThat(found.findVersion(1)).isNull()
        }

        @Test
        fun `findAllRules search filters by name trigram`() {
            adapter.saveRule(aRule(ruleSetId, "Loan Approval"))
            adapter.saveRule(aRule(ruleSetId, "Risk Assessment"))

            val result = adapter.findAllRules(null, 0, 10, "Loan", true)
            assertThat(result.content.map { it.name }).containsExactly("Loan Approval")
        }
    }
}
