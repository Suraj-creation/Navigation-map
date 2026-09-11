package com.example.data.places

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.domain.model.Destination
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GooglePlacesDataSource(private val context: Context) {

    private val TAG = "GooglePlacesDataSource"
    private var placesClient: PlacesClient? = null

    init {
        try {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isNotEmpty() && !apiKey.contains("MOCK") && !Places.isInitialized()) {
                Places.initialize(context.applicationContext, apiKey)
                placesClient = Places.createClient(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Places SDK not initialized (using offline/demo catalog): ${e.message}")
        }
    }

    suspend fun autocomplete(query: String): List<Destination> {
        val client = placesClient ?: return emptyList()
        if (query.trim().length < 2) return emptyList()

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    val list = response.autocompletePredictions.map { prediction ->
                        Destination(
                            id = prediction.placeId,
                            placeId = prediction.placeId,
                            name = prediction.getPrimaryText(null).toString(),
                            latitude = 26.9688, // Default anchor until details query
                            longitude = 94.2205,
                            category = "Place",
                            address = prediction.getSecondaryText(null).toString()
                        )
                    }
                    continuation.resume(list)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Autocomplete error: ${e.message}")
                    continuation.resume(emptyList())
                }
        }
    }
}
