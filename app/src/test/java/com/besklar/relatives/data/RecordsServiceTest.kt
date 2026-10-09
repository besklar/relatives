package com.besklar.relatives.data

import com.besklar.relatives.data.remote.InvalidRecord
import com.besklar.relatives.data.remote.PeopleEnvelopeDto
import com.besklar.relatives.data.remote.acceptedPeople
import com.besklar.relatives.data.remote.acceptedRelatives
import com.besklar.relatives.data.remote.ProfileDto
import com.besklar.relatives.data.remote.recordsJson
import com.besklar.relatives.data.remote.recordsService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class RecordsServiceTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
    private val service by lazy { recordsService(client, server.url("/")) }

    @Before fun start() = server.start()
    @After fun stop() {
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test fun httpListDecodesIntoOwnModelsAndPreservesDisplayDates() = runTest {
        server.enqueue(MockResponse().setBody(Fixtures.people(Fixtures.summary(), Fixtures.summary("B-2", living = true))))
        val accepted = service.people().acceptedPeople()
        assertEquals("/persons.json", server.takeRequest().path)
        assertEquals("about 1838", accepted.people.first().birth.date)
        assertEquals(1838, accepted.people.first().birth.year)
        assertTrue(accepted.people.last().living)
        assertNull(accepted.people.last().death)
    }

    @Test fun badRecordsAreIsolatedAndDuplicateIdsKeepFirstValidRecord() = runTest {
        val missingName = Fixtures.summary("BAD-1").replace("\"given\":\"Ada\",", "")
        val wrongType = Fixtures.summary("BAD-2").replace("\"year\":1838", "\"year\":{}")
        val badId = Fixtures.summary("bad/id")
        val noName = Fixtures.summary("BAD-3", "").replace("Whitcomb", "")
        val body = Fixtures.people(missingName, Fixtures.summary(), wrongType, "null", "42", badId,
            noName, Fixtures.summary("A-1", "Duplicate"), Fixtures.summary("B-2"), count = 99)
        server.enqueue(MockResponse().setBody(body))
        val accepted = service.people().acceptedPeople()
        assertEquals(listOf("A-1", "B-2"), accepted.people.map { it.id })
        assertEquals("Ada", accepted.people.first().name.given)
        assertEquals(7, accepted.discardedCount)
    }

    @Test fun unknownFieldsDoNotBlockRecordsAndEmptyListIsValid() = runTest {
        val extra = Fixtures.summary().trimEnd().dropLast(1) + ",\"future\":{\"anything\":true}}"
        val accepted = recordsJson.decodeFromString<PeopleEnvelopeDto>(Fixtures.people(extra)).acceptedPeople()
        assertEquals(1, accepted.people.size)
        assertTrue(recordsJson.decodeFromString<PeopleEnvelopeDto>(Fixtures.people()).acceptedPeople().people.isEmpty())
    }

    @Test fun nonemptyAllInvalidListFailsInsteadOfPretendingToBeEmpty() = runTest {
        try {
            recordsJson.decodeFromString<PeopleEnvelopeDto>(Fixtures.people("{}", "null")).acceptedPeople()
            fail("Expected InvalidRecord")
        } catch (_: InvalidRecord) { }
    }

    @Test fun malformedEnvelopesFail() = runTest {
        for (body in listOf("{", "{}", """{"updated":"today","count":1,"persons":{}}""")) {
            server.enqueue(MockResponse().setBody(body))
            try {
                service.people()
                fail("Expected decoding failure")
            } catch (_: SerializationException) { }
        }
    }

    @Test fun profileUsesPersonEndpointAndAllowsNullOccupation() = runTest {
        server.enqueue(MockResponse().setBody(Fixtures.profile()))
        val profile = service.profile("A-1")
        assertEquals("/persons/A-1.json", server.takeRequest().path)
        assertNull(profile.occupation)
        assertEquals("B-2", profile.acceptedRelatives().relatives.single().id)
        assertEquals("Ada Whitcomb", profile.summary().name.fullName)
    }

    @Test fun badAndDuplicateRelativesAreDiscardedWhileCoreProfileRemainsUsable() = runTest {
        val unfamiliar = Fixtures.relative("C-3").replace("father", "cousin")
        val body = Fixtures.profile(relatives = listOf(Fixtures.relative(), "{}", "null",
            Fixtures.relative(), unfamiliar).joinToString(","))
        val dto = recordsJson.decodeFromString<ProfileDto>(body)
        val accepted = dto.acceptedRelatives()
        assertEquals("Ada Whitcomb", dto.summary().name.fullName)
        assertEquals(listOf("B-2", "C-3"), accepted.relatives.map { it.id })
        assertEquals("cousin", accepted.relatives.last().relationship)
        assertEquals(3, accepted.discardedCount)
    }

    @Test fun allInvalidAndEmptyRelativesStillAllowUsableProfileCore() = runTest {
        val invalid = recordsJson.decodeFromString<ProfileDto>(Fixtures.profile(relatives = "{},null"))
            .acceptedRelatives()
        assertTrue(invalid.relatives.isEmpty())
        assertEquals(2, invalid.discardedCount)
        val empty = recordsJson.decodeFromString<ProfileDto>(Fixtures.profile(relatives = ""))
            .acceptedRelatives()
        assertTrue(empty.relatives.isEmpty())
        assertEquals(0, empty.discardedCount)
    }

    @Test fun httpAndTransportFailuresRemainFailures() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            service.people()
            fail("Expected HTTP error")
        } catch (failure: HttpException) { assertEquals(503, failure.code()) }
        server.shutdown()
        try {
            service.people()
            fail("Expected transport error")
        } catch (_: IOException) { }
    }
}
