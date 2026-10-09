package com.besklar.relatives.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.besklar.relatives.data.local.PeopleDatabase
import com.besklar.relatives.data.remote.PeopleEnvelopeDto
import com.besklar.relatives.data.remote.ProfileDto
import com.besklar.relatives.data.remote.RecordsService
import com.besklar.relatives.data.remote.recordsJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-${UUID.randomUUID()}.db"
    private lateinit var db: PeopleDatabase
    private lateinit var repository: PersonRepository
    private val service = FakeService()
    private val clock = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC)

    @Before fun open() {
        db = Room.databaseBuilder(context, PeopleDatabase::class.java, name)
            .addMigrations(PeopleDatabase.MIGRATION_1_2).build()
        repository = PersonRepository(db, service, clock)
    }
    @After fun close() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun uncachedAndSuccessfullyEmptyAreDifferent() = runBlocking {
        assertNull(repository.observePeople().first())
        service.listBody = Fixtures.people()
        assertEquals(RefreshResult.Success(), repository.refreshPeople())
        val saved = repository.observePeople().first()!!
        assertTrue(saved.people.isEmpty())
        assertEquals(clock.millis(), saved.retrievedAt)
    }

    @Test fun mixedRecordsPersistOnlyAcceptedPeopleInOrderAndSkippedCount() = runBlocking {
        service.listBody = Fixtures.people("{}", Fixtures.summary("B-2"), "null", Fixtures.summary(), Fixtures.summary())
        assertEquals(RefreshResult.Success(3), repository.refreshPeople())
        val saved = repository.observePeople().first()!!
        assertEquals(listOf("B-2", "A-1"), saved.people.map { it.id })
        assertEquals(3, saved.discardedRecordCount)
        assertNull(repository.observeProfile("A-1").first())
    }

    @Test fun listRefreshDoesNotOverwriteOrDeleteOpenedProfiles() = runBlocking {
        assertEquals(RefreshResult.Success(), repository.refreshProfile("A-1"))
        val profile = repository.observeProfile("A-1").first()
        service.listBody = Fixtures.people(Fixtures.summary("A-1", "Different"))
        repository.refreshPeople()
        assertEquals("Different", repository.observePeople().first()!!.people.single().name.given)
        assertEquals(profile, repository.observeProfile("A-1").first())
        service.listBody = Fixtures.people()
        repository.refreshPeople()
        assertEquals(profile, repository.observeProfile("A-1").first())
    }

    @Test fun failedRefreshesPreserveLastSavedList() = runBlocking {
        repository.refreshPeople()
        val saved = repository.observePeople().first()
        for (body in listOf(Fixtures.people("{}", "null"), "{", "{}")) {
            service.listBody = body
            assertEquals(RefreshResult.InvalidData, repository.refreshPeople())
            assertEquals(saved, repository.observePeople().first())
        }
        service.failure = IOException("offline")
        assertEquals(RefreshResult.NetworkFailure, repository.refreshPeople())
        assertEquals(saved, repository.observePeople().first())
        service.failure = HttpException(Response.error<Any>(503, "down".toResponseBody()))
        assertEquals(RefreshResult.HttpFailure(503), repository.refreshPeople())
        assertEquals(saved, repository.observePeople().first())
    }

    @Test fun profileUpdatesReplaceRelativesAndRejectMismatchedOrInvalidProfiles() = runBlocking {
        repository.refreshProfile("A-1")
        assertEquals("B-2", repository.observeProfile("A-1").first()!!.relatives.single().id)
        service.profileBody = Fixtures.profile(relatives = Fixtures.relative("C-3"))
        repository.refreshProfile("A-1")
        val updated = repository.observeProfile("A-1").first()!!
        assertEquals(listOf("C-3"), updated.relatives.map { it.id })
        service.profileBody = Fixtures.profile("WRONG-ID")
        assertEquals(RefreshResult.InvalidData, repository.refreshProfile("A-1"))
        assertEquals(updated, repository.observeProfile("A-1").first())
        service.profileBody = "{}"
        assertEquals(RefreshResult.InvalidData, repository.refreshProfile("A-1"))
        assertEquals(updated, repository.observeProfile("A-1").first())
        assertEquals(RefreshResult.InvalidData, repository.refreshProfile("../bad"))
    }

    @Test fun snapshotsAndRelativesSurviveDatabaseCloseAndReopen() = runBlocking {
        service.listBody = Fixtures.people(Fixtures.summary(), "{}")
        repository.refreshPeople()
        repository.refreshProfile("A-1")
        val list = repository.observePeople().first()
        val profile = repository.observeProfile("A-1").first()
        db.close()
        db = Room.databaseBuilder(context, PeopleDatabase::class.java, name)
            .addMigrations(PeopleDatabase.MIGRATION_1_2).build()
        repository = PersonRepository(db, service, clock)
        service.failure = IOException("offline")
        assertEquals(list, repository.observePeople().first())
        assertEquals(profile, repository.observeProfile("A-1").first())
        assertNull(repository.observeProfile("not-opened").first())
    }

    @Test fun cancelingFetchPropagatesAndKeepsSavedData() = runBlocking {
        repository.refreshPeople()
        val previous = repository.observePeople().first()
        val started = CompletableDeferred<Unit>()
        service.beforeList = { started.complete(Unit); CompletableDeferred<Unit>().await() }
        val job = async(Dispatchers.IO) { repository.refreshPeople() }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(previous, repository.observePeople().first())
        service.beforeList = null
        assertEquals(RefreshResult.Success(), repository.refreshPeople())
    }

    @Test fun malformedRelativesAreSkippedAndCountSurvivesReopen() = runBlocking {
        service.profileBody = Fixtures.profile(relatives = "${Fixtures.relative()}, {}, null, ${Fixtures.relative()}")
        assertEquals(RefreshResult.Success(3), repository.refreshProfile("A-1"))
        val saved = repository.observeProfile("A-1").first()!!
        assertEquals("A family story.", saved.biography)
        assertEquals(listOf("B-2"), saved.relatives.map { it.id })
        assertEquals(3, saved.discardedRelativeCount)
        db.close()
        db = Room.databaseBuilder(context, PeopleDatabase::class.java, name)
            .addMigrations(PeopleDatabase.MIGRATION_1_2).build()
        repository = PersonRepository(db, service, clock)
        assertEquals(saved, repository.observeProfile("A-1").first())
    }

    @Test fun entirelyInvalidRelativesStillSaveValidCoreWithExplicitSkippedCount() = runBlocking {
        service.profileBody = Fixtures.profile(relatives = "{}, null")
        assertEquals(RefreshResult.Success(2), repository.refreshProfile("A-1"))
        val saved = repository.observeProfile("A-1").first()!!
        assertEquals("Ada Whitcomb", saved.person.name.fullName)
        assertTrue(saved.relatives.isEmpty())
        assertEquals(2, saved.discardedRelativeCount)
    }

    @Test fun cancellationInsideRoomTransactionRollsBackChanges() = runBlocking {
        repository.refreshPeople()
        val previous = repository.observePeople().first()
        try {
            db.withTransaction {
                db.peopleDao().clearSummaries()
                throw CancellationException("screen removed")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(previous, repository.observePeople().first())
    }

    @Test fun storageConstraintFailureRollsBackWholeSnapshot() = runBlocking {
        repository.refreshPeople()
        val previous = repository.observePeople().first()
        val rows = db.peopleDao().observePeople().first()!!.people
        try {
            db.withTransaction {
                db.peopleDao().clearSummaries()
                db.peopleDao().putSummaries(listOf(rows.first(), rows.first()))
            }
            fail("Expected duplicate primary key")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(previous, repository.observePeople().first())
    }

    private class FakeService : RecordsService {
        var listBody = Fixtures.people(Fixtures.summary())
        var profileBody = Fixtures.profile()
        var failure: Exception? = null
        var beforeList: (suspend () -> Unit)? = null
        override suspend fun people(): PeopleEnvelopeDto {
            beforeList?.invoke()
            failure?.let { throw it }
            return recordsJson.decodeFromString(listBody)
        }
        override suspend fun profile(id: String): ProfileDto {
            failure?.let { throw it }
            return recordsJson.decodeFromString(profileBody)
        }
    }
}
