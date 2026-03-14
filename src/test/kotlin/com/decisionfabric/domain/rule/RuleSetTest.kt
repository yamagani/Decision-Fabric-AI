package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.BusinessRuleViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RuleSetTest {

    @Test
    fun `create initialises rule set with ACTIVE status`() {
        val ruleSet = RuleSet.create(
            id = RuleSetId.generate(),
            name = "My Rule Set",
            description = "A test rule set",
            createdBy = "user1"
        )
        assertThat(ruleSet.status).isEqualTo(RuleSetStatus.ACTIVE)
        assertThat(ruleSet.name).isEqualTo("My Rule Set")
    }

    @Test
    fun `create trims name`() {
        val ruleSet = RuleSet.create(
            id = RuleSetId.generate(),
            name = "  Padded  ",
            description = "",
            createdBy = "u"
        )
        assertThat(ruleSet.name).isEqualTo("Padded")
    }

    @Test
    fun `create rejects blank name`() {
        assertThatThrownBy {
            RuleSet.create(
                id = RuleSetId.generate(),
                name = "   ",
                description = "",
                createdBy = "u"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `create rejects name longer than 255 characters`() {
        assertThatThrownBy {
            RuleSet.create(
                id = RuleSetId.generate(),
                name = "A".repeat(256),
                description = "",
                createdBy = "u"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `deactivate sets status to INACTIVE`() {
        val ruleSet = RuleSet.create(
            id = RuleSetId.generate(),
            name = "Test",
            description = "",
            createdBy = "u"
        )
        val deactivated = ruleSet.deactivate()
        assertThat(deactivated.status).isEqualTo(RuleSetStatus.INACTIVE)
    }

    @Test
    fun `deactivate is idempotent`() {
        val ruleSet = RuleSet.create(
            id = RuleSetId.generate(),
            name = "Test",
            description = "",
            createdBy = "u"
        ).deactivate()
        val again = ruleSet.deactivate()
        assertThat(again).isEqualTo(ruleSet)
    }
}
