package com.decisionfabric.karate

import com.intuit.karate.junit5.Karate
import org.junit.jupiter.api.Tag

/**
 * JUnit 5 runner for all Karate API tests.
 *
 * Run with:
 *   ./gradlew karateTest
 *
 * Or target a single feature:
 *   ./gradlew karateTest --tests "*KarateRunner*" -Dkarate.options="classpath:karate/rule-sets"
 *
 * Requires a running local stack:
 *   docker compose up -d   (or ./run-local.sh --build-only then docker compose up -d)
 */
@Tag("karate")
class KarateRunner {

    /** Runs all Karate feature files under src/test/resources/karate/. */
    @Karate.Test
    fun apiTests(): Karate =
        Karate.run("classpath:karate").relativeTo(javaClass)
}
