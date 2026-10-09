package com.besklar.relatives

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.besklar.relatives.data.PersonRepository
import com.besklar.relatives.data.PortraitStore
import com.besklar.relatives.data.local.PeopleDatabase
import com.besklar.relatives.data.remote.recordsService
import com.besklar.relatives.ui.SavedPortraitLoader
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class RelativesApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val database = Room.databaseBuilder(context.applicationContext,
        PeopleDatabase::class.java, "relatives.db").build()
    val repository = PersonRepository(database, recordsService(client))
    val portraits = PortraitStore(File(context.filesDir, "portraits"), client)
    val portraitLoader = SavedPortraitLoader(portraits)
}
