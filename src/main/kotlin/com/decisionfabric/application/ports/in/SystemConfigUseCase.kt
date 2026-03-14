package com.decisionfabric.application.ports.`in`

import com.decisionfabric.application.ports.out.AiProviderConfig

interface SystemConfigUseCase {
    fun getAiConfig(): AiProviderConfig
    fun updateAiConfig(command: UpdateAiConfigCommand)
}

data class UpdateAiConfigCommand(
    val provider: String,
    val model: String,
    val endpoint: String?,
    val confidenceThreshold: Double,
    val maxTokens: Int,
    val timeoutSeconds: Int,
    val updatedBy: String
)
