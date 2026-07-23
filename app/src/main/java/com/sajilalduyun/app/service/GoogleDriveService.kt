package com.sajilalduyun.app.service

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

object GoogleDriveService {

    // Your client ID from Google Console
    const val CLIENT_ID = "901959023112-ustg3ndftdjtora54p3n0og4o182557a.apps.googleusercontent.com"

    // Get sign in intent to show Google account picker
    fun getSignInIntent(context: Context): Intent {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        val client = GoogleSignIn.getClient(context, signInOptions)
        return client.signInIntent
    }

    // Check if user is already signed in
    fun isSignedIn(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null
    }

    // Sign out
    fun signOut(context: Context) {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, signInOptions)
        client.signOut()
    }
}