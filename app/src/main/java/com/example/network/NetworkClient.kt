package com.example.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object NetworkClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://pvdghgrvxcxpxekvtybx.supabase.co/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val superTaskService: SuperTaskService = retrofit.create(SuperTaskService::class.java)
}
