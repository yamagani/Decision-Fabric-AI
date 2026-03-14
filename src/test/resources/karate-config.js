// karate-config.js  — loaded automatically by Karate before every feature
function fn() {
  var env = karate.env || 'local';
  karate.log('Karate env:', env);

  var config = {
    env: env,
    baseUrl: 'http://localhost:8080',

    // Keycloak token endpoint (local docker-compose)
    tokenUrl: 'http://localhost:9090/realms/decision-fabric-ai/protocol/openid-connect/token',
    clientId: 'decision-fabric-ai-client',
    clientSecret: 'local-secret',

    // Default test users (defined in realm-export.json)
    ruleAdminUser: 'rule-admin',
    ruleAdminPass: 'password',
    systemAdminUser: 'system-admin',
    systemAdminPass: 'password',
    consumerUser: 'consumer',
    consumerPass: 'password'
  };

  if (env === 'ci') {
    config.baseUrl   = karate.properties['app.url']   || 'http://localhost:8080';
    config.tokenUrl  = karate.properties['token.url'] || config.tokenUrl;
  }

  // Helper: fetch a bearer token for a given user
  karate.configure('ssl', true);
  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 30000);

  return config;
}
