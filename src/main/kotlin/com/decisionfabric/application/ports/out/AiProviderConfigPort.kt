package com.decisionfabric.application.ports.out

/**
 * Outbound port for reading and writing AI provider configuration.
 * Implemented by: AwsParameterStoreConfigAdapter (SSM Parameter Store + Secrets Manager)
 */
interface AiProviderConfigPort {

    /** Returns the current active AI provider configuration. */
    fun getConfig(): AiProviderConfig

    /** Persists updated configuration. Triggers [AiProviderConfigChangedEvent] on success. */
    fun updateConfig(config: AiProviderConfig)
}

data class AiProviderConfig(
    val provider: String,          // "bedrock" | "openai"
    val model: String,
    val endpoint: String?,
    val confidenceThreshold: Double,
    val maxTokens: Int,
    val timeoutSeconds: Int
)
