package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.BusinessRuleViolationException
import com.decisionfabric.domain.shared.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RuleTest {

    private lateinit var rule: Rule

    @BeforeEach
    fun setUp() {
        rule = Rule.create(
            id = RuleId.generate(),
            ruleSetId = RuleSetId.generate(),
            name = "Test Rule",
            description = "Test description",
            dmnXml = DmnXmlContent("<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" id=\"d\" name=\"d\" namespace=\"ns\"/>"),
            createdBy = "user1"
        )
    }

    @Nested
    inner class Create {
        @Test
        fun `create initialises rule with ACTIVE status and single DRAFT version`() {
            assertThat(rule.status).isEqualTo(RuleStatus.ACTIVE)
            assertThat(rule.versions).hasSize(1)
            assertThat(rule.versions[0].status).isEqualTo(RuleVersionStatus.DRAFT)
            assertThat(rule.versions[0].version).isEqualTo(1)
        }

        @Test
        fun `create trims name`() {
            val r = Rule.create(
                id = RuleId.generate(),
                ruleSetId = RuleSetId.generate(),
                name = "  My Rule  ",
                description = "",
                dmnXml = DmnXmlContent("<x/>"),
                createdBy = "u"
            )
            assertThat(r.name).isEqualTo("My Rule")
        }

        @Test
        fun `create rejects blank name`() {
            assertThatThrownBy {
                Rule.create(
                    id = RuleId.generate(),
                    ruleSetId = RuleSetId.generate(),
                    name = "   ",
                    description = "",
                    dmnXml = DmnXmlContent("<x/>"),
                    createdBy = "u"
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class AddVersion {
        @Test
        fun `addVersion increments version number and creates DRAFT`() {
            val updated = rule.addVersion(DmnXmlContent("<x/>"), "user2")
            assertThat(updated.versions).hasSize(2)
            assertThat(updated.versions[1].version).isEqualTo(2)
            assertThat(updated.versions[1].status).isEqualTo(RuleVersionStatus.DRAFT)
        }

        @Test
        fun `addVersion on INACTIVE rule throws BusinessRuleViolationException`() {
            val inactive = rule.softDelete()
            assertThatThrownBy {
                inactive.addVersion(DmnXmlContent("<x/>"), "user2")
            }.isInstanceOf(BusinessRuleViolationException::class.java)
        }
    }

    @Nested
    inner class ActivateVersion {
        @Test
        fun `activateVersion transitions DRAFT to ACTIVE`() {
            val updated = rule.activateVersion(1, "user1")
            assertThat(updated.findVersion(1)!!.status).isEqualTo(RuleVersionStatus.ACTIVE)
            assertThat(updated.findVersion(1)!!.activatedBy).isEqualTo("user1")
            assertThat(updated.findVersion(1)!!.activatedAt).isNotNull()
        }

        @Test
        fun `activateVersion is idempotent when already ACTIVE`() {
            val activated = rule.activateVersion(1, "user1")
            val again = activated.activateVersion(1, "user1")
            assertThat(again).isEqualTo(activated)
        }

        @Test
        fun `activateVersion throws when version not found`() {
            assertThatThrownBy {
                rule.activateVersion(99, "user1")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `activateVersion throws on INACTIVE rule`() {
            val inactive = rule.softDelete()
            assertThatThrownBy {
                inactive.activateVersion(1, "user1")
            }.isInstanceOf(BusinessRuleViolationException::class.java)
        }
    }

    @Nested
    inner class DeactivateVersion {
        @Test
        fun `deactivateVersion transitions ACTIVE to INACTIVE`() {
            val activated = rule.activateVersion(1, "user1")
            val deactivated = activated.deactivateVersion(1)
            assertThat(deactivated.findVersion(1)!!.status).isEqualTo(RuleVersionStatus.INACTIVE)
        }

        @Test
        fun `deactivateVersion throws when not ACTIVE`() {
            // version is DRAFT, not ACTIVE
            assertThatThrownBy {
                rule.deactivateVersion(1)
            }.isInstanceOf(BusinessRuleViolationException::class.java)
        }

        @Test
        fun `deactivateVersion throws when already INACTIVE`() {
            val activated = rule.activateVersion(1, "user1")
            val deactivated = activated.deactivateVersion(1)
            assertThatThrownBy {
                deactivated.deactivateVersion(1)
            }.isInstanceOf(BusinessRuleViolationException::class.java)
        }
    }

    @Nested
    inner class DiscardVersion {
        @Test
        fun `discardVersion transitions DRAFT to INACTIVE`() {
            val discarded = rule.discardVersion(1)
            assertThat(discarded.findVersion(1)!!.status).isEqualTo(RuleVersionStatus.INACTIVE)
        }

        @Test
        fun `discardVersion throws when version is ACTIVE`() {
            val activated = rule.activateVersion(1, "user1")
            assertThatThrownBy {
                activated.discardVersion(1)
            }.isInstanceOf(BusinessRuleViolationException::class.java)
        }

        @Test
        fun `discardVersion throws when version not found`() {
            assertThatThrownBy {
                rule.discardVersion(99)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class SoftDelete {
        @Test
        fun `softDelete sets rule INACTIVE and cascades versions`() {
            val withActive = rule.addVersion(DmnXmlContent("<x/>"), "u").let {
                it.activateVersion(1, "u")
            }
            val deleted = withActive.softDelete()
            assertThat(deleted.status).isEqualTo(RuleStatus.INACTIVE)
            deleted.versions.forEach { v ->
                assertThat(v.status).isEqualTo(RuleVersionStatus.INACTIVE)
            }
        }
    }

    @Nested
    inner class Queries {
        @Test
        fun `activeVersions returns only ACTIVE versions`() {
            val withTwoVersions = rule.addVersion(DmnXmlContent("<x/>"), "u")
            val withActivated = withTwoVersions.activateVersion(1, "u")
            assertThat(withActivated.activeVersions()).hasSize(1)
            assertThat(withActivated.activeVersions()[0].version).isEqualTo(1)
        }

        @Test
        fun `latestVersion returns highest version number`() {
            val v2 = rule.addVersion(DmnXmlContent("<x/>"), "u")
            assertThat(v2.latestVersion()?.version).isEqualTo(2)
        }

        @Test
        fun `nextVersionNumber is latest + 1`() {
            val v2 = rule.addVersion(DmnXmlContent("<x/>"), "u")
            assertThat(v2.nextVersionNumber()).isEqualTo(3)
        }
    }
}
