package pl.prodevcode.tvairplay.data.receiver

import androidx.media3.common.text.Cue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import pl.prodevcode.tvairplay.platform.SubtitleCues

@Singleton
class ServiceSubtitleCues @Inject constructor(private val connector: AirPlayServiceConnector) : SubtitleCues {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val cues: Flow<List<Cue>> = connector.service.flatMapLatest { it?.video?.cues ?: flowOf(emptyList()) }
}
