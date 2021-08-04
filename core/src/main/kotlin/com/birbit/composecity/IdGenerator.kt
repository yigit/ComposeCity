package com.birbit.composecity

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

@JvmInline
@Serializable
value class Id(val value: Int)
class IdGenerator(
    start: Id
) {
    private val counter = AtomicInteger(start.value)
    val lastId
        get() = Id(counter.get())
    fun nextId() = Id(counter.incrementAndGet())

}