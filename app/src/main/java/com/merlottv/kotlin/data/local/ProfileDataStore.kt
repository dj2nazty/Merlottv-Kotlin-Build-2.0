package com.merlottv.kotlin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profiles")

data class UserProfile(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val colorIndex: Int = 0,
    val avatarUrl: String = "", // Empty = use color+letter, non-empty = show image
    val isDefault: Boolean = false
)

class ProfileDataStore(private val context: Context) {

    companion object {
        val PROFILES = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        val AVATAR_COLORS = listOf(
            0xFF00E5FF.toInt(), // Cyan (MerlotTV accent)
            0xFFFF5252.toInt(), // Red
            0xFF69F0AE.toInt(), // Green
            0xFFFFD740.toInt(), // Amber
            0xFFE040FB.toInt(), // Purple
            0xFF448AFF.toInt(), // Blue
            0xFFFF6E40.toInt(), // Deep Orange
            0xFF64FFDA.toInt()  // Teal
        )

        /** 25 pre-selected avatar images using DiceBear API — themed after popular animated characters */
        val AVATAR_IMAGES = listOf(
            // Family Guy themed
            "https://api.dicebear.com/7.x/adventurer/png?seed=Peter&size=128&backgroundColor=b6e3f4",
            "https://api.dicebear.com/7.x/adventurer/png?seed=Stewie&size=128&backgroundColor=ffd5dc",
            "https://api.dicebear.com/7.x/adventurer/png?seed=Brian&size=128&backgroundColor=c0aede",
            "https://api.dicebear.com/7.x/adventurer/png?seed=Quagmire&size=128&backgroundColor=d1d4f9",
            "https://api.dicebear.com/7.x/adventurer/png?seed=Lois&size=128&backgroundColor=ffdfbf",
            // Simpsons themed
            "https://api.dicebear.com/7.x/fun-emoji/png?seed=Homer&size=128&backgroundColor=ffd93d",
            "https://api.dicebear.com/7.x/fun-emoji/png?seed=Bart&size=128&backgroundColor=ff6b6b",
            "https://api.dicebear.com/7.x/fun-emoji/png?seed=Lisa&size=128&backgroundColor=c0eb75",
            "https://api.dicebear.com/7.x/fun-emoji/png?seed=Marge&size=128&backgroundColor=74b9ff",
            "https://api.dicebear.com/7.x/fun-emoji/png?seed=Krusty&size=128&backgroundColor=fdcb6e",
            // Star Wars themed
            "https://api.dicebear.com/7.x/bottts/png?seed=DarthVader&size=128&backgroundColor=2d3436",
            "https://api.dicebear.com/7.x/bottts/png?seed=Yoda&size=128&backgroundColor=00b894",
            "https://api.dicebear.com/7.x/bottts/png?seed=BobaFett&size=128&backgroundColor=636e72",
            "https://api.dicebear.com/7.x/bottts/png?seed=Stormtrooper&size=128&backgroundColor=dfe6e9",
            "https://api.dicebear.com/7.x/bottts/png?seed=R2D2&size=128&backgroundColor=0984e3",
            // Disney themed
            "https://api.dicebear.com/7.x/pixel-art/png?seed=Mickey&size=128&backgroundColor=e17055",
            "https://api.dicebear.com/7.x/pixel-art/png?seed=Donald&size=128&backgroundColor=74b9ff",
            "https://api.dicebear.com/7.x/pixel-art/png?seed=Goofy&size=128&backgroundColor=fdcb6e",
            "https://api.dicebear.com/7.x/pixel-art/png?seed=BuzzLightyear&size=128&backgroundColor=a29bfe",
            "https://api.dicebear.com/7.x/pixel-art/png?seed=Stitch&size=128&backgroundColor=81ecec",
            // Other popular animated
            "https://api.dicebear.com/7.x/adventurer/png?seed=SpongeBob&size=128&backgroundColor=ffeaa7",
            "https://api.dicebear.com/7.x/adventurer/png?seed=RickSanchez&size=128&backgroundColor=55efc4",
            "https://api.dicebear.com/7.x/adventurer/png?seed=ScoobyDoo&size=128&backgroundColor=fab1a0",
            "https://api.dicebear.com/7.x/bottts/png?seed=Batman&size=128&backgroundColor=2d3436",
            "https://api.dicebear.com/7.x/fun-emoji/png?seed=Pikachu&size=128&backgroundColor=ffeaa7"
        )

        /** Human-readable labels for avatar images */
        val AVATAR_LABELS = listOf(
            "Peter", "Stewie", "Brian", "Quagmire", "Lois",
            "Homer", "Bart", "Lisa", "Marge", "Krusty",
            "Vader", "Yoda", "Boba Fett", "Trooper", "R2-D2",
            "Mickey", "Donald", "Goofy", "Buzz", "Stitch",
            "SpongeBob", "Rick", "Scooby", "Batman", "Pikachu"
        )

        private const val DEFAULT_PROFILE_ID = "default"
    }

    val profiles: Flow<List<UserProfile>> = context.profileDataStore.data.map { prefs ->
        val json = prefs[PROFILES]
        if (json != null) parseProfilesJson(json)
        else listOf(UserProfile(DEFAULT_PROFILE_ID, "Default", 0, "", true))
    }

    val activeProfileId: Flow<String> = context.profileDataStore.data.map { prefs ->
        prefs[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
    }

    suspend fun getActiveProfileId(): String {
        return context.profileDataStore.data.first()[ACTIVE_PROFILE_ID] ?: DEFAULT_PROFILE_ID
    }

    suspend fun setActiveProfile(profileId: String) {
        context.profileDataStore.edit { it[ACTIVE_PROFILE_ID] = profileId }
    }

    suspend fun addProfile(name: String, colorIndex: Int, avatarUrl: String = ""): UserProfile {
        val currentProfiles = profiles.first().toMutableList()
        if (currentProfiles.size >= 6) throw IllegalStateException("Maximum 6 profiles allowed")
        val profile = UserProfile(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            colorIndex = colorIndex,
            avatarUrl = avatarUrl
        )
        currentProfiles.add(profile)
        saveProfiles(currentProfiles)
        return profile
    }

    suspend fun removeProfile(profileId: String) {
        val currentProfiles = profiles.first().toMutableList()
        currentProfiles.removeAll { it.id == profileId && !it.isDefault }
        saveProfiles(currentProfiles)
        // If removed active profile, switch to default
        val activeId = getActiveProfileId()
        if (activeId == profileId) {
            setActiveProfile(currentProfiles.first().id)
        }
    }

    suspend fun updateProfile(profileId: String, name: String, colorIndex: Int) {
        val currentProfiles = profiles.first().toMutableList()
        val index = currentProfiles.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            currentProfiles[index] = currentProfiles[index].copy(name = name, colorIndex = colorIndex)
            saveProfiles(currentProfiles)
        }
    }

    suspend fun hasSelectedProfile(): Boolean {
        return context.profileDataStore.data.first()[ACTIVE_PROFILE_ID] != null
    }

    private suspend fun saveProfiles(list: List<UserProfile>) {
        val jsonArray = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("colorIndex", p.colorIndex)
            obj.put("avatarUrl", p.avatarUrl)
            obj.put("isDefault", p.isDefault)
            jsonArray.put(obj)
        }
        context.profileDataStore.edit { it[PROFILES] = jsonArray.toString() }
    }

    // ─── Cloud Sync Restore ───
    suspend fun restoreProfiles(profiles: List<UserProfile>) {
        saveProfiles(profiles)
    }

    suspend fun restoreActiveProfileId(id: String) {
        setActiveProfile(id)
    }

    private fun parseProfilesJson(json: String): List<UserProfile> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UserProfile(
                    id = obj.optString("id", "default"),
                    name = obj.optString("name", "Profile"),
                    colorIndex = obj.optInt("colorIndex", 0),
                    avatarUrl = obj.optString("avatarUrl", ""),
                    isDefault = obj.optBoolean("isDefault", false)
                )
            }
        } catch (_: Exception) {
            listOf(UserProfile(DEFAULT_PROFILE_ID, "Default", 0, "", true))
        }
    }
}
