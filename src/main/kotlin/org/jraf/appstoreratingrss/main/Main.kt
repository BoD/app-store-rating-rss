package org.jraf.appstoreratingrss.main

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jraf.klibappstorerating.AppStore
import org.jraf.klibappstorerating.KLibAppStoreRating
import java.text.SimpleDateFormat
import java.util.Date

private const val DEFAULT_PORT = 8042

private const val ENV_PORT = "PORT"

private const val PATH_APP_STORE_ID = "storeId"
private const val PATH_APP_ID = "appId"

private const val STORE_ID_GOOGLE_PLAY_STORE = "googlePlayStore"
private const val STORE_ID_APPLE_APP_STORE = "appleAppStore"

private val GUID_TIME_FORMAT = SimpleDateFormat("YYYY-MM-dd")

fun main() {
    val port = System.getenv(ENV_PORT)?.toInt() ?: DEFAULT_PORT
    embeddedServer(Netty, port) {
        install(DefaultHeaders)

        routing {
            get("{$PATH_APP_STORE_ID}/{$PATH_APP_ID}") {
                val appStoreId = call.parameters[PATH_APP_STORE_ID]!!
                val appId = call.parameters[PATH_APP_ID]!!
                val appStore = when (appStoreId) {
                    STORE_ID_GOOGLE_PLAY_STORE -> AppStore.GOOGLE_PLAY_STORE
                    STORE_ID_APPLE_APP_STORE -> AppStore.APPLE_APP_STORE
                    else -> throw IllegalArgumentException("Unknown app store")
                }
                val rating = KLibAppStoreRating.retrieveRating(
                    appStore, appId
                )

                call.respondText(getRss(appStore, appId, rating), ContentType.Application.Rss.withCharset(Charsets.UTF_8))
            }
        }
    }.start(wait = true)
}

private fun getRss(appStore: AppStore, appId: String, rating: Float): String {
    val appStoreStr = when (appStore) {
        AppStore.APPLE_APP_STORE -> "Apple App Store"
        AppStore.GOOGLE_PLAY_STORE -> "Google Play Store"
    }
    return """
        <?xml version="1.0" encoding="UTF-8" ?>
        <rss version="2.0">
            <channel>
                <title>$appStoreStr ratings for $appId</title>
                <item>
                    <guid>${getTodayGuid()}</guid>
                    <description>Today's $appStoreStr rating for $appId is $rating</description>
                </item>
            </channel>
        </rss>
    """.trimIndent()
}

private fun getTodayGuid(): String = GUID_TIME_FORMAT.format(Date())
