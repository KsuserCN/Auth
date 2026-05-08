package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.data.model.SessionItem
import com.google.gson.Gson

class SessionsRepository(
    private val api: KsuserApiService,
    private val gson: Gson,
) {
    suspend fun getSessions(): List<SessionItem> {
        val envelope = executeEnvelope(gson) { api.getSessions() }
        requireCode(envelope, 200)
        return envelope.data.orEmpty()
    }

    suspend fun revokeSession(sessionId: Long) {
        val envelope = executeEnvelope(gson) { api.revokeSession(sessionId) }
        requireCode(envelope, 200)
    }
}
