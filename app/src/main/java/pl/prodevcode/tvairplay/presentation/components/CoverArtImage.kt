package pl.prodevcode.tvairplay.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import pl.prodevcode.tvairplay.domain.model.CoverArt

/** Decodes artwork once per distinct [CoverArt]; the domain only carries the encoded bytes. */
@Composable
fun rememberCoverArt(art: CoverArt?): ImageBitmap? = remember(art) {
    art?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
}
