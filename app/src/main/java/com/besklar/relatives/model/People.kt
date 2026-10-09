package com.besklar.relatives.model

data class PersonName(val given: String, val surname: String) {
    val fullName: String get() = listOf(given, surname).filter(String::isNotBlank).joinToString(" ")
}

data class LifeEvent(val date: String, val year: Int, val place: String)

data class PersonSummary(
    val id: String,
    val name: PersonName,
    val sex: String,
    val living: Boolean,
    val birth: LifeEvent,
    val death: LifeEvent?,
    val portraitUrl: String,
)

data class Relative(
    val id: String,
    val relationship: String,
    val name: PersonName,
    val birthYear: Int,
    val deathYear: Int?,
)

data class PeopleSnapshot(
    val people: List<PersonSummary>,
    val serviceUpdated: String,
    val retrievedAt: Long,
    val discardedRecordCount: Int,
)

data class PersonProfile(
    val person: PersonSummary,
    val occupation: String?,
    val biography: String,
    val relatives: List<Relative>,
    val lastModified: String,
    val retrievedAt: Long,
)
