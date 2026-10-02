package pl.prodevcode.tvairplay.domain.model

data class OpenSourceComponent(
    val name: String,
    val license: String,
    val url: String,
    /** Asset file with the full license text, relative to `assets/licenses/`. */
    val licenseAsset: String,
)
