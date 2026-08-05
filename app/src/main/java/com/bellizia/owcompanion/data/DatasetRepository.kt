package com.bellizia.owcompanion.data

import android.content.Context
import com.bellizia.owcompanion.sim.WeaponSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the bundled weapon dataset.
 *
 * Today this is the 2020 reference set, which is also what the engine's golden tests run
 * against. The 2026 dataset built by `tools/` replaces it in place, and the online refresh
 * (writing a newer file into `filesDir`) hooks in here.
 */
class DatasetRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: WeaponSet? = null

    suspend fun weapons(): WeaponSet = cached ?: withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        json.decodeFromString(WeaponSet.serializer(), text).also { cached = it }
    }

    private companion object {
        const val ASSET_NAME = "weapons.json"
    }
}
