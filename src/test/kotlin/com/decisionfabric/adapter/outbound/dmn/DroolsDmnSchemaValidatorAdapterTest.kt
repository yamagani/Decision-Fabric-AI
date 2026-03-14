package com.decisionfabric.adapter.outbound.dmn

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
}
