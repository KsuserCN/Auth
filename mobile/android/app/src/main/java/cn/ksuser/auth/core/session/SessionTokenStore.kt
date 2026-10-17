package cn.ksuser.auth.core.session

import cn.ksuser.auth.data.model.UserProfile

interface SessionTokenStore {
    fun getAccessToken(): String?
    fun setAccessToken(token: String)
    fun getProfile(): UserProfile?
    fun setProfile(profile: UserProfile)
    fun clear()
}

class MemorySessionStorage : SessionTokenStore {
    private var token: String? = null
    private var profile: UserProfile? = null
    @Synchronized override fun getAccessToken() = token
    @Synchronized override fun setAccessToken(token: String) { this.token = token }
    @Synchronized override fun getProfile() = profile
    @Synchronized override fun setProfile(profile: UserProfile) { this.profile = profile }
    @Synchronized override fun clear() { token = null; profile = null }
}
