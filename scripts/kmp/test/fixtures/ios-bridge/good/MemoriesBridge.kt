package com.example.ios

// runBlocking, value class and Result<T> only appear in comments here, so they must not trip the checker.
/**
 * fun neverChecked(a: Int = 1)
 */
interface MemoriesObserver { fun onChanged(items: List<String>) } // no-throw: callback into Swift

fun interface Tick { fun call(x: Int) } // no-throw: callback into Swift

class MemoriesBridge @Throws(Exception::class) constructor(private val repo: Repo, val limit: Int) {
    @Throws(Exception::class)
    fun observeMemories(observer: MemoriesObserver): Subscription = TODO()

    @Throws(Exception::class) fun load(id: String): String = ""

    @HiddenFromObjC
    fun kotlinOnly(a: Int): Int = a

    fun ping(): Int = 1 // no-throw: plain getter

    fun named(): Int = compute(x = 1) // no-throw: named argument in an expression body is not a default value

    fun same(a: Int, b: Int): Boolean { // no-throw: pure comparison
        return a == b && a >= 0 && a <= b
    }

    fun multi( // no-throw: plain parameters
        a: Int,
        b: String,
    ): Int = a

    private fun helper(x: Int = 3): Int = x

    internal fun other(x: Int = 3) = x

    override fun toString(): String = "bridge"
}

class SecondCtor {
    @Throws(Exception::class)
    constructor(a: Int)

    private constructor(a: Int = 1, b: Int = 2)
}

@HiddenFromObjC
class HiddenThing(val x: Int = 1)

private class PrivateThing(val x: Int = 1)

class NoCtor

object Holder {
    @Throws(Exception::class)
    fun make(): MemoriesBridge = TODO()
}
