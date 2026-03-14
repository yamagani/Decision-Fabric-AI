package com.decisionfabric

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DecisionFabricAiApplication

fun main(args: Array<String>) {
    runApplication<DecisionFabricAiApplication>(*args)
}
