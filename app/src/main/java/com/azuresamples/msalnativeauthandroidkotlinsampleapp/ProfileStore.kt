package com.azuresamples.msalnativeauthandroidkotlinsampleapp

/**
 * In-memory holder for the loyalty profile collected by LoginFragment's "Join Banana Club" step
 * and HomeFragment's driver-details form. Mirrors utils/profileStore.js in the companion web
 * app: this sample has no real backend, so "saving a profile" just means keeping it around for
 * the rest of the process lifetime rather than persisting it.
 */
object ProfileStore {

    data class Profile(
        val firstName: String,
        val lastName: String,
        val preferredName: String = "",
        val dob: String = "",
        val phone: String = ""
    )

    private var profile: Profile? = null

    fun save(profile: Profile) {
        this.profile = profile
    }

    fun get(): Profile? = profile

    fun clear() {
        profile = null
    }
}
