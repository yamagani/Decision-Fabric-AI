package com.decisionfabric.application.rule

import com.decisionfabric.application.ports.out.DmnSchemaValidatorPort
import com.decisionfabric.application.ports.out.PagedResult
import com.decisionfabric.application.ports.out.RuleRepositoryPort
import com.decisionfabric.application.ports.out.ValidationResult
import com.decisionfabric.application.rule.command.ActivateVersionCommand
import com.decisionfabric.application.rule.command.CreateRuleCommand
import com.decisionfabric.application.rule.command.CreateRuleSetCommand
import com.decisionfabric.application.rule.command.DeleteRuleCommand
import com.decisionfabric.application.rule.command.DeleteRuleSetCommand
import com.decisionfabric.application.rule.command.DeactivateVersionCommand
import com.decisionfabric.application.rule.command.PurgeVersionCommand
import com.decisionfabric.domain.rule.DmnXmlContent
import com.decisionfabric.domain.rule.Rule
import com.decisionfabric.domain.rule.RuleId
import com.decisionfabric.domain.rule.RuleSet
import com.decisionfabric.domain.rule.RuleSetId
import com.decisionfabric.domain.rule.RuleSetStatus
import com.decisionfabric.domain.rule.RuleStatus
import com.decisionfabric.domain.rule.RuleVersionStatus
import com.decisionfabric.domain.shared.BusinessRuleViolationException
import com.decisionfabric.domain.shared.ConflictException
import com.decisionfabric.domain.shared.DmnValidationException
import com.decisionfabric.domain.shared.EntityNotFoundException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class RuleManagementServiceTest {

    private val ruleRepo: RuleRepositoryPort = mockk()
    private val validator: DmnSchemaValidatorPort = mockk()
    private val auditPort: RuleAuditPort = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    private val service = RuleManagementService(ruleRepo, validator, auditPort, eventPublisher, RuleValidationProperties())

    private val validDmn = "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\" id=\"d\" name=\"d\" namespace=\"ns\"/>"
    private val ruleSetId = RuleSetId.generate()

    private fun aRuleSet(id: RuleSetId = ruleSetId) = RuleSet.create(id, "Test Set", "desc", "user1")

    private fun aRule(ruleId: RuleId = RuleId.generate(), ruleSetId: RuleSetId = this.ruleSetId) =
        Rule.create(ruleId, ruleSetId, "Test Rule", "desc", DmnXmlContent(validDmn), "user1")

    @Nested
    inner class CreateRuleSet {
        private val command = CreateRuleSetCommand("New Set", "desc", "user1", "corr-1")

        @Test
        fun `createRuleSet saves and returns view`() {
            every { ruleRepo.existsRuleSetByName("New Set") } returns false
            val saved = aRuleSet()
            every { ruleRepo.saveRuleSet(any()) } returns saved
            every { ruleRepo.countRulesInSet(any()) } returns 0
            every { ruleRepo.countActiveRulesInSet(any()) } returns 0

            val result = service.createRuleSet(command)

            assertThat(result.name).isEqualTo("Test Set")
            verify { ruleRepo.saveRuleSet(any()) }
        }

        @Test
        fun `createRuleSet throws ConflictException when name already exists`() {
            every { ruleRepo.existsRuleSetByName("New Set") } returns true

            assertThatThrownBy { service.createRuleSet(command) }
                .isInstanceOf(ConflictException::class.java)
        }
    }

    @Nested
    inner class CreateRule {
        private val command = CreateRuleCommand(ruleSetId, "My Rule", "desc", validDmn, "user1", "corr-1")

        @Test
        fun `createRule validates DMN and saves rule`() {
            every { ruleRepo.findRuleSetById(ruleSetId) } returns aRuleSet()
            every { ruleRepo.existsRuleByNameInSet(ruleSetId, "My Rule") } returns false
            every { validator.validate(validDmn) } returns ValidationResult(isValid = true)
            val savedRule = aRule()
            every { ruleRepo.saveRule(any()) } returns savedRule
            every { ruleRepo.findRuleSetById(savedRule.ruleSetId) } returns aRuleSet()

            val result = service.createRule(command)

            assertThat(result.name).isEqualTo("Test Rule")
            verify { ruleRepo.saveRule(any()) }
        }

        @Test
        fun `createRule throws DmnValidationException when DMN is invalid`() {
            every { ruleRepo.findRuleSetById(ruleSetId) } returns aRuleSet()
            every { ruleRepo.existsRuleByNameInSet(ruleSetId, "My Rule") } returns false
            every { validator.validate(validDmn) } returns ValidationResult(
                isValid = false, errors = listOf("Invalid namespace")
            )

            assertThatThrownBy { service.createRule(command) }
                .isInstanceOf(DmnValidationException::class.java)
                .hasMessageContaining("Invalid namespace")
        }

        @Test
        fun `createRule throws ConflictException when name already exists in rule set`() {
            every { ruleRepo.findRuleSetById(ruleSetId) } returns aRuleSet()
            every { ruleRepo.existsRuleByNameInSet(ruleSetId, "My Rule") } returns true

            assertThatThrownBy { service.createRule(command) }
                .isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `createRule throws EntityNotFoundException when rule set not found`() {
            every { ruleRepo.findRuleSetById(ruleSetId) } returns null

            assertThatThrownBy { service.createRule(command) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class ActivateVersion {
        @Test
        fun `activateVersion saves updated rule`() {
            val rule = aRule()
            val ruleId = rule.id
            val command = ActivateVersionCommand(ruleId, 1, "user1", "corr-1")

            every { ruleRepo.findRuleByIdForUpdate(ruleId) } returns rule
            val activated = rule.activateVersion(1, "user1")
            every { ruleRepo.saveRule(any()) } returns activated

            val result = service.activateVersion(command)

            assertThat(result.status).isEqualTo(RuleVersionStatus.ACTIVE)
        }

        @Test
        fun `activateVersion throws EntityNotFoundException when rule not found`() {
            val ruleId = RuleId.generate()
            val command = ActivateVersionCommand(ruleId, 1, "user1", "corr-1")

            every { ruleRepo.findRuleByIdForUpdate(ruleId) } returns null

            assertThatThrownBy { service.activateVersion(command) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class DeleteRule {
        @Test
        fun `deleteRule soft deletes rule`() {
            val rule = aRule()
            val ruleId = rule.id
            val command = DeleteRuleCommand(ruleId, "user1", "corr-1")

            every { ruleRepo.findRuleByIdForUpdate(ruleId) } returns rule
            every { ruleRepo.saveRule(any()) } answers { firstArg() }

            service.deleteRule(command)

            verify { ruleRepo.saveRule(match { it.status == RuleStatus.INACTIVE }) }
        }
    }

    @Nested
    inner class PurgeVersion {
        @Test
        fun `purgeVersion throws when version is not INACTIVE`() {
            val rule = aRule()
            val ruleId = rule.id
            val command = PurgeVersionCommand(ruleId, 1, "admin", "corr-1")

            every { ruleRepo.findRuleByIdForUpdate(ruleId) } returns rule

            // version 1 is DRAFT, not INACTIVE
            assertThatThrownBy { service.purgeVersion(command) }
                .isInstanceOf(BusinessRuleViolationException::class.java)
        }

        @Test
        fun `purgeVersion deletes INACTIVE version`() {
            val rule = aRule()
            val discardedRule = rule.discardVersion(1)
            val ruleId = rule.id
            val command = PurgeVersionCommand(ruleId, 1, "admin", "corr-1")

            every { ruleRepo.findRuleByIdForUpdate(ruleId) } returns discardedRule
            every { ruleRepo.deleteRuleVersion(ruleId, 1) } just runs

            service.purgeVersion(command)

            verify { ruleRepo.deleteRuleVersion(ruleId, 1) }
        }
    }

    @Nested
    inner class DeleteRuleSet {
        @Test
        fun `deleteRuleSet throws when active rules exist`() {
            val command = DeleteRuleSetCommand(ruleSetId, "user1", "corr-1")

            every { ruleRepo.findRuleSetByIdForUpdate(ruleSetId) } returns aRuleSet()
            every { ruleRepo.countActiveRulesInSet(ruleSetId) } returns 3

            assertThatThrownBy { service.deleteRuleSet(command) }
                .isInstanceOf(BusinessRuleViolationException::class.java)
                .hasMessageContaining("active rules")
        }

        @Test
        fun `deleteRuleSet deactivates rule set when no active rules`() {
            val command = DeleteRuleSetCommand(ruleSetId, "user1", "corr-1")

            every { ruleRepo.findRuleSetByIdForUpdate(ruleSetId) } returns aRuleSet()
            every { ruleRepo.countActiveRulesInSet(ruleSetId) } returns 0
            every { ruleRepo.saveRuleSet(any()) } answers { firstArg() }

            service.deleteRuleSet(command)

            verify { ruleRepo.saveRuleSet(match { it.status == RuleSetStatus.INACTIVE }) }
        }
    }
}
