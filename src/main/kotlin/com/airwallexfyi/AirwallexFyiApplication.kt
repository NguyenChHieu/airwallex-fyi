package com.airwallexfyi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AirwallexFyiApplication

fun main(args: Array<String>) {
    runApplication<AirwallexFyiApplication>(*args)
}