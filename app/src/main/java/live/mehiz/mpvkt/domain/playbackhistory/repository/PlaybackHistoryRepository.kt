package live.mehiz.mpvkt.domain.playbackhistory.repository

import kotlinx.coroutines.flow.Flow
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity

interface PlaybackHistoryRepository {

  suspend fun upsert(entity: PlaybackHistoryEntity)

  fun observeAll(): Flow<List<PlaybackHistoryEntity>>

  fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

  suspend fun deleteByUri(uri: String)

  suspend fun clearAll()

  suspend fun pruneOldest(keepCount: Int)
}