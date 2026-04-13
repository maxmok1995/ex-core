package co.nilin.opex.config.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("co.nilin.opex")
class ConfigApp

fun main(args: Array<String>) {
    runApplication<ConfigApp>(*args)
}
