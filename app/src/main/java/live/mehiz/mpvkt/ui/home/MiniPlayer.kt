package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

/**
 * Persistent now-playing strip pinned to the bottom of the HomeScreen.
 *
 * Shows the currently-decoded title / artist, an artwork thumbnail and a play-pause
 * button. Tapping the strip (or the artwork) jumps straight back into
 * [PlayerActivity]; tapping the play button starts / pauses playback without leaving
 * the HomeScreen thanks to the background [live.mehiz.mpvkt.ui.player.MediaPlaybackService].
 *
 * The strip only renders when [NowPlayingHolder.state] has a non-empty uri.
 */
@Composable
fun MiniPlayer(
  modifier: Modifier = Modifier,
  onPlayPause: () -> Unit = {},
  onClose: () -> Unit = {},
) {
  val holder: NowPlayingHolder = koinInject()
  val snapshot by holder.state.collectAsState()
  // Capture context once at composition time so the click lambda stays Compose-free.
  val context: Context = LocalContext.current

  AnimatedVisibility(
    visible = snapshot.hasContent,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    modifier = modifier,
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      tonalElevation = 6.dp,
      shadowElevation = 6.dp,
      shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .padding(horizontal = MaterialTheme.spacing.smaller)
          .clickable {
            val intent = Intent(context, PlayerActivity::class.java).apply {
              action = Intent.ACTION_VIEW
              data = snapshot.uri.toUri()
              flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
          },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
      ) {
        Artwork(snapshot.artwork)
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = snapshot.title.ifBlank { stringResource(R.string.mini_player_unknown_title) },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (snapshot.artist.isNotBlank()) {
            Text(
              text = snapshot.artist,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        IconButton(onClick = onPlayPause) {
          Icon(
            imageVector = if (snapshot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (snapshot.isPlaying) {
              stringResource(R.string.notification_pause)
            } else {
              stringResource(R.string.notification_play)
            },
          )
        }
        IconButton(onClick = onClose) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.mini_player_close),
          )
        }
      }
    }
  }
}

@Composable
private fun Artwork(bitmap: android.graphics.Bitmap?) {
  Box(
    modifier = Modifier
      .size(48.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(48.dp),
      )
    } else {
      Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
