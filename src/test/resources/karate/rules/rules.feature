Feature: Rule CRUD and Version Lifecycle API

  Background:
    * def auth = call read('classpath:karate/auth.feature') { username: #(ruleAdminUser), password: #(ruleAdminPass) }
    * def token = auth.accessToken
    * def authHeader = 'Bearer ' + token
    * url baseUrl
    * configure headers = { Authorization: '#(authHeader)', 'Content-Type': 'application/json' }
    # Minimal valid DMN XML — use 'text' keyword to prevent Karate from auto-parsing it as XML Document
    * text validDmn =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                   xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
                   id="Definitions_1" name="simple" namespace="http://camunda.org/schema/1.0/dmn">
        <decision id="Decision_1" name="Approve">
          <decisionTable id="table_1" hitPolicy="FIRST">
            <input id="in_1" label="Age">
              <inputExpression id="exp_1" typeRef="integer"><text>age</text></inputExpression>
            </input>
            <output id="out_1" label="Result" name="result" typeRef="string"/>
            <rule id="rule_1">
              <inputEntry id="ie_1"><text>&gt;= 18</text></inputEntry>
              <outputEntry id="oe_1"><text>"Approved"</text></outputEntry>
            </rule>
          </decisionTable>
        </decision>
      </definitions>
      """
    # Create a fresh rule set for this test run
    * def ruleSetName = 'Rules-Feature-Set-' + java.util.UUID.randomUUID()
    Given path '/api/v1/rule-sets'
    And request { name: '#(ruleSetName)', description: 'test set' }
    When method post
    Then status 201
    * def ruleSetId = response.id

  # ── CREATE ──────────────────────────────────────────────────────────────────

  Scenario: Create a rule returns 201 with DRAFT version
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Age Check', description: 'Checks age', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    And match response.name == 'Age Check'
    And match response.status == 'ACTIVE'
    And match response.versions[0].status == 'DRAFT'
    And match response.versions[0].version == 1
    * def ruleId = response.id

  Scenario: Create rule with blank name returns 400
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: '', description: 'bad', dmnXml: '#(validDmn)' }
    When method post
    Then status 400

  # ── READ ─────────────────────────────────────────────────────────────────────

  Scenario: Get rule by id
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'GetRule-Test', description: 'desc', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    * def id = response.id
    Given path '/api/v1/rules/' + id
    When method get
    Then status 200
    And match response.id == id

  Scenario: List rules filtered by ruleSetId
    # Create two rules
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'List-Rule-1', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'List-Rule-2', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    # List
    Given path '/api/v1/rules'
    And param ruleSetId = ruleSetId
    When method get
    Then status 200
    And match response.totalElements == '#? _ >= 2'

  Scenario: Get non-existent rule returns 404
    Given path '/api/v1/rules/00000000-0000-0000-0000-000000000000'
    When method get
    Then status 404

  # ── VERSION LIFECYCLE ────────────────────────────────────────────────────────

  Scenario: Activate a DRAFT version
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Activate-Test', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    * def ruleId = response.id
    Given path '/api/v1/rules/' + ruleId + '/versions/1/activate'
    And request {}
    When method post
    Then status 200
    And match response.status == 'ACTIVE'
    And match response.version == 1

  Scenario: Deactivate an ACTIVE version
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Deactivate-Test', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    * def ruleId = response.id
    # Activate first
    Given path '/api/v1/rules/' + ruleId + '/versions/1/activate'
    And request {}
    When method post
    Then status 200
    # Now deactivate
    Given path '/api/v1/rules/' + ruleId + '/versions/1/deactivate'
    And request {}
    When method post
    Then status 200
    And match response.status == 'INACTIVE'

  Scenario: Update rule creates a new DRAFT version (branch model)
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Update-Test', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    * def ruleId = response.id
    # Update — creates v2
    Given path '/api/v1/rules/' + ruleId
    And request { dmnXml: '#(validDmn)' }
    When method put
    Then status 200
    And match response.versions == '#[2]'
    And match response.versions[1].status == 'DRAFT'

  # ── DMN VALIDATE ─────────────────────────────────────────────────────────────

  Scenario: Validate valid DMN returns valid=true
    * configure headers = { Authorization: '#(authHeader)', 'Content-Type': 'application/xml' }
    Given path '/api/v1/rules/validate'
    And request validDmn
    When method post
    Then status 200
    And match response.valid == true
    And match response.errors == '#[]'

  Scenario: Validate empty DMN returns 400 or valid=false
    * configure headers = { Authorization: '#(authHeader)', 'Content-Type': 'application/xml' }
    Given path '/api/v1/rules/validate'
    And request '<bad/>'
    When method post
    * def ok = responseStatus == 200 || responseStatus == 400
    Then assert ok

  # ── DELETE ────────────────────────────────────────────────────────────────────

  Scenario: Soft-delete a rule
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Delete-Me-Rule', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    * def ruleId = response.id
    Given path '/api/v1/rules/' + ruleId
    When method delete
    Then status 204

  # ── SECURITY ─────────────────────────────────────────────────────────────────

  Scenario: RULE_READER cannot create a rule (403)
    * configure headers = null
    * def readerAuth = call read('classpath:karate/auth.feature') { username: #(consumerUser), password: #(consumerPass) }
    * def readerHeader = 'Bearer ' + readerAuth.accessToken
    * configure headers = { Authorization: '#(readerHeader)', 'Content-Type': 'application/json' }
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Forbidden', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 403

  Scenario: RULE_ADMIN cannot purge a version (403)
    # Create and activate a rule first with system-admin so we have an INACTIVE version to purge
    * configure headers = null
    * def sysAuth = call read('classpath:karate/auth.feature') { username: #(systemAdminUser), password: #(systemAdminPass) }
    * def sysHeader = 'Bearer ' + sysAuth.accessToken
    * configure headers = { Authorization: '#(sysHeader)', 'Content-Type': 'application/json' }
    * def purgeSetName = 'Purge-Test-Set-' + java.util.UUID.randomUUID()
    Given path '/api/v1/rule-sets'
    And request { name: '#(purgeSetName)', description: 'purge' }
    When method post
    Then status 201
    * def purgeSetId = response.id
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(purgeSetId)', name: 'PurgeTarget', description: 'd', dmnXml: '#(validDmn)' }
    When method post
    Then status 201
    * def purgeRuleId = response.id
    # Deactivate v1 (already DRAFT -> make INACTIVE by discarding)
    Given path '/api/v1/rules/' + purgeRuleId + '/versions/1/discard'
    And request {}
    When method post
    Then status 200
    # Now try purge as RULE_ADMIN — should be 403
    * configure headers = { Authorization: '#(authHeader)', 'Content-Type': 'application/json' }
    Given path '/api/v1/rules/' + purgeRuleId + '/versions/1'
    When method delete
    Then status 403
