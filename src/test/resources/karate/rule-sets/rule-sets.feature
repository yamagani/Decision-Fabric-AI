Feature: Rule Set CRUD API

  Background:
    # Fetch a token for rule-admin before each scenario
    * def auth = call read('classpath:karate/auth.feature') { username: #(ruleAdminUser), password: #(ruleAdminPass) }
    * def token = auth.accessToken
    * def authHeader = 'Bearer ' + token
    * url baseUrl
    * configure headers = { Authorization: '#(authHeader)', 'Content-Type': 'application/json' }
    # Unique prefix so parallel or repeated runs don't collide on server-side unique-name constraints
    * def testId = java.util.UUID.randomUUID().toString().substring(0, 8)

  # ── CREATE ──────────────────────────────────────────────────────────────────

  Scenario: Create a rule set successfully
    * def rsName = 'Pricing-Rules-' + testId
    Given path '/api/v1/rule-sets'
    And request { name: '#(rsName)', description: 'All pricing decision tables' }
    When method post
    Then status 201
    And match response.name == rsName
    And match response.status == 'ACTIVE'
    And match response.id == '#uuid'
    * def ruleSetId = response.id

  Scenario: Create duplicate rule set returns 409
    * def dupeName = 'Dupe-Set-' + testId
    Given path '/api/v1/rule-sets'
    And request { name: '#(dupeName)', description: 'first' }
    When method post
    Then status 201
    # Create same name again
    Given path '/api/v1/rule-sets'
    And request { name: '#(dupeName)', description: 'second' }
    When method post
    Then status 409

  Scenario: Create rule set with blank name returns 400
    Given path '/api/v1/rule-sets'
    And request { name: '', description: 'bad' }
    When method post
    Then status 400

  # ── READ ─────────────────────────────────────────────────────────────────────

  Scenario: Get rule set by id
    # First create
    * def getByIdName = 'GetById-' + testId
    Given path '/api/v1/rule-sets'
    And request { name: '#(getByIdName)', description: 'desc' }
    When method post
    Then status 201
    * def id = response.id
    # Then retrieve
    Given path '/api/v1/rule-sets/' + id
    When method get
    Then status 200
    And match response.id == id
    And match response.name == getByIdName

  Scenario: Get non-existent rule set returns 404
    Given path '/api/v1/rule-sets/00000000-0000-0000-0000-000000000000'
    When method get
    Then status 404

  Scenario: List rule sets returns paginated response
    Given path '/api/v1/rule-sets'
    And param page = 0
    And param size = 10
    When method get
    Then status 200
    And match response.content == '#array'
    And match response.page == 0
    And match response.size == 10
    And match response.totalElements == '#number'

  # ── SECURITY ─────────────────────────────────────────────────────────────────

  Scenario: Unauthenticated request returns 401
    * configure headers = { 'Content-Type': 'application/json' }
    Given path '/api/v1/rule-sets'
    When method get
    Then status 401

  Scenario: DECISION_CONSUMER cannot create rule sets (403)
    * configure headers = null
    * def consAuth = call read('classpath:karate/auth.feature') { username: #(consumerUser), password: #(consumerPass) }
    * def consHeader = 'Bearer ' + consAuth.accessToken
    * configure headers = { Authorization: '#(consHeader)', 'Content-Type': 'application/json' }
    * def failName = 'Should-Fail-' + testId
    Given path '/api/v1/rule-sets'
    And request { name: '#(failName)', description: 'test' }
    When method post
    Then status 403

  # ── DELETE ────────────────────────────────────────────────────────────────────

  Scenario: Delete (soft) a rule set
    # Create
    * def delName = 'Delete-Me-' + testId
    Given path '/api/v1/rule-sets'
    And request { name: '#(delName)', description: 'to be deleted' }
    When method post
    Then status 201
    * def id = response.id
    # Delete
    Given path '/api/v1/rule-sets/' + id
    When method delete
    Then status 204
    # Confirm it's no longer in active list
    Given path '/api/v1/rule-sets'
    And param includeInactive = false
    When method get
    Then status 200
    And match response.content[*].id !contains id
