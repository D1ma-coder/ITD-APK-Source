package com.itd.app.data.remote

class ApiException(
    val code: String,
    override val message: String,
    val status: Int,
    val retryAfter: Int? = null,
    val attemptsLeft: Int? = null,
) : Exception(message)
