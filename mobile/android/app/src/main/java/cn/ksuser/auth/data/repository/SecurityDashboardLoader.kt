package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.runCatchingCancellable
import cn.ksuser.auth.data.model.AdaptiveAuthStatus
import cn.ksuser.auth.data.model.PasskeyListItem
import cn.ksuser.auth.data.model.SensitiveVerificationStatus
import cn.ksuser.auth.data.model.TotpStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class SecurityDashboardSnapshot(
    val totp: Result<TotpStatus>,
    val passkeys: Result<List<PasskeyListItem>>,
    val sensitive: Result<SensitiveVerificationStatus>,
    val adaptive: Result<AdaptiveAuthStatus>,
    val qq: Result<Boolean>,
    val apple: Result<Boolean>,
)

class SecurityDashboardLoader(
    private val totp: suspend () -> TotpStatus,
    private val passkeys: suspend () -> List<PasskeyListItem>,
    private val sensitive: suspend () -> SensitiveVerificationStatus,
    private val adaptive: suspend () -> AdaptiveAuthStatus,
    private val qq: suspend () -> Boolean,
    private val apple: suspend () -> Boolean,
) {
    constructor(security: SecurityRepository, accounts: AccountRepository) : this(
        security::getTotpStatus, security::getPasskeyList, security::getSensitiveVerificationStatus,
        security::getAdaptiveAuthStatus, accounts::qqBound, accounts::appleBound,
    )

    suspend fun load(): SecurityDashboardSnapshot = coroutineScope {
        val totpResult = async { runCatchingCancellable { totp() } }
        val passkeyResult = async { runCatchingCancellable { passkeys() } }
        val sensitiveResult = async { runCatchingCancellable { sensitive() } }
        val adaptiveResult = async { runCatchingCancellable { adaptive() } }
        val qqResult = async { runCatchingCancellable { qq() } }
        val appleResult = async { runCatchingCancellable { apple() } }
        SecurityDashboardSnapshot(totpResult.await(), passkeyResult.await(), sensitiveResult.await(),
            adaptiveResult.await(), qqResult.await(), appleResult.await())
    }
}
