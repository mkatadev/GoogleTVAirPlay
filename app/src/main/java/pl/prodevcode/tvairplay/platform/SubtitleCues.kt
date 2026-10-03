package pl.prodevcode.tvairplay.platform

import androidx.media3.common.text.Cue
import kotlinx.coroutines.flow.Flow

/** Android/Media3-specific port: live subtitle cues of the AirPlay video, rendered by the UI. */
interface SubtitleCues {
    val cues: Flow<List<Cue>>
}
