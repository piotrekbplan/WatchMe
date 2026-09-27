package pl.watchme.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgrammeDao {

    @Query(
        "SELECT * FROM programme WHERE channelId IN (:channelIds) " +
            "AND stopMillis > :fromMillis AND startMillis < :untilMillis ORDER BY startMillis",
    )
    fun observe(channelIds: List<String>, fromMillis: Long, untilMillis: Long): Flow<List<ProgrammeEntity>>

    @Query("DELETE FROM programme WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programme WHERE stopMillis <= :cutoffMillis")
    suspend fun deleteEndedBefore(cutoffMillis: Long)
}

@Dao
interface GuideSyncDao {

    @Query("SELECT * FROM guide_sync WHERE channelId IN (:channelIds)")
    suspend fun get(channelIds: List<String>): List<GuideSyncEntity>

    @Upsert
    suspend fun upsert(sync: GuideSyncEntity)
}

@Dao
interface LineupDao {

    @Query("SELECT * FROM lineup WHERE id = $SINGLE_ROW_ID")
    fun observe(): Flow<LineupEntity?>

    @Upsert
    suspend fun upsert(lineup: LineupEntity)

    @Query("DELETE FROM lineup")
    suspend fun clear()
}

@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalog_cache WHERE id = $SINGLE_ROW_ID")
    fun observe(): Flow<CatalogCacheEntity?>

    @Query("SELECT * FROM catalog_cache WHERE id = $SINGLE_ROW_ID")
    suspend fun get(): CatalogCacheEntity?

    @Upsert
    suspend fun upsert(cache: CatalogCacheEntity)
}
