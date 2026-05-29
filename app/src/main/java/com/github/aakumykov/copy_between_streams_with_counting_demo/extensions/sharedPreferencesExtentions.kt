package com.github.aakumykov.copy_between_streams_with_counting_demo.extensions

import android.app.Activity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

// String
fun Activity.storeStringInPreferences(key: String, value: String?) {
    PreferenceManager.getDefaultSharedPreferences(this).edit(commit = true) {
        putString(key, value)
    }
}

fun Activity.getStringFromPreferences(key: String): String? {
    return PreferenceManager.getDefaultSharedPreferences(this)
        .getString(key, null)
}

fun Activity.eraseStringFromPreferences(key: String) {
    PreferenceManager.getDefaultSharedPreferences(this).edit(commit = true) {
        remove(key)
    }
}

// Integer
fun Activity.storeIntInPreferences(key: String, value: Int) {
    PreferenceManager.getDefaultSharedPreferences(this).edit(commit = true) {
        putInt(key, value)
    }
}

fun Activity.getIntFromPreferences(key: String, defaultValue: Int): Int {
    return PreferenceManager.getDefaultSharedPreferences(this)
        .getInt(key, defaultValue)
}

fun Activity.eraseIntFromPreferences(key: String) {
    PreferenceManager.getDefaultSharedPreferences(this).edit(commit = true) {
        remove(key)
    }
}


// String
fun Fragment.storeStringInPreferences(key: String, value: String?) {
    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true) {
        putString(key, value)
    }
}

fun Fragment.getStringFromPreferences(key: String): String? {
    return PreferenceManager.getDefaultSharedPreferences(requireContext())
        .getString(key, null)
}

fun Fragment.eraseStringFromPreferences(key: String) {
    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true) {
        remove(key)
    }
}


// Integer
fun Fragment.storeIntInPreferences(key: String, value: Int) {
    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true) {
        putInt(key, value)
    }
}

fun Fragment.getIntFromPreferences(key: String, defaultValue: Int): Int {
    return PreferenceManager.getDefaultSharedPreferences(requireContext())
        .getInt(key, defaultValue)
}

fun Fragment.eraseIntFromPreferences(key: String) {
    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true) {
        remove(key)
    }
}