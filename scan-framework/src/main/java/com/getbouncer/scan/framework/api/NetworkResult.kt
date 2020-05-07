package com.getbouncer.scan.framework.api

sealed class NetworkResult<Success, Error>(open val responseCode: Int) {
    data class Success<Success, Error>(override val responseCode: Int, val body: Success) : NetworkResult<Success, Error>(responseCode)
    data class Error<Success, Error>(override val responseCode: Int, val error: Error) : NetworkResult<Success, Error>(responseCode)
    data class Exception<Success, Error>(override val responseCode: Int, val exception: Throwable) :
        NetworkResult<Success, Error>(responseCode)
}
