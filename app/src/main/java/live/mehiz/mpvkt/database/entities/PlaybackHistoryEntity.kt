package live.mehiz.mpvkt.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackHistoryEntity(
  @PrimaryKey val uri: String,
  val displayName: String,
  val lastPlayedAt: Long,
  val duration: Int,
)