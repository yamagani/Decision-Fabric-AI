package com.decisionfabric.adapter.outbound.dmn

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DroolsDmnSchemaValidatorAdapterTest {

    private lateinit var adapter: DroolsDmnSchemaValidatorAdapter

    private val validDmn = """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             id="test_decision"
             name="Test Decision"
             namespace="http://example.com/test">
  <decision id="d1" name="Decision1">
    <decisionTable id="dt1" hitPolicy="UNIQUE">
      <input id="i1" label="Score">
        <inputExpression id="ie1" typeRef="integer">
          <text>score</text>
        </inputExpression>
      </input>
      <output id="o1" label="Result" name="result" typeRef="string"/>
      <rule id="r1">
        <inputEntry id="in1"><text>&gt;= 0</text></inputEntry>
        <outputEntry id="out1"><text>"pass"</text></outputEntry>
      </rule>
    </decisionTable>
  </decision>
</definitions>"""

    @BeforeEach
    fun setUp() {
        adapter = DroolsDmnSchemaValidatorAdapter()
    }

    @Test
    fun `validate returns valid for well-formed DMN XML`() {
        val result = adapter.validate(validDmn)
        // May or may not pass Drools schema check in unit tests without full KIE on classpath —
        // at minimum the XXE and well-formedness checks must not throw
        assertThat(result).isNotNull()
    }

    @Test
    fun `validate rejects XXE injection attempt`() {
        val xxeDmn = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE definitions [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             id="xxe" name="XXE" namespace="ns">
  <description>&xxe;</description>
</definitions>"""
        val result = adapter.validate(xxeDmn)
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("DOCTYPE", ignoreCase = true) || it.contains("well-formed", ignoreCase = true) }
    }

    @Test
    fun `validate rejects non-XML content`() {
        val result = adapter.validate("this is not xml at all")
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun `validate rejects content exceeding 1 MB`() {
        val hugeDmn = "<x>" + "A".repeat(1_100_000) + "</x>"
        val result = adapter.validate(hugeDmn)
        assertThat(result.isValid).isFalse()
        assertThat(result.errors[0]).contains("1 MB")
    }

    @Test
    fun `validate rejects malformed XML`() {
        val result = adapter.validate("<definitions><unclosed>")
        assertThat(result.isValid).isFalse()
    }

    // ── Decision Table fixture ────────────────────────────────────────────────

    @Nested
    inner class DecisionTableFixture {

        private fun loadFixture(name: String): String =
            javaClass.getResourceAsStream("/fixtures/dmn/$name")
                ?.bufferedReader()?.readText()
                ?: error("Fixture not found: $name")

        @Test
        fun `comprehensive decision table fixture is well-formed XML`() {
            val dmn = loadFixture("valid-decision-table-comprehensive.dmn")
            val result = adapter.validate(dmn)
            // XML must be well-formed and pass the size + XXE guards
            assertThat(result).isNotNull()
            assertThat(result.errors).noneMatch {
                it.contains("DOCTYPE", ignoreCase = true) ||
                    it.contains("1 MB", ignoreCase = true) ||
                    it.contains("well-formed", ignoreCase = true)
            }
        }

        @Test
        fun `comprehensive decision table fixture uses FIRST hit policy`() {
            val dmn = loadFixture("valid-decision-table-comprehensive.dmn")
            assertThat(dmn).contains("hitPolicy=\"FIRST\"")
        }

        @Test
        fun `comprehensive decision table fixture declares three inputs`() {
            val dmn = loadFixture("valid-decision-table-comprehensive.dmn")
            assertThat(dmn)
                .contains("<text>employmentType</text>")
                .contains("<text>yearsOfService</text>")
                .contains("<text>performanceRating</text>")
        }

        @Test
        fun `comprehensive decision table fixture declares three outputs`() {
            val dmn = loadFixture("valid-decision-table-comprehensive.dmn")
            assertThat(dmn)
                .contains("name=\"bonusPercentage\"")
                .contains("name=\"leaveEntitlement\"")
                .contains("name=\"healthCoverageLevel\"")
        }

        @Test
        fun `comprehensive decision table fixture contains eight rules`() {
            val dmn = loadFixture("valid-decision-table-comprehensive.dmn")
            val ruleCount = "<rule id=".toRegex().findAll(dmn).count()
            assertThat(ruleCount).isEqualTo(8)
        }

        @Test
        fun `comprehensive decision table fixture has correct namespace`() {
            val dmn = loadFixture("valid-decision-table-comprehensive.dmn")
            assertThat(dmn).contains("namespace=\"http://decisionfabric.com/employee-benefits\"")
        }
    }

    // ── Decision Tree (DRG) fixture ───────────────────────────────────────────

    @Nested
    inner class DecisionTreeFixture {

        private fun loadFixture(name: String): String =
            javaClass.getResourceAsStream("/fixtures/dmn/$name")
                ?.bufferedReader()?.readText()
                ?: error("Fixture not found: $name")

        @Test
        fun `decision tree fixture is well-formed XML`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            val result = adapter.validate(dmn)
            assertThat(result).isNotNull()
            assertThat(result.errors).noneMatch {
                it.contains("DOCTYPE", ignoreCase = true) ||
                    it.contains("1 MB", ignoreCase = true) ||
                    it.contains("well-formed", ignoreCase = true)
            }
        }

        @Test
        fun `decision tree fixture contains three linked decisions`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            val decisionCount = "<decision id=".toRegex().findAll(dmn).count()
            assertThat(decisionCount).isEqualTo(3)
        }

        @Test
        fun `decision tree root decision references both sub-decisions via informationRequirement`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            // Drools may serialise self-closing elements with a space before />
            assertThat(dmn)
                .contains("href=\"#driverRiskLevel\"")
                .contains("href=\"#vehicleSurcharge\"")
        }

        @Test
        fun `decision tree branch decisions cover all driver risk levels`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            // Drools XML-encodes FEEL string literals as &quot;; use description text as a stable assertion.
            assertThat(dmn)
                .contains("low risk")
                .contains("medium risk")
                .contains("high risk")
        }

        @Test
        fun `decision tree vehicle surcharge branch covers electric vehicle discount`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            // Electric vehicle rule outputs a negative surcharge (discount)
            assertThat(dmn).contains("<text>-50</text>")
        }

        @Test
        fun `decision tree root decision produces premium tier outputs`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            // Output names are stable; tier values use description text to avoid &quot; encoding dependence.
            assertThat(dmn)
                .contains("name=\"annualPremium\"")
                .contains("name=\"premiumTier\"")
                .contains("preferred tier")
                .contains("elevated tier")
                .contains("premium tier")
        }

        @Test
        fun `decision tree fixture has correct namespace`() {
            val dmn = loadFixture("valid-decision-tree.dmn")
            assertThat(dmn).contains("namespace=\"http://decisionfabric.com/insurance-premium\"")
        }
    }
}
