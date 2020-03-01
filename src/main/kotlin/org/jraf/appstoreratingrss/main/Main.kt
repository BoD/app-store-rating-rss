/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2020-present Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jraf.appstoreratingrss.main

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.withCharset
import io.ktor.request.host
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.escapeHTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import org.jraf.klibappstorerating.AppStore
import org.jraf.klibappstorerating.KLibAppStoreRating
import org.jraf.klibappstorerating.RatingRetrievalException
import java.io.FileNotFoundException
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


private const val DEFAULT_PORT = 8042

private const val ENV_PORT = "PORT"

private const val PATH_APP_STORE_ID = "storeId"
private const val PATH_APP_ID = "appId"

private const val PARAM_FRIENDLY_NAME = "friendlyName"
private const val PARAM_HTML = "html"
private const val PARAM_TRUE = "true"
private const val PARAM_NO_STORE_LINK = "noStoreLink"

private const val STORE_ID_GOOGLE_PLAY_STORE = "googlePlayStore"
private const val STORE_ID_APPLE_APP_STORE = "appleAppStore"

private const val APP_URL = "https://app-store-rating-rss.herokuapp.com"

private val GUID_DATE_FORMAT = DateTimeFormatter.ofPattern("YYYY-MM-dd")
private val PUB_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy '00:00:00 Z'", Locale.US)
private val RATING_DECIMAL_FORMAT = DecimalFormat("#.##")


fun main() {
    val listenPort = System.getenv(ENV_PORT)?.toInt() ?: DEFAULT_PORT
    embeddedServer(Netty, listenPort) {
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

                val wantHtml = call.request.queryParameters[PARAM_HTML] == PARAM_TRUE
                if (wantHtml) {
                    call.respondText(getHtml(appStore, appId, rating, friendlyName), ContentType.Text.Html.withCharset(Charsets.UTF_8))
                } else {
                    val wantNoStoreLink = call.request.queryParameters[PARAM_NO_STORE_LINK] == PARAM_TRUE
                    val selfLink = if (wantNoStoreLink) {
                        URLBuilder("${call.request.origin.scheme}://${call.request.host()}${call.request.uri}").apply {
                            parameters.append(PARAM_HTML, PARAM_TRUE)
                        }.buildString()
                    } else {
                        null
                    }
                    call.respondText(getRss(appStore, appId, rating, friendlyName, selfLink), ContentType.Application.Rss.withCharset(Charsets.UTF_8))
                }
            }
        }
    }.start(wait = true)
}

private fun getAppStoreName(appStore: AppStore): String {
    return when (appStore) {
        AppStore.APPLE_APP_STORE -> "Apple App Store"
        AppStore.GOOGLE_PLAY_STORE -> "Google Play Store"
    }
}

private fun getRss(
    appStore: AppStore,
    appId: String,
    rating: Float,
    friendlyName: String?,
    selfLink: String?
): String {
    val appStoreStr = getAppStoreName(appStore)
    val appName = friendlyName ?: appId
    val link = (selfLink ?: KLibAppStoreRating.getStorePageUrl(appStore, appId)).escapeHTML()
    val today = LocalDate.now()
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
            <channel>
                <title>$appStoreStr ratings for $appName</title>
                <description>$appStoreStr ratings for $appName</description>
                <link>$link</link>
                <item>
                    <guid isPermaLink="false">${formatGuid(today)}</guid>
                    <title>Today's $appStoreStr rating for $appName is ${formatRating(rating)}</title>
                    <pubDate>${formatPubDate(today)}</pubDate>
                    <link>$link</link>
                </item>
            </channel>
        </rss>
    """.trimIndent()
}

private fun getHtml(appStore: AppStore, appId: String, rating: Float, friendlyName: String?): String {
    val appStoreStr = getAppStoreName(appStore)
    val appName = friendlyName ?: appId
    val link = KLibAppStoreRating.getStorePageUrl(appStore, appId).escapeHTML()
    return createHTML()
        .html {
            head {
                title("Today's $appStoreStr rating for $appName is ${formatRating(rating)}")
            }
            body {
                a(href = link) { +"Today's $appStoreStr rating for $appName is ${formatRating(rating)}" }
            }
        }.toString()
}


private fun formatRating(rating: Float): String = RATING_DECIMAL_FORMAT.format(rating)

private fun formatGuid(date: LocalDate): String = date.format(GUID_DATE_FORMAT)

private fun formatPubDate(date: LocalDate): String = PUB_DATE_FORMAT.format(date)
