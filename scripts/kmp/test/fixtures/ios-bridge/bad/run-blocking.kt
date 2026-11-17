package com.example.ios

import kotlinx.coroutines.runBlocking

class WaitsForKotlin {
    @Throws(Exception::class)
    fun wait(): Int = runBlocking { 1 }
}
