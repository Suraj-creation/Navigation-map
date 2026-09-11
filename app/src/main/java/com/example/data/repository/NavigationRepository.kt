package com.example.data.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface NavigationRepository {
    suspend fun getCuratedDestinations(): List<Destination>
    suspend fun searchDestinations(query: String): List<Destination>
    suspend fun getWalkingRoute(origin: LatLngPoint, destination: Destination): NavigationRoute
    suspend fun getRecalculatedRoute(currentLocation: LatLngPoint, destination: Destination): NavigationRoute
    fun getRecentDestinations(): Flow<List<Destination>>
    suspend fun saveRecentDestination(destination: Destination)
}
