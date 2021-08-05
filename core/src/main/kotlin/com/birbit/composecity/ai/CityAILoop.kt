@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.birbit.composecity.ai

import com.birbit.composecity.Id
import com.birbit.composecity.data.*
import com.birbit.composecity.data.snapshot.CitySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime


private val aiRand = Random(System.nanoTime())

private fun <R> randomTile(
    gameLoop: GameLoop,
    city: City,
    tryExecutor: (tile: Tile) -> R?
): R? {
    val now = gameLoop.gameTime.now.value
    // expand from the left top
    val maxRange = 5 + now.inWholeHours.toInt()
    repeat(10) {
        val row = aiRand.nextInt(minOf(maxRange + it, city.map.height))
        val col = aiRand.nextInt(minOf(maxRange + it, city.map.width))
        val tile = city.map.tiles.get(row = row, col = col)
        tryExecutor(tile)?.let {
            return it
        }
    }
    return null
}

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
        return randomTile(gameLoop, city) {
            if (city.addBusiness(it)) {
                gameLoop.gameTime.now.value
            } else {
                null
            }
        }
    }
}

private class SetPassengerMoodIfNotOnCar(
    private val passengerId: Id,
    private val mood: Passenger.Mood
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val passenger = city.passengers.value.firstOrNull {
            it.id == passengerId
        } ?: return
        if (passenger.car.value != null) {
            return
        }
        passenger.setMood(mood)
    }
}

private class AddPassengerEvent: AIEventWithResult<Duration?>() {
    override fun doApply(gameLoop: GameLoop, city: City): Duration? {
        val businessTiles = city.businessTiles
        if(businessTiles.isEmpty()) return null
        val targetBusiness = businessTiles[aiRand.nextInt(businessTiles.size)]
        return randomTile(gameLoop, city) { tile ->
            if (tile.content.value != TileContent.Business) {
                city.addPassenger(
                    Passenger(
                        id = city.idGenerator.nextId(),
                        pos = tile.center,
                        target = targetBusiness,
                        creationTime = gameLoop.gameTime.now.value
                    )
                )
                gameLoop.gameTime.now.value
            } else {
                null
            }
        }
    }
}

private class RemoveUpsetPassenger(
    val passengerId: Id
): Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val passenger = city.passengers.value.firstOrNull {
            it.id == passengerId && it.car.value == null
        } ?: return
        gameLoop.player.onMissedPassenger(passenger)
        city.removeUnpickedPassenger(passenger)
    }

}

// TODO gotta save what this did as well so that between load / save, it won't go bananas
class CityAILoop(
    lastAddedPassengerTime: Duration = Duration.ZERO,
    lastAddedBusinessTime: Duration = Duration.ZERO,
    private val config: Config = Config()
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
        val passengerEvents = handlePassengerMoods(citySnapshot)
        val leavingPassengers = handlePassengerLeave(citySnapshot)
        return CompositeEvent(
            listOfNotNull(businessEvent, passengerEvent, passengerEvents, leavingPassengers)
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

    private fun handlePassengerLeave(citySnapshot: CitySnapshot): Event? {
        val now = citySnapshot.now
        val events = citySnapshot.availablePassengers.mapNotNull { passenger ->
            val waitTime = now - passenger.creationTime
            if (waitTime > config.maxPassengerWaitDuration) {
                RemoveUpsetPassenger(passengerId = passenger.id)
            } else {
                null
            }
        }
        return if (events.isEmpty()) {
            null
        } else {
            CompositeEvent(events)
        }
    }

    private fun handlePassengerMoods(citySnapshot: CitySnapshot): Event? {
        val now = citySnapshot.now
        val events = citySnapshot.availablePassengers.mapNotNull { passenger ->
            val expectedMood = config.expectedPassengerMood(
                now - passenger.creationTime
            )
            if (passenger.mood != expectedMood) {
                SetPassengerMoodIfNotOnCar(
                    passengerId = passenger.id,
                    mood = expectedMood
                )
            } else {
                null
            }
        }
        return if (events.isEmpty()) {
            null
        } else {
            CompositeEvent(events)
        }
    }

    class Config(
        val minTimeBetweenNewPassengers: Duration = Duration.hours(1),
        val maxTimeBetweenNewPassengers: Duration = Duration.hours(2),

        val minTimeBetweenNewBusinesses: Duration = Duration.hours(8),
        val maxTimeBetweenNewBusinesses: Duration = Duration.days(1),

        val maxPassengerWaitDuration: Duration = Duration.hours(6),

        val expectedPassengerMood : (waitingTime: Duration) -> Passenger.Mood = { waitingTime ->
            when {
                waitingTime < Duration.minutes(45) -> Passenger.Mood.NEW
                waitingTime < Duration.hours(2) -> Passenger.Mood.OK
                waitingTime < Duration.hours(4) -> Passenger.Mood.GETTING_UPSET
                else -> Passenger.Mood.UPSET
            }
        }
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
            val shouldCreate = lastAction == Duration.ZERO || rand.nextLong(
                from = min,
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