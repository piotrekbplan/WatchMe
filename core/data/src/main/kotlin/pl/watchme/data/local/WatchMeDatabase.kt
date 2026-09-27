package pl.watchme.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import javax.inject.Inject

@Database(
    entities = [ProgrammeEntity::class, GuideSyncEntity::class, LineupEntity::class, CatalogCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WatchMeDatabase : RoomDatabase() {
    abstract fun programmeDao(): ProgrammeDao
    abstract fun guideSyncDao(): GuideSyncDao
    abstract fun lineupDao(): LineupDao
    abstract fun catalogDao(): CatalogDao
}

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(private val database: WatchMeDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
}
