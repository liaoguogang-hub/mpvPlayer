package live.mehiz.mpvkt.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity

@Dao
interface PlaybackHistoryDao {
  @Upsert
  suspend fun upsert(entity: PlaybackHistoryEntity)

  @Query("SELECT * FROM PlaybackHistoryEntity ORDER BY lastPlayedAt DESC")
  fun observeAll(): Flow<List<PlaybackHistoryEntity>>

  @Query("SELECT * FROM PlaybackHistoryEntity ORDER BY lastPlayedAt DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

  @Query("SELECT * FROM PlaybackHistoryEntity ORDER BY lastPlayedAt DESC")
  suspend fun getAll(): List<PlaybackHistoryEntity>

  @Query("DELETE FROM PlaybackHistoryEntity WHERE uri = :uri")
  suspend fun deleteByUri(uri: String)

  @Query("DELETE FROM PlaybackHistoryEntity WHERE lastPlayedAt < :cutoffMs")
  suspend fun deleteOlderThan(cutoffMs: Long)

  @Query("DELETE FROM PlaybackHistoryEntity")
  suspend fun clearAll()
}