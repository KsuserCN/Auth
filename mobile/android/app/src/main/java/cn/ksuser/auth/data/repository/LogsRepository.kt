package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.data.model.PaginatedSensitiveLogs
import com.google.gson.Gson

class LogsRepository(
    private val api: KsuserApiService,
    private val gson: Gson,
) {
    suspend fun getSensitiveLogs(
        page: Int = 1,
        pageSize: Int = 20,
        startDate: String? = null,
        endDate: String? = null,
        operationType: String? = null,
        result: String? = null,
    ): PaginatedSensitiveLogs {
        val envelope = executeEnvelope(gson) {
            api.getSensitiveLogs(page, pageSize, startDate, endDate, operationType, result)
        }
        requireCode(envelope, 200)
        return envelope.data ?: PaginatedSensitiveLogs(emptyList(), page, pageSize, 0, 0)
    }
}
