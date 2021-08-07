package com.birbit.composecity.data

interface Blackboard {
    fun <T> put(key: Key<T>, t: T)
    fun <T> get(key: Key<T>): T?
    interface Key<T> {
        fun get(blackboard: Blackboard) :T? = blackboard.get(this)
        fun put(blackboard: Blackboard, value: T) {
            blackboard.put(this, value)
        }
    }
}

class BlackboardImpl : Blackboard {
    private val data = mutableMapOf<Blackboard.Key<*>, Any?>()
    override fun <T> put(key: Blackboard.Key<T>, t: T) {
        data[key] = t
    }

    override fun <T> get(key: Blackboard.Key<T>) = data[key] as? T


}