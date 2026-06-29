package com.campuscue.data.repository

class IcloudServerException(
    val statusCode: Int? = null,
    override val message: String = "iCloudEMS server error",
) : Exception(message)
