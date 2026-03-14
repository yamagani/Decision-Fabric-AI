package com.decisionfabric.application.ports.out

/**
 * Outbound port for LLM provider integration.
 * Implemented by: LlmProviderAdapter (AWS Bedrock / OpenAI)
 * Protected by Resilience4j circuit breaker.
 */
interface LlmProviderPort {

    /**
     * Augments a DMN decision result with AI reasoning.
     *
     * @param context  The evaluation context including inputs and DMN result
     * @return [AiAugmentationResult] with outcome, reasoning, and updated confidence
     * @throws [com.decisionfabric.domain.shared.AiUnavailableException] when provider is unavailable or circuit breaker is open
     */
    fun augmentDecision(context: AiAugmentationContext): AiAugmentationResult
}

data class AiAugmentationContext(
    val inputData: Map<String, Any?>,
    val dmnOutputs: Map<String, Any?>,
    val dmnConfidenceScore: Double,
    val decisionName: String,
    val userId: String
)

data class AiAugmentationResult(
    val outcome: String,
    val reasoning: String,
    val confidenceScore: Double,
    val provider: String,
    val model: String
)
