package com.decisionfabric.application.ports.out

/**
 * Outbound port for validating DMN XML against OMG DMN 1.4 schema.
 * Implemented by: DroolsDmnSchemaValidatorAdapter
 */
interface DmnSchemaValidatorPort {

    /**
     * Validates the given DMN XML string against the OMG DMN 1.4 XSD schema.
     *
     * @param dmnXml  Raw DMN XML content
     * @return [ValidationResult] with isValid flag and any validation errors
     */
    fun validate(dmnXml: String): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
