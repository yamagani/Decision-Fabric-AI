package com.decisionfabric.adapter.outbound.dmn

import com.decisionfabric.application.ports.out.DmnSchemaValidatorPort
import com.decisionfabric.application.ports.out.ValidationResult
import org.kie.api.KieServices
import org.kie.api.builder.KieFileSystem
import org.kie.api.builder.Message
import org.kie.dmn.api.core.DMNMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.xml.sax.SAXException

/** Maximum DMN XML size accepted for parsing (1 MB). */
private const val MAX_DMN_BYTES = 1_048_576

/** Timeout for Drools validation (ms). */
private const val VALIDATION_TIMEOUT_MS = 1_000L

/**
 * Validates DMN XML using Drools KIE services.
 * Security hardened: XXE prevention, size guard, 1-second timeout.
 */
@Component
class DroolsDmnSchemaValidatorAdapter : DmnSchemaValidatorPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun validate(dmnXml: String): ValidationResult {
        val bytes = dmnXml.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_DMN_BYTES) {
            return ValidationResult(isValid = false, errors = listOf("DMN content exceeds 1 MB size limit"))
        }

        // XXE-safe well-formedness check first
        val wellFormednessCheck = checkWellFormed(dmnXml)
        if (!wellFormednessCheck.isValid) {
            return wellFormednessCheck
        }

        return try {
            CompletableFuture.supplyAsync { droolsValidate(dmnXml) }
                .get(VALIDATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.warn("DMN schema validation timed out after ${VALIDATION_TIMEOUT_MS}ms")
            ValidationResult(isValid = false, errors = listOf("DMN validation timed out"))
        } catch (e: Exception) {
            log.error("Unexpected error during DMN validation", e)
            ValidationResult(isValid = false, errors = listOf("DMN validation error: ${e.message}"))
        }
    }

    private fun checkWellFormed(dmnXml: String): ValidationResult {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // XXE prevention
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(dmnXml)))
            ValidationResult(isValid = true)
        } catch (e: SAXException) {
            ValidationResult(isValid = false, errors = listOf("XML is not well-formed: ${e.message}"))
        } catch (e: Exception) {
            ValidationResult(isValid = false, errors = listOf("XML parse error: ${e.message}"))
        }
    }

    private fun droolsValidate(dmnXml: String): ValidationResult {
        return try {
            val kieServices = KieServices.Factory.get()
            val kieFileSystem: KieFileSystem = kieServices.newKieFileSystem()
            kieFileSystem.write("src/main/resources/decision.dmn", dmnXml)

            val kieBuilder = kieServices.newKieBuilder(kieFileSystem)
            kieBuilder.buildAll()

            val results = kieBuilder.results
            val errors = results.getMessages(Message.Level.ERROR)
                .map { it.toString() }

            if (errors.isEmpty()) {
                ValidationResult(isValid = true)
            } else {
                ValidationResult(isValid = false, errors = errors)
            }
        } catch (e: Exception) {
            log.debug("Drools KIE validation threw exception — treating as validation failure", e)
            ValidationResult(isValid = false, errors = listOf("Drools validation failed: ${e.message}"))
        }
    }
}
