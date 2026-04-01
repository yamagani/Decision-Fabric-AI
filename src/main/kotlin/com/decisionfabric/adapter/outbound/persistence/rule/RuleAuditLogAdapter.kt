package com.decisionfabric.adapter.outbound.persistence.rule

import com.decisionfabric.application.rule.RuleAuditPort
import com.decisionfabric.application.rule.RuleAuditRecord
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class RuleAuditLogAdapter(
    private val jdbcTemplate: JdbcTemplate
) : RuleAuditPort {

    override fun append(record: RuleAuditRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO rule_audit_log
              (id, entity_id, entity_type, action, performed_by, correlation_id, detail, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            record.entityId,
            record.entityType,
            record.action.name,
            record.performedBy,
            record.correlationId,
            record.detail,
            Timestamp.from(record.occurredAt)
        )
    }

    override fun purgeOlderThan(cutoff: Instant): Int =
        jdbcTemplate.update(
            "DELETE FROM rule_audit_log WHERE occurred_at < ?",
            Timestamp.from(cutoff)
        )
}
