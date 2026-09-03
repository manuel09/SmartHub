package com.smarthub.player.data.api

import com.smarthub.player.data.model.StreamResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StreamViXApi {
    @GET("stream/{type}/{id}.json")
    suspend fun getStreams(
        @Path("type") type: String,
        @Path("id") id: String,
        @Query("title") title: String? = null
    ): StreamResponse
}
