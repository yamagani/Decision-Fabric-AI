-- V3: Make the rules → rule_sets foreign key explicitly RESTRICT on delete.
-- PostgreSQL's default (NO ACTION) is equivalent in practice, but making it
-- explicit provides a DB-level safety net for the application-level check in
-- RuleManagementService.deleteRuleSet().

ALTER TABLE rules DROP CONSTRAINT fk_rules_rule_set;

ALTER TABLE rules
    ADD CONSTRAINT fk_rules_rule_set
        FOREIGN KEY (rule_set_id)
        REFERENCES rule_sets(id)
        ON DELETE RESTRICT;
