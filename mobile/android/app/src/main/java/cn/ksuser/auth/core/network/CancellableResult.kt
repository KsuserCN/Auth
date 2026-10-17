package cn.ksuser.auth.core.network

import kotlinx.coroutines.CancellationException

/** A screen leaving its account must cancel work, never turn cancellation into a UI failure. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
