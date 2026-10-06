package com.example.dopaminecut2.data.remote

class RetryableRemoteWriteException(cause: Throwable) :
    Exception(cause.message, cause)
