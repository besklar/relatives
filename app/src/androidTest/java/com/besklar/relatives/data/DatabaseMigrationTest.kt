package com.besklar.relatives.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.besklar.relatives.data.local.PeopleDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PeopleDatabase::class.java)

    @Test fun migrationPreservesListProfileAndRelativeRecords() = runBlocking {
        val name = "migration-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            helper.createDatabase(name, 1).use { db ->
                val columns = "given,surname,sex,living,birthDate,birthYear,birthPlace,deathDate,deathYear,deathPlace,portraitUrl"
                val values = "'Ada','Whitcomb','female',0,'about 1838',1838,'Nauvoo','1903',1903,'Ogden','portraits/A-1.jpg'"
                db.execSQL("INSERT INTO list_snapshots VALUES (1,'2026-08-14',1234,2)")
                db.execSQL("INSERT INTO person_summaries (id,snapshotId,position,$columns) VALUES ('A-1',1,0,$values)")
                db.execSQL("INSERT INTO profiles (id,$columns,occupation,biography,lastModified,retrievedAt) " +
                    "VALUES ('A-1',$values,NULL,'Saved story','2026-08-14',1234)")
                db.execSQL("INSERT INTO relatives VALUES ('A-1',0,'B-2','father','Amos','Whitcomb',1810,1877)")
            }
            helper.runMigrationsAndValidate(name, 2, true, PeopleDatabase.MIGRATION_1_2).close()
            val db = Room.databaseBuilder(context, PeopleDatabase::class.java, name)
                .addMigrations(PeopleDatabase.MIGRATION_1_2).build()
            try {
                    val list = db.peopleDao().observePeople().first()!!.toModel()
                    val profile = db.peopleDao().observeProfile("A-1").first()!!.toModel()
                    assertEquals("Ada Whitcomb", list.people.single().name.fullName)
                    assertEquals(2, list.discardedRecordCount)
                    assertEquals("Saved story", profile.biography)
                    assertEquals("B-2", profile.relatives.single().id)
                    assertEquals(0, profile.discardedRelativeCount)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
