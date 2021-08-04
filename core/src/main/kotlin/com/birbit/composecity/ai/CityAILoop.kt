@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.birbit.composecity.ai

import com.birbit.composecity.data.*
import com.birbit.composecity.data.snapshot.CitySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime


private val aiRand = Random(System.nanoTime())

private abstract class AIEventWithResult<T> : Event {
    private val _result: CompletableDeferred<T> = CompletableDeferred()
    val result: Deferred<T>
        get() = _result
    final override fun apply(gameLoop: GameLoop, city: City) {
        _result.complete(doApply(gameLoop, city))
    }
    abstract fun doApply(gameLoop: GameLoop, city: City): T
}

private class AddBusinessEvent: AIEventWithResult<Duration?>() {
    override fun doApply(gameLoop: GameLoop, city: City): Duration? {
        repeat(10) {
            val row = aiRand.nextInt(city.map.height)
            val col = aiRand.nextInt(city.map.width)
            val tile = city.map.tiles.get(row = row, col = col)
            if (tile.contentValue == TileContent.Grass) {
                // TODO businesses should be cached in City
                tile.contentValue = TileContent.Business
                return gameLoop.gameTime.now.value
            }
        }
        return null
    }
}

private class AddPassengerEvent: AIEventWithResult<Duration?>() {
    override fun doApply(gameLoop: GameLoop, city: City): Duration? {
        val businessTiles = city.map.tiles.data.filter {
            it.contentValue == TileContent.Business
        }
        if(businessTiles.isEmpty()) return null
        val targetBusiness = businessTiles[aiRand.nextInt(businessTiles.size)]
        repeat(10) {
            val row = aiRand.nextInt(city.map.height)
            val col = aiRand.nextInt(city.map.width)
            val tile = city.map.tiles.get(row = row, col = col)
            if (tile.contentValue != TileContent.Business) {
                city.addPassenger(
                    Passenger(
                        id = city.idGenerator.nextId(),
                        pos = tile.center,
                        target = targetBusiness
                    )
                )
                return gameLoop.gameTime.now.value
            }
        }
        return null
    }

}

// TODO gotta save what this did as well so that between load / save, it won't go bananas
class CityAILoop(
    lastAddedPassengerTime: Duration = Duration.ZERO,
    lastAddedBusinessTime: Duration = Duration.ZERO,
    config: Config = Config()
) {
    private val addBusinessConstraint = DurationConstraint(
        min = config.minTimeBetweenNewBusinesses,
        max = config.maxTimeBetweenNewBusinesses,
        lastAction = lastAddedBusinessTime
    )
    private val addPassengerConstraint = DurationConstraint(
        min = config.minTimeBetweenNewPassengers,
        max = config.maxTimeBetweenNewPassengers,
        lastAction = lastAddedPassengerTime
    )
    internal fun doAILoop(
        citySnapshot: CitySnapshot
    ): Event {
        val businessEvent = handleBusinessCreation(citySnapshot)
        val passengerEvent = handlePassengerCreation(citySnapshot)
        return CompositeEvent(
            listOfNotNull(businessEvent, passengerEvent)
        )
    }

    private fun handlePassengerCreation(citySnapshot: CitySnapshot): Event? {
        return addPassengerConstraint.considerExecution(
            now = citySnapshot.now
        ) {
            AddPassengerEvent()
        }
    }
    private fun handleBusinessCreation(citySnapshot: CitySnapshot): Event? {
        return addBusinessConstraint.considerExecution(
            now = citySnapshot.now
        ) {
            AddBusinessEvent()
        }
    }

    class Config(
        val minTimeBetweenNewPassengers: Duration = Duration.hours(1),
        val maxTimeBetweenNewPassengers: Duration = Duration.hours(2),

        val minTimeBetweenNewBusinesses: Duration = Duration.days(1),
        val maxTimeBetweenNewBusinesses: Duration = Duration.days(2)
    )

    private class DurationConstraint(
        min: Duration,
        max: Duration,
        private var lastAction: Duration = Duration.ZERO
    ) {
        private val rand = aiRand
        val min = min.inWholeMinutes
        val max = max.inWholeMinutes
        var pendingEvent: AIEventWithResult<Duration?>? = null
        fun considerExecution(now: Duration, create : () -> AIEventWithResult<Duration?>): Event? {
            val pending = pendingEvent
            if (pending != null) {
                // TODO we could instead await here but that might block other AI stuff so better skip
                if (pending.result.isActive) {
                    return null
                }
                val completionTime = pending.result.getCompleted()
                if (completionTime != null) {
                    lastAction = completionTime
                }
                pendingEvent = null
            }
            val durationSinceLastAction = (now - lastAction).inWholeMinutes
            val shouldCreate = rand.nextLong(
                from = if (lastAction == Duration.ZERO) 0 else min,
                until = max
            ) <= durationSinceLastAction
            if (!shouldCreate) {
                return null
            }
            val event = create()
            pendingEvent = event
            return event
        }
    }
}