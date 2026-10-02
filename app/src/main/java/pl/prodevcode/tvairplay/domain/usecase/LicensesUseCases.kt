package pl.prodevcode.tvairplay.domain.usecase

import javax.inject.Inject
import pl.prodevcode.tvairplay.domain.repository.LicensesRepository

class GetOpenSourceComponentsUseCase @Inject constructor(private val repo: LicensesRepository) {
    operator fun invoke() = repo.components()
}

class GetLicenseTextUseCase @Inject constructor(private val repo: LicensesRepository) {
    suspend operator fun invoke(asset: String) = repo.licenseText(asset)
}
