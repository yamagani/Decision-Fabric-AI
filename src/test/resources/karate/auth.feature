# auth.feature — reusable token-fetch called via karate.call()
@ignore
Feature: Obtain OAuth2 tokens from local Keycloak

  Scenario: Get token for a user
    # Called as: def auth = call read('classpath:karate/auth.feature') { username: '...', password: '...' }
    Given url tokenUrl
    And form field grant_type  = 'password'
    And form field client_id   = clientId
    And form field client_secret = clientSecret
    And form field username    = username
    And form field password    = password
    When method post
    Then status 200
    * def accessToken = response.access_token
