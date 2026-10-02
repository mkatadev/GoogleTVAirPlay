package pl.prodevcode.tvairplay.domain.repository

import pl.prodevcode.tvairplay.domain.model.OpenSourceComponent

interface LicensesRepository {
    fun components(): List<OpenSourceComponent>
    suspend fun licenseText(asset: String): String
}
