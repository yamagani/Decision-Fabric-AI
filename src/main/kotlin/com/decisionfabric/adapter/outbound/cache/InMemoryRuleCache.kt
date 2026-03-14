package com.decisionfabric.adapter.outbound.cache

import com.decisionfabric.application.ports.out.RuleRepositoryPort
import com.decisionfabric.domain.rule.RuleVersionStatus
import com.decisionfabric.domain.rule.event.RuleLifecycleEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory cache for active rule versions.
 * Uses ConcurrentHashMap with byte-cap eviction (oldest activatedAt first).
 * Consistency model: eventual — cache updated after commit via @TransactionalEventListener(AFTER_COMMIT).
 */
@Component
class InMemoryRuleCache(
    private val ruleRepositoryPort: RuleRepositoryPort,
    @Value("\${rule.cache.max-bytes-mb:200}") maxBytesMb: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxBytes = maxBytesMb * 1_048_576L

    // key: ruleId -> list of active versions for that rule
    private val cache: ConcurrentHashMap<UUID, List<CachedRuleVersion>> = ConcurrentHashMap()
    private val currentBytes = AtomicLong(0)

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    /** Returns all active versions for the given rule, or empty list if not cached. */
    fun getActiveVersions(ruleId: UUID): List<CachedRuleVersion> =
        cache[ruleId] ?: emptyList()

    /** Returns a flat list of all cached active rule versions across all rules. */
    fun getAllActiveEntries(): List<CachedRuleVersion> =
        cache.values.flatten()

    fun getCurrentBytes(): Long = currentBytes.get()

    fun size(): Int = cache.size

    // -------------------------------------------------------------------------
    // Warm-up
    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent::class)
    fun warmUp() {
        log.info("InMemoryRuleCache: starting warm-up")
        val activeRules = ruleRepositoryPort.findAllActiveRules()
        var loaded = 0
        for (rule in activeRules) {
            val activeVersions = rule.versions
                .filter { it.status == RuleVersionStatus.ACTIVE }
                .map { v ->
                    CachedRuleVersion(
                        ruleId = rule.id.value,
                        version = v.version,
                        dmnXml = v.dmnXml.value,
                        activatedAt = v.activatedAt ?: rule.createdAt
                    )
                }
            if (activeVersions.isNotEmpty()) {
                putIfCapacity(rule.id.value, activeVersions)
                loaded++
            }
        }
        log.info("InMemoryRuleCache: warm-up complete — $loaded rules cached, ${currentBytes.get()} bytes")
    }

    // -------------------------------------------------------------------------
    // Event Listeners
    // -------------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onVersionActivated(event: RuleLifecycleEvent.RuleVersionActivated) {
        refreshRule(event.ruleId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onVersionDeactivated(event: RuleLifecycleEvent.RuleVersionDeactivated) {
        refreshRule(event.ruleId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRuleDeleted(event: RuleLifecycleEvent.RuleDeleted) {
        evict(event.ruleId.value)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRuleCreated(event: RuleLifecycleEvent.RuleCreated) {
        // new rules have only a DRAFT version — nothing to cache yet
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun refreshRule(ruleId: com.decisionfabric.domain.rule.RuleId) {
        val rule = ruleRepositoryPort.findRuleById(ruleId)
        if (rule == null) {
            evict(ruleId.value)
            return
        }
        val activeVersions = rule.versions
            .filter { it.status == RuleVersionStatus.ACTIVE }
            .map { v ->
                CachedRuleVersion(
                    ruleId = ruleId.value,
                    version = v.version,
                    dmnXml = v.dmnXml.value,
                    activatedAt = v.activatedAt ?: rule.createdAt
                )
            }
        if (activeVersions.isEmpty()) {
            evict(ruleId.value)
        } else {
            putIfCapacity(ruleId.value, activeVersions)
        }
    }

    private fun evict(ruleId: UUID) {
        cache.compute(ruleId) { _, existing ->
            if (existing != null) {
                val freed = existing.sumOf { it.estimatedBytes }
                currentBytes.addAndGet(-freed)
            }
            null
        }
    }

    private fun putIfCapacity(ruleId: UUID, versions: List<CachedRuleVersion>) {
        cache.compute(ruleId) { _, existing ->
            // Free up bytes from old versions
            if (existing != null) {
                currentBytes.addAndGet(-existing.sumOf { it.estimatedBytes })
            }
            val newBytes = versions.sumOf { it.estimatedBytes }
            val projected = currentBytes.get() + newBytes
            if (projected > maxBytes) {
                evictOldest(newBytes)
            }
            currentBytes.addAndGet(newBytes)
            versions
        }
    }

    /** Evict entries (oldest activatedAt first) until we free at least `needed` bytes. */
    private fun evictOldest(needed: Long) {
        val candidates = cache.entries
            .sortedBy { (_, versions) -> versions.minOf { it.activatedAt } }
            .iterator()
        var freed = 0L
        while (freed < needed && candidates.hasNext()) {
            val entry = candidates.next()
            cache.compute(entry.key) { _, existing ->
                if (existing != null) {
                    val bytes = existing.sumOf { it.estimatedBytes }
                    freed += bytes
                    currentBytes.addAndGet(-bytes)
                }
                null
            }
        }
        if (freed < needed) {
            log.warn("InMemoryRuleCache: could not free enough bytes — needed=$needed freed=$freed")
        }
    }
}
