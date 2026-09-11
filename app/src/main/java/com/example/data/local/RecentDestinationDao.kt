package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDestinationDao {
    @Query("SELECT * FROM recent_destinations ORDER BY timestamp DESC LIMIT 10")
    fun getRecentDestinations(): Flow<List<RecentDestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(destination: RecentDestinationEntity)

    @Query("DELETE FROM recent_destinations")
    suspend fun clearRecents()
}
