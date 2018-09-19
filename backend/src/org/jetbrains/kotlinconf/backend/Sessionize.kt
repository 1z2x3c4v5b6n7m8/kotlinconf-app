package org.jetbrains.kotlinconf.backend

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.jetbrains.kotlinconf.data.*
import java.time.*
import java.util.concurrent.*

@Volatile
private var sessionizeData: SessionizeData? = null
val comeBackLater = HttpStatusCode(477, "Come Back Later")
val tooLate = HttpStatusCode(478, "Too Late")
val keynoteTimeZone = ZoneId.of("Europe/Paris")!!
val keynoteEndDateTime = ZonedDateTime.of(
    2018, 10, 4, 10, 0, 0, 0, keynoteTimeZone
)!!

const val fakeSessionId = "007"

fun Application.launchSyncJob(sessionizeUrl: String, sessionizeInterval: Long) {
    log.info("Synchronizing each $sessionizeInterval minutes with $sessionizeUrl")
    launch(CommonPool) {
        while (true) {
            log.trace("Synchronizing to Sessionize…")
            synchronizeWithSessionize(sessionizeUrl)
            log.trace("Finished loading data from Sessionize.")
            delay(sessionizeInterval, TimeUnit.MINUTES)
        }
    }
}

private val client = HttpClient {
    install(JsonFeature) {
        serializer = KotlinxCustomSerializer()
    }
}

suspend fun synchronizeWithSessionize(sessionizeUrl: String) {
    val data = client.get<AllData>("$sessionizeUrl/all")
    val schedule = client.get<List<ConfSchedule>>("$sessionizeUrl/gridtable")
    val sessionsInfo = schedule
        .flatMap { it.rooms }
        .flatMap { it.sessions }
        .map { it.id to it }
        .toMap()

    val sessionsWithMeta = data.sessions.map { rawSession ->
        val source = sessionsInfo[rawSession.id]!!
        rawSession.copy(startsAt = source.startsAt, endsAt = source.endsAt, roomId = source.roomId
        )
    }

    sessionizeData = SessionizeData(data.copy(sessions = sessionsWithMeta))
}

fun getSessionizeData() = sessionizeData ?: throw ServiceUnavailable()