package com.birbit.composecity.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

class FairSharedQueue<T, R> {
    private val queue = ArrayDeque<Entry>()
    private val agents = mutableListOf<Agent>()
    suspend fun register(
        initialData: T,
        doLoop: (SharedQueueScope<T, R>, data: T) -> Unit
    ) : R? {
        val agent = Agent(
            doLoop = doLoop
        )
        agents.add(agent)
        enqueue(agent, initialData)
        return agent.result.await()
    }

    suspend fun execute() {
        while (queue.isNotEmpty()) {
            val entry = queue.removeFirst()
            entry.agent.doEntry(entry.data)
        }
        agents.forEach {
            it.forceEndIfNecessary()
        }
    }

    private fun enqueue(
        agent: Agent,
        data: T
    ) {
        queue.addLast(
            Entry(
                agent = agent,
                data = data
            )
        )
    }

    interface SharedQueueScope<T, R> {
        fun finish(r: R)
        fun enqueue(t : T)
    }

    private inner class Entry(
        val agent: Agent,
        val data: T
    )
    private inner class Agent(
        private val doLoop: (SharedQueueScope<T, R>, T) -> Unit
    ) {
        val result = CompletableDeferred<R?>()
        val scope = object: SharedQueueScope<T, R> {
            override fun finish(r:R) {
                result.complete(r)
            }
            override fun enqueue(t: T) {
                enqueue(this@Agent, t)
            }
        }

        fun forceEndIfNecessary() {
            if (result.isActive) {
                result.complete(null)
            }
        }

        fun doEntry(data: T) {
            if (result.isActive) {
                doLoop(scope, data)
            }
        }

    }
}