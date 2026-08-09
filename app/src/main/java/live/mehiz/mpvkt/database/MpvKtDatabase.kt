package live.mehiz.mpvkt.database

import androidx.room.Database
import androidx.room.RoomDatabase
import live.mehiz.mpvkt.database.dao.CustomButtonDao
import live.mehiz.mpvkt.database.dao.PlaybackHistoryDao
import live.mehiz.mpvkt.database.dao.PlaybackStateDao
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity

@Database(
  entities = [PlaybackStateEntity::class, CustomButtonEntity::class, PlaybackHistoryEntity::class],
  version = 6,
)
abstract class MpvKtDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao
  abstract fun customButtonDao(): CustomButtonDao
  abstract fun playbackHistoryDao(): PlaybackHistoryDao
}
