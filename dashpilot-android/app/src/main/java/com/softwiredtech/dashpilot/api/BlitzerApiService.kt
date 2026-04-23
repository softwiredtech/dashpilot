package com.softwiredtech.dashpilot.api

import com.softwiredtech.dashpilot.datamodel.api.BlitzerResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface BlitzerApiService {
    @GET("pois.php")
    suspend fun getPois(
        @Query("type") type: String,
        @Query("box") box: String,
        @Query("z") zoom: Int = 18
    ): BlitzerResponse
}
