package com.besklar.relatives.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.besklar.relatives.model.LifeEvent
import com.besklar.relatives.model.PeopleSnapshot
import com.besklar.relatives.model.PersonName
import com.besklar.relatives.model.PersonProfile
import com.besklar.relatives.model.PersonSummary
import com.besklar.relatives.model.Relative
import kotlinx.coroutines.flow.Flow

data class PersonColumns(
    val given: String,
    val surname: String,
    val sex: String,
    val living: Boolean,
    val birthDate: String,
    val birthYear: Int,
    val birthPlace: String,
    val deathDate: String?,
    val deathYear: Int?,
    val deathPlace: String?,
    val portraitUrl: String,
) {
    fun toModel(id: String) = PersonSummary(
        id, PersonName(given, surname), sex, living, LifeEvent(birthDate, birthYear, birthPlace),
        deathYear?.let { LifeEvent(checkNotNull(deathDate), it, checkNotNull(deathPlace)) }, portraitUrl,
    )
}

fun PersonSummary.toColumns() = PersonColumns(
    name.given, name.surname, sex, living, birth.date, birth.year, birth.place,
    death?.date, death?.year, death?.place, portraitUrl,
)

@Entity(tableName = "list_snapshots")
data class ListSnapshotRow(
    @PrimaryKey val id: Int = 1,
    val serviceUpdated: String,
    val retrievedAt: Long,
    val discardedRecordCount: Int,
)

@Entity(
    tableName = "person_summaries",
    foreignKeys = [ForeignKey(entity = ListSnapshotRow::class, parentColumns = ["id"],
        childColumns = ["snapshotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("snapshotId")],
)
data class SummaryRow(
    @PrimaryKey val id: String,
    val snapshotId: Int = 1,
    val position: Int,
    @Embedded val person: PersonColumns,
)

@Entity(tableName = "profiles")
data class ProfileRow(
    @PrimaryKey val id: String,
    @Embedded val person: PersonColumns,
    val occupation: String?,
    val biography: String,
    val lastModified: String,
    val retrievedAt: Long,
)

@Entity(
    tableName = "relatives", primaryKeys = ["ownerProfileId", "position"],
    foreignKeys = [ForeignKey(entity = ProfileRow::class, parentColumns = ["id"],
        childColumns = ["ownerProfileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ownerProfileId")],
)
data class RelativeRow(
    val ownerProfileId: String,
    val position: Int,
    val targetId: String,
    val relationship: String,
    val given: String,
    val surname: String,
    val birthYear: Int,
    val deathYear: Int?,
) {
    fun toModel() = Relative(targetId, relationship, PersonName(given, surname), birthYear, deathYear)
}

data class SavedList(
    @Embedded val snapshot: ListSnapshotRow,
    @Relation(parentColumn = "id", entityColumn = "snapshotId") val people: List<SummaryRow>,
) {
    fun toModel() = PeopleSnapshot(
        people.sortedBy { it.position }.map { it.person.toModel(it.id) },
        snapshot.serviceUpdated, snapshot.retrievedAt, snapshot.discardedRecordCount,
    )
}

data class SavedProfile(
    @Embedded val profile: ProfileRow,
    @Relation(parentColumn = "id", entityColumn = "ownerProfileId") val relatives: List<RelativeRow>,
) {
    fun toModel() = PersonProfile(
        profile.person.toModel(profile.id), profile.occupation, profile.biography,
        relatives.sortedBy { it.position }.map(RelativeRow::toModel),
        profile.lastModified, profile.retrievedAt,
    )
}

@Dao
interface PeopleDao {
    @Transaction @Query("SELECT * FROM list_snapshots WHERE id = 1")
    fun observePeople(): Flow<SavedList?>
    @Transaction @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeProfile(id: String): Flow<SavedProfile?>
    @Upsert suspend fun putSnapshot(snapshot: ListSnapshotRow)
    @Query("DELETE FROM person_summaries") suspend fun clearSummaries()
    @Insert suspend fun putSummaries(people: List<SummaryRow>)
    @Upsert suspend fun putProfile(profile: ProfileRow)
    @Query("DELETE FROM relatives WHERE ownerProfileId = :id") suspend fun clearRelatives(id: String)
    @Insert suspend fun putRelatives(relatives: List<RelativeRow>)
}

@Database(entities = [ListSnapshotRow::class, SummaryRow::class, ProfileRow::class, RelativeRow::class],
    version = 1, exportSchema = true)
abstract class PeopleDatabase : RoomDatabase() {
    abstract fun peopleDao(): PeopleDao
}
