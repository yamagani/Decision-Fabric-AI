package com.decisionfabric.application.ports.`in`

import com.decisionfabric.application.ports.out.AiAugmentationResult
import com.decisionfabric.application.ports.out.DmnEvaluationResult
import java.time.Instant
import java.util.UUID

interface DecisionEvaluationUseCase {
    fun evaluate(request: EvaluateDecisionCommand): DecisionEvaluationResult
}

data class EvaluateDecisionCommand(
    val inputData: Map<String, Any?>,
    val ruleId: UUID?,
    val requestAiAugmentation: Boolean = false,
    val userId: String,
    val correlationId: String
)

data class DecisionEvaluationResult(
    val decisionId: UUID,
    val ruleId: UUID?,
    val outputs: Map<String, Any?>,
    val matchedRuleIds: List<String>,
    val confidenceScore: Double,
    val aiAugmented: Boolean,
    val aiResult: AiAugmentationResult?,
    val evaluatedAt: Instant
)
