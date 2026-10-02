package pl.prodevcode.tvairplay.presentation.components

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoSurface(
    aspectRatio: Float,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val available by rememberUpdatedState(onSurfaceAvailable)
    val destroyed by rememberUpdatedState(onSurfaceDestroyed)
    val callback = remember {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = available(holder.surface)
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                available(holder.surface)
            override fun surfaceDestroyed(holder: SurfaceHolder) = destroyed(holder.surface)
        }
    }
    AndroidView(
        factory = { ctx -> SurfaceView(ctx).also { it.holder.addCallback(callback) } },
        modifier = modifier.aspectRatio(aspectRatio.coerceIn(0.5f, 2.4f)),
    )
}
