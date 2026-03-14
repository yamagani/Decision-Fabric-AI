Feature: DMN Decision Table and Decision Tree API Examples
  # Tests that the Rule Management API accepts and stores:
  #   1. A comprehensive decision table (FIRST hit-policy, 3 inputs / 3 outputs / 8 rules)
  #   2. A decision tree modelled as a DMN Decision Requirements Graph (DRG) with 3 linked decisions
  #
  # Run against a live local stack:
  #   docker compose up -d
  #   ./gradlew karateTest

  Background:
    * def auth = call read('classpath:karate/auth.feature') { username: #(ruleAdminUser), password: #(ruleAdminPass) }
    * def token = auth.accessToken
    * url baseUrl
    * configure headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }

    # Load the DMN fixture files from the test-resources directory.
    # karate.readAsString() returns the raw text without any XML parsing.
    * def decisionTableDmn  = karate.readAsString('classpath:fixtures/dmn/valid-decision-table-comprehensive.dmn')
    * def decisionTreeDmn   = karate.readAsString('classpath:fixtures/dmn/valid-decision-tree.dmn')

    # Create a dedicated rule set for this feature run
    * def ruleSetName = 'DMN-Examples-' + java.util.UUID.randomUUID()
    Given path '/api/v1/rule-sets'
    And request { name: '#(ruleSetName)', description: 'DMN decision table and tree examples' }
    When method post
    Then status 201
    * def ruleSetId = response.id

  # ── Decision Table ───────────────────────────────────────────────────────────

  Scenario: Create a rule using a comprehensive decision table DMN returns 201
    Given path '/api/v1/rules'
    And request
      """
      {
        "ruleSetId":   "#(ruleSetId)",
        "name":        "Employee Benefits Eligibility",
        "description": "FIRST-hit-policy table: employment type, years of service and performance rating → bonus, leave and health cover",
        "dmnXml":      "#(decisionTableDmn)"
      }
      """
    When method post
    Then status 201
    And match response.name        == 'Employee Benefits Eligibility'
    And match response.status      == 'ACTIVE'
    And match response.versions[0].status  == 'DRAFT'
    And match response.versions[0].version == 1
    * def decisionTableRuleId = response.id

  Scenario: Decision table rule is retrievable by id
    # Create
    Given path '/api/v1/rules'
    And request
      """
      {
        "ruleSetId":   "#(ruleSetId)",
        "name":        "Employee Benefits Eligibility - Get",
        "description": "Used to test GET after create",
        "dmnXml":      "#(decisionTableDmn)"
      }
      """
    When method post
    Then status 201
    * def createdId = response.id
    # Retrieve
    Given path '/api/v1/rules/' + createdId
    When method get
    Then status 200
    And match response.id   == createdId
    And match response.name == 'Employee Benefits Eligibility - Get'

  Scenario: Decision table rule version can be activated
    Given path '/api/v1/rules'
    And request
      """
      {
        "ruleSetId":   "#(ruleSetId)",
        "name":        "Employee Benefits Eligibility - Activate",
        "description": "Activate version lifecycle test",
        "dmnXml":      "#(decisionTableDmn)"
      }
      """
    When method post
    Then status 201
    * def ruleId = response.id
    Given path '/api/v1/rules/' + ruleId + '/versions/1/activate'
    And request {}
    When method post
    Then status 200
    And match response.status  == 'ACTIVE'
    And match response.version == 1

  # ── Decision Tree (DRG) ──────────────────────────────────────────────────────

  Scenario: Create a rule using a decision tree (DRG) DMN returns 201
    Given path '/api/v1/rules'
    And request
      """
      {
        "ruleSetId":   "#(ruleSetId)",
        "name":        "Insurance Premium Decision Tree",
        "description": "DRG with 3 linked decisions: driver risk level + vehicle surcharge → final premium",
        "dmnXml":      "#(decisionTreeDmn)"
      }
      """
    When method post
    Then status 201
    And match response.name        == 'Insurance Premium Decision Tree'
    And match response.status      == 'ACTIVE'
    And match response.versions[0].status  == 'DRAFT'
    And match response.versions[0].version == 1
    * def decisionTreeRuleId = response.id

  Scenario: Decision tree rule is retrievable by id
    Given path '/api/v1/rules'
    And request
      """
      {
        "ruleSetId":   "#(ruleSetId)",
        "name":        "Insurance Premium Decision Tree - Get",
        "description": "Used to test GET after create",
        "dmnXml":      "#(decisionTreeDmn)"
      }
      """
    When method post
    Then status 201
    * def createdId = response.id
    Given path '/api/v1/rules/' + createdId
    When method get
    Then status 200
    And match response.id   == createdId
    And match response.name == 'Insurance Premium Decision Tree - Get'

  Scenario: Decision tree rule version can be activated
    Given path '/api/v1/rules'
    And request
      """
      {
        "ruleSetId":   "#(ruleSetId)",
        "name":        "Insurance Premium Decision Tree - Activate",
        "description": "Activate version lifecycle test",
        "dmnXml":      "#(decisionTreeDmn)"
      }
      """
    When method post
    Then status 201
    * def ruleId = response.id
    Given path '/api/v1/rules/' + ruleId + '/versions/1/activate'
    And request {}
    When method post
    Then status 200
    And match response.status  == 'ACTIVE'
    And match response.version == 1

  Scenario: Both DMN types appear in rule list for the rule set
    # Create one of each type
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'DT-List-Check', description: 'd', dmnXml: '#(decisionTableDmn)' }
    When method post
    Then status 201
    Given path '/api/v1/rules'
    And request { ruleSetId: '#(ruleSetId)', name: 'Tree-List-Check', description: 'd', dmnXml: '#(decisionTreeDmn)' }
    When method post
    Then status 201
    # List all rules for this rule set — both should appear
    Given path '/api/v1/rules'
    And param ruleSetId = ruleSetId
    When method get
    Then status 200
    And match response.totalElements == '#? _ >= 2'

  # ── Security ─────────────────────────────────────────────────────────────────

  Scenario: Creating a DMN rule without a token returns 401
    Given path '/api/v1/rules'
    And configure headers = { 'Content-Type': 'application/json' }
    And request { ruleSetId: '#(ruleSetId)', name: 'No-Auth', description: 'd', dmnXml: '#(decisionTableDmn)' }
    When method post
    Then status 401
