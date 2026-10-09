package com.besklar.relatives.data.remote

import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.PersonName
import com.besklar.relatives.model.PersonSummary
import com.besklar.relatives.model.Relative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

val RECORDS_BASE_URL: HttpUrl = "https://fs-records-sample.vercel.app/".toHttpUrl()
val recordsJson = Json { ignoreUnknownKeys = true }

interface RecordsService {
    @GET("persons.json") suspend fun people(): PeopleEnvelopeDto
    @GET("persons/{id}.json") suspend fun profile(@Path("id") id: String): ProfileDto
}

fun recordsService(client: OkHttpClient, baseUrl: HttpUrl = RECORDS_BASE_URL): RecordsService =
    Retrofit.Builder().baseUrl(baseUrl).client(client)
        .addConverterFactory(recordsJson.asConverterFactory("application/json".toMediaType()))
        .build().create(RecordsService::class.java)

@Serializable
data class PeopleEnvelopeDto(val updated: String, val count: Int, val persons: List<JsonElement>)

@Serializable data class NameDto(val given: String, val surname: String)
@Serializable data class EventDto(val date: String, val year: Int, val place: String)

@Serializable
data class SummaryDto(
    val id: String,
    val name: NameDto,
    val sex: String,
    val living: Boolean,
    val birth: EventDto,
    val death: EventDto?,
    val portraitUrl: String,
)

@Serializable
data class RelativeDto(
    val id: String,
    val relationship: String,
    val name: NameDto,
    val birthYear: Int,
    val deathYear: Int?,
)

@Serializable
data class ProfileDto(
    val id: String,
    val name: NameDto,
    val sex: String,
    val living: Boolean,
    val birth: EventDto,
    val death: EventDto?,
    val portraitUrl: String,
    val occupation: String? = null,
    val biography: String,
    val relatives: List<RelativeDto>,
    val lastModified: String,
) {
    fun summary(): PersonSummary = SummaryDto(id, name, sex, living, birth, death, portraitUrl).toModel()
}

class InvalidRecord(message: String) : Exception(message)

private val personId = Regex("[A-Za-z0-9-]+")
fun validateId(id: String) {
    if (!personId.matches(id)) throw InvalidRecord("Invalid person ID")
}

private fun NameDto.toModel(): PersonName {
    if (given.isBlank() && surname.isBlank()) throw InvalidRecord("Missing name")
    return PersonName(given.trim(), surname.trim())
}

private fun EventDto.toModel(): LifeEvent {
    if (date.isBlank() || place.isBlank()) throw InvalidRecord("Incomplete life event")
    return LifeEvent(date, year, place)
}

fun SummaryDto.toModel(): PersonSummary {
    validateId(id)
    return PersonSummary(id, name.toModel(), sex, living, birth.toModel(), death?.toModel(), portraitUrl)
}

fun RelativeDto.toModel(): Relative {
    validateId(id)
    if (relationship.isBlank()) throw InvalidRecord("Missing relationship")
    return Relative(id, relationship, name.toModel(), birthYear, deathYear)
}

data class AcceptedPeople(val people: List<PersonSummary>, val updated: String, val discardedCount: Int)

// Decode each element separately: a single bad person never poisons its siblings.
suspend fun PeopleEnvelopeDto.acceptedPeople(): AcceptedPeople = withContext(Dispatchers.Default) {
    if (updated.isBlank()) throw InvalidRecord("Missing update metadata")
    val accepted = mutableListOf<PersonSummary>()
    val ids = mutableSetOf<String>()
    var discarded = 0
    for (element in persons) {
        currentCoroutineContext().ensureActive()
        val person = try {
            recordsJson.decodeFromJsonElement<SummaryDto>(element).toModel()
        } catch (_: SerializationException) {
            null
        } catch (_: InvalidRecord) {
            null
        }
        if (person != null && ids.add(person.id)) accepted += person else discarded++
    }
    if (persons.isNotEmpty() && accepted.isEmpty()) throw InvalidRecord("No valid person records")
    AcceptedPeople(accepted, updated, discarded)
}
