package com.example.arwalking

import android.content.Context

fun loadJSONFromAsset(context: Context, filename: String): String {
    return context.assets.open(filename).bufferedReader().use { it.readText() }
}