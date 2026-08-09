package live.mehiz.mpvkt.database.repository

import kotlinx.coroutines.flow.Flow
import live.mehiz.mpvkt.database.MpvKtDatabase
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity
import live.mehiz.mpvkt.domain.playbackhistory.repository.PlaybackHistoryRepository

class PlaybackHistoryRepositoryImpl(
  private val database: MpvKtDatabase
) : PlaybackHistoryRepository {

  override suspend fun upsert(entity: PlaybackHistoryEntity) {
    database.playbackHistoryDao().upsert(entity)
  }

  override fun observeAll(): Flow<List<PlaybackHistoryEntity>> {
    return database.playbackHistoryDao().observeAll()
  }

  override fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>> {
    return database.playbackHistoryDao().observeRecent(limit)
  }

  override suspend fun deleteByUri(uri: String) {
    database.playbackHistoryDao().deleteByUri(uri)
  }

  override suspend fun clearAll() {
    database.playbackHistoryDao().clearAll()
  }

  override suspend fun pruneOldest(keepCount: Int) {
    val all = database.playbackHistoryDao().getAll()
    if (all.size <= keepCount) return
    val cutoff = all[keepCount - 1].lastPlayedAt
    database.playbackHistoryDao().deleteOlderThan(cutoff)
  }
}