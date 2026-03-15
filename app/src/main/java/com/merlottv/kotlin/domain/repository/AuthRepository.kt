package com.merlottv.kotlin.domain.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>
    val isSignedIn: Flow<Boolean>
    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun signInWithCustomToken(token: String): Result<FirebaseUser>
    suspend fun signOut()
    fun getUserId(): String?
}
