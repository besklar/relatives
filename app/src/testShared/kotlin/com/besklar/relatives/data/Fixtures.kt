package com.besklar.relatives.data

object Fixtures {
    fun summary(id: String = "A-1", given: String = "Ada", living: Boolean = false): String = """
        {"id":"$id","name":{"given":"$given","surname":"Whitcomb"},
         "sex":"female","living":$living,
         "birth":{"date":"about 1838","year":1838,"place":"Nauvoo"},
         "death":${if (living) "null" else """{"date":"1903","year":1903,"place":"Ogden"}"""},
         "portraitUrl":"portraits/$id.jpg"}
    """.trimIndent()

    fun people(vararg records: String, count: Int = records.size): String =
        """{"updated":"2026-08-14","count":$count,"persons":[${records.joinToString(",") }]}"""

    fun relative(id: String = "B-2"): String = """
        {"id":"$id","relationship":"father","name":{"given":"Amos","surname":"Whitcomb"},
         "birthYear":1810,"deathYear":1877}
    """.trimIndent()

    fun profile(id: String = "A-1", relatives: String = relative()): String =
        summary(id).trimEnd().dropLast(1) + """,
         "occupation":null,"biography":"A family story.","relatives":[$relatives],
         "sources":[],"lastModified":"2026-08-14T09:21:00Z"}
        """.trimIndent()
}
