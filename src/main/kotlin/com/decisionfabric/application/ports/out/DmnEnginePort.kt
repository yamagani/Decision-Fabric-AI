package com.decisionfabric.application.ports.out

/**
 * Outbound port for DMN decision evaluation.
 * Implemented by: DroolsDmnEngineAdapter (primary), CamundaDmnEngineAdapter (future)
 */
interface DmnEnginePort {

    /**
     * Evaluates a DMN decision table against the given input data.
     *
     * @param dmnXml  Raw DMN 1.4 XML content
     * @param inputData  Key-value map of input variables
     * @return [DmnEvaluationResult] containing matched rules, outputs, and confidence score
     */
    fun evaluate(dmnXml: String, inputData: Map<String, Any?>): DmnEvaluationResult

    /**
     * Validates that the given DMN XML is well-formed and conforms to OMG DMN 1.4 schema.
     * Returns true if valid; throws [com.decisionfabric.domain.shared.DmnValidationException] if not.
     */
    fun validateSchema(dmnXml: String): Boolean
}

data class DmnEvaluationResult(
    val matchedRuleIds: List<String>,
    val outputs: Map<String, Any?>,
    val confidenceScore: Double,
    val hitPolicy: String,
    val decisionName: String
)
