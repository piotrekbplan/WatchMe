package pl.watchme.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programme",
    primaryKeys = ["channelId", "startMillis"],
    indices = [Index("stopMillis")],
)
data class ProgrammeEntity(
    val channelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val kind: String,
    val category: String,
    val year: Int?,
    val imdbId: String?,
    val rating: Double?,
    val votes: Int?,
    val posterUrl: String?,
)

@Entity(tableName = "guide_sync")
data class GuideSyncEntity(
    @PrimaryKey val channelId: String,
    val etag: String?,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "lineup")
data class LineupEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val channelIds: String,
    val operatorId: String?,
    val packageId: String?,
    val updatedAtMillis: Long,
)

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val channelsJson: String,
    val operatorsJson: String,
    val fetchedAtMillis: Long,
)

const val SINGLE_ROW_ID = 0
