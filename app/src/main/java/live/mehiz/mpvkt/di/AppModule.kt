package live.mehiz.mpvkt.di

import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.ui.home.NowPlayingHolder
import org.koin.dsl.module

// generic dependencies for the app's needs
val AppModule = module {
  single {
    Json {
      isLenient = true
      ignoreUnknownKeys = true
    }
  }
  // Cross-screen shared state: PlayerActivity writes, HomeScreen reads.
  // See NowPlayingHolder for usage details.
  single { NowPlayingHolder() }
}
