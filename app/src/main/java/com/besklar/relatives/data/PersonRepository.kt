package com.besklar.relatives.data

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.besklar.relatives.data.local.ListSnapshotRow
import com.besklar.relatives.data.local.PeopleDatabase
import com.besklar.relatives.data.local.ProfileRow
import com.besklar.relatives.data.local.RelativeRow
import com.besklar.relatives.data.local.SummaryRow
import com.besklar.relatives.data.local.toColumns
import com.besklar.relatives.data.remote.InvalidRecord
import com.besklar.relatives.data.remote.RecordsService
import com.besklar.relatives.data.remote.acceptedPeople
import com.besklar.relatives.data.remote.toModel
import com.besklar.relatives.data.remote.validateId
import com.besklar.relatives.model.PeopleSnapshot
import com.besklar.relatives.model.PersonProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock

sealed interface RefreshResult {
    data class Success(val discardedRecordCount: Int = 0) : RefreshResult
    data class HttpFailure(val code: Int) : RefreshResult
    data object NetworkFailure : RefreshResult
    data object InvalidData : RefreshResult
    data object StorageFailure : RefreshResult
}

class PersonRepository(
    private val database: PeopleDatabase,
    private val service: RecordsService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.peopleDao()
    private val refreshMutex = Mutex()

    fun observePeople(): Flow<PeopleSnapshot?> = dao.observePeople().map { it?.toModel() }
    fun observeProfile(id: String): Flow<PersonProfile?> = dao.observeProfile(id).map { it?.toModel() }

    suspend fun refreshPeople(): RefreshResult = refresh {
        val accepted = service.people().acceptedPeople()
        database.withTransaction {
            dao.putSnapshot(ListSnapshotRow(serviceUpdated = accepted.updated,
                retrievedAt = clock.millis(), discardedRecordCount = accepted.discardedCount))
            dao.clearSummaries()
            dao.putSummaries(accepted.people.mapIndexed { position, person ->
                SummaryRow(person.id, position = position, person = person.toColumns())
            })
        }
        RefreshResult.Success(accepted.discardedCount)
    }

    suspend fun refreshProfile(id: String): RefreshResult = refresh {
        validateId(id)
        val dto = service.profile(id)
        if (dto.id != id || dto.lastModified.isBlank()) throw InvalidRecord("Invalid profile metadata")
        val person = dto.summary()
        val relatives = dto.relatives.map { it.toModel() }
        database.withTransaction {
            dao.putProfile(ProfileRow(id, person.toColumns(), dto.occupation, dto.biography,
                dto.lastModified, clock.millis()))
            dao.clearRelatives(id)
            dao.putRelatives(relatives.mapIndexed { position, relative ->
                RelativeRow(id, position, relative.id, relative.relationship,
                    relative.name.given, relative.name.surname, relative.birthYear, relative.deathYear)
            })
        }
        RefreshResult.Success()
    }

    private suspend fun refresh(block: suspend () -> RefreshResult): RefreshResult = refreshMutex.withLock {
        try {
            block()
        } catch (failure: HttpException) {
            RefreshResult.HttpFailure(failure.code())
        } catch (_: IOException) {
            RefreshResult.NetworkFailure
        } catch (_: SerializationException) {
            RefreshResult.InvalidData
        } catch (_: InvalidRecord) {
            RefreshResult.InvalidData
        } catch (_: SQLiteException) {
            RefreshResult.StorageFailure
        }
        // CancellationException intentionally escapes: canceled work is not a failed refresh.
    }
}
