package com.decisionfabric.application.rule

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class RuleAuditRetentionJob(
    private val ruleAuditPort: RuleAuditPort,
    @Value("\${rule.audit.retention-days:2555}") private val retentionDays: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Purge audit records older than the configured retention period. Runs daily at 02:00 server time. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    fun purgeOldAuditLogs() {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        val deleted = ruleAuditPort.purgeOlderThan(cutoff)
        log.info("RuleAuditRetentionJob: purged {} audit records older than {}", deleted, cutoff)
    }
}
