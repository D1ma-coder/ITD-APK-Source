package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val data: SearchData,
)
