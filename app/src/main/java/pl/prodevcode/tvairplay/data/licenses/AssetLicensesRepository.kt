package pl.prodevcode.tvairplay.data.licenses

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.prodevcode.tvairplay.domain.model.OpenSourceComponent
import pl.prodevcode.tvairplay.domain.repository.LicensesRepository

@Singleton
class AssetLicensesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LicensesRepository {

    override fun components(): List<OpenSourceComponent> = listOf(
        OpenSourceComponent("AirPlay for Google TV (this app)", "GPL-3.0", "https://github.com/mkatadev", "gpl-3.0.txt"),
        OpenSourceComponent("android-airplay-server (jqssun)", "GPL-3.0", "https://github.com/jqssun/android-airplay-server", "gpl-3.0.txt"),
        OpenSourceComponent("UxPlay", "GPL-3.0", "https://github.com/FDH2/UxPlay", "gpl-3.0.txt"),
        OpenSourceComponent("libplist", "LGPL-2.1", "https://github.com/libimobiledevice/libplist", "lgpl-2.1.txt"),
        OpenSourceComponent("FFmpeg (libavcodec, ALAC)", "LGPL-2.1", "https://ffmpeg.org", "lgpl-2.1.txt"),
        OpenSourceComponent("OpenSSL", "Apache-2.0", "https://www.openssl.org", "apache-2.0.txt"),
        OpenSourceComponent("Oboe", "Apache-2.0", "https://github.com/google/oboe", "apache-2.0.txt"),
        OpenSourceComponent("AndroidX, Jetpack Compose, Media3", "Apache-2.0", "https://developer.android.com/jetpack", "apache-2.0.txt"),
        OpenSourceComponent("Dagger Hilt", "Apache-2.0", "https://dagger.dev/hilt", "apache-2.0.txt"),
        OpenSourceComponent("Tink (HomeKit pairing crypto)", "Apache-2.0", "https://github.com/tink-crypto/tink-java", "apache-2.0.txt"),
        OpenSourceComponent("Kotlin & kotlinx.coroutines", "Apache-2.0", "https://kotlinlang.org", "apache-2.0.txt"),
    )

    override suspend fun licenseText(asset: String): String = withContext(Dispatchers.IO) {
        context.assets.open("licenses/$asset").bufferedReader().use { it.readText() }
    }
}
