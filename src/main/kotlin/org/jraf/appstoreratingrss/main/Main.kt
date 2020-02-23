package org.jraf.appstoreratingrss.main

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jraf.klibappstorerating.AppStore
import org.jraf.klibappstorerating.KLibAppStoreRating
import org.jraf.klibappstorerating.RatingRetrievalException
import java.io.FileNotFoundException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date

private const val DEFAULT_PORT = 8042

private const val ENV_PORT = "PORT"

private const val PATH_APP_STORE_ID = "storeId"
private const val PATH_APP_ID = "appId"
private const val PARAM_FRIENDLY_NAME = "friendlyName"

private const val STORE_ID_GOOGLE_PLAY_STORE = "googlePlayStore"
private const val STORE_ID_APPLE_APP_STORE = "appleAppStore"

private const val APP_URL = "https://app-store-rating-rss.herokuapp.com"

private val GUID_TIME_FORMAT = SimpleDateFormat("YYYY-MM-dd")
private val RATING_DECIMAL_FORMAT = DecimalFormat("#.##")


fun main() {
    val port = System.getenv(ENV_PORT)?.toInt() ?: DEFAULT_PORT
    embeddedServer(Netty, port) {
        install(DefaultHeaders)

        install(StatusPages) {
            status(HttpStatusCode.NotFound) {
                call.respondText(
                    text = "Usage: $APP_URL/<$STORE_ID_GOOGLE_PLAY_STORE|$STORE_ID_APPLE_APP_STORE>/<app id>",
                    status = it
                )
            }

            exception<IllegalArgumentException> { exception ->
                call.respond(HttpStatusCode.BadRequest, exception.message ?: "Bad request")
            }
            exception<RatingRetrievalException> { exception ->
                val cause = exception.cause
                call.respond(
                    HttpStatusCode.BadRequest, if (cause is FileNotFoundException) {
                        "Could not retrieve the rating for this app:\n${cause.message} not found"
                    } else {
                        exception.message ?: "Could not retrieve the rating for this app"
                    }
                )
            }
        }

        routing {
            get("{$PATH_APP_STORE_ID}/{$PATH_APP_ID}") {
                val appStoreId = call.parameters[PATH_APP_STORE_ID]!!
                val appId = call.parameters[PATH_APP_ID]!!
                val appStore = when (appStoreId) {
                    STORE_ID_GOOGLE_PLAY_STORE -> AppStore.GOOGLE_PLAY_STORE
                    STORE_ID_APPLE_APP_STORE -> AppStore.APPLE_APP_STORE
                    else -> throw IllegalArgumentException("Unknown app store, authorized values are:\n- $STORE_ID_GOOGLE_PLAY_STORE\n- $STORE_ID_APPLE_APP_STORE")
                }
                val friendlyName = call.request.queryParameters[PARAM_FRIENDLY_NAME]
                val rating = KLibAppStoreRating.retrieveRating(appStore, appId)

                call.respondText(getRss(appStore, appId, rating, friendlyName), ContentType.Application.Rss.withCharset(Charsets.UTF_8))
            }
        }
    }.start(wait = true)
}

private fun getRss(appStore: AppStore, appId: String, rating: Float, friendlyName: String?): String {
    val appStoreStr = when (appStore) {
        AppStore.APPLE_APP_STORE -> "Apple App Store"
        AppStore.GOOGLE_PLAY_STORE -> "Google Play Store"
    }
    val appName = friendlyName ?: appId
    return """
        <?xml version="1.0" encoding="UTF-8" ?>
        <rss version="2.0">
            <channel>
                <title>$appStoreStr ratings for $appName</title>
                <item>
                    <guid isPermaLink="false">${getTodayGuid()}</guid>
                    <title>Today's $appStoreStr rating for $appName is ${formatRating(rating)}</title>
                    <link>${KLibAppStoreRating.getStorePageUrl(appStore, appId)}</link>
                </item>
            </channel>
        </rss>
    """.trimIndent()
}


private fun formatRating(rating: Float): String = RATING_DECIMAL_FORMAT.format(rating)

private fun getTodayGuid(): String = GUID_TIME_FORMAT.format(Date())
