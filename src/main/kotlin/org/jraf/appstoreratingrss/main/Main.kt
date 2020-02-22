package org.jraf.appstoreratingrss.main

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

private const val DEFAULT_PORT = 8042

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: DEFAULT_PORT
    embeddedServer(Netty, port) {
        routing {
            get("") {
                call.respond("Hello, World!")
            }
        }
    }.start(wait = true)
}