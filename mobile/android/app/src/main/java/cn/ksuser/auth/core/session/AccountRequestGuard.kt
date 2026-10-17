package cn.ksuser.auth.core.session

/** Only the latest read for the still-current account may publish its result. */
class AccountRequestGuard(private val currentAccount: () -> String?) {
    data class Ticket internal constructor(val accountId: String, internal val sequence: Long)
    private var sequence = 0L

    @Synchronized fun begin(accountId: String): Ticket? {
        if (currentAccount() != accountId) return null
        return Ticket(accountId, ++sequence)
    }

    @Synchronized fun isCurrent(ticket: Ticket): Boolean =
        currentAccount() == ticket.accountId && sequence == ticket.sequence
}
