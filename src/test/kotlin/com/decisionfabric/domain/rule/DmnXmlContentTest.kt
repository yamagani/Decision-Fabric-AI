package com.decisionfabric.domain.rule

import com.decisionfabric.domain.shared.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DmnXmlContentTest {

    @Test
    fun `constructor accepts valid DMN XML starting with angle bracket`() {
        val content = DmnXmlContent("<definitions/>")
        assertThat(content.value).isEqualTo("<definitions/>")
    }

    @Test
    fun `constructor rejects blank content`() {
        assertThatThrownBy {
            DmnXmlContent("   ")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
    }

    @Test
    fun `constructor rejects content not starting with angle bracket`() {
        assertThatThrownBy {
            DmnXmlContent("not xml")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("XML element")
    }

    @Test
    fun `value class equality is by value`() {
        val a = DmnXmlContent("<x/>")
        val b = DmnXmlContent("<x/>")
        assertThat(a).isEqualTo(b)
    }
}
