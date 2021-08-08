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


// TODO some weighted selection logic here would be nice where businesses like being close to businesses
//  whereas homes like being away from other businesses, slightly closer to other homes though.
private val aiRand = Random(System.nanoTime())

private val VALID_TILES_FOR_NEW_PASSENGERS = listOf(
    TileContent.Road,
    TileContent.Grass
)
private val MAX_PASSENGERS_PER_TILE = 3
private fun <R> randomTile(
    gameLoop: GameLoop,
    city: City,
    tryExecutor: (tile: Tile) -> R?
): R? {
    val now = gameLoop.gameTime.now.value
    // expand from the left top, or not??
    val maxRange = 1000//5 + now.inWholeHours.toInt()
    val map = city.map.value
    repeat(10) {
        val row = aiRand.nextInt(minOf(maxRange + it, map.height))
        val col = aiRand.nextInt(minOf(maxRange + it, map.width))
        val tile = map.tiles.get(row = row, col = col)
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

private class AddHomeEvent: AIEventWithResult<Duration?>() {
    override fun doApply(gameLoop: GameLoop, city: City): Duration? {
        return randomTile(gameLoop, city) {
            if (city.addHouse(it)) {
                gameLoop.gameTime.now.value
            } else {
                null
            }
        }
    }
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

private class ExpandCityEvent(
    val width: Int,
    val height: Int,
): Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        city.expandTo(width, height)
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

private class AddPassengerEvent(
    val row: Int,
    val col: Int,
    val passengersOnThisTile: List<Id>
): AIEventWithResult<Duration?>() {
    override fun doApply(gameLoop: GameLoop, city: City): Duration? {
        val businessTiles = city.businessTiles
        if(businessTiles.isEmpty()) return null
        val targetBusiness = businessTiles[aiRand.nextInt(businessTiles.size)]
        val tile = city.map.value.tiles.get(
            row = row,
            col = col
        )
        if (tile.content.value !in VALID_TILES_FOR_NEW_PASSENGERS) {
            return null
        }
        val newPassenger = Passenger(
            id = city.idGenerator.nextId(),
            pos = tile.center,
            target = targetBusiness,
            creationTime = gameLoop.gameTime.now.value
        )
        city.addPassenger(
            newPassenger
        )
        val allPassengers = passengersOnThisTile.mapNotNull {  id ->
            city.passengers.value.firstOrNull {
                it.id == id && it.car.value == null
            }
        } + newPassenger
        // reposition them to avoid overlaps
        val increment = CityMap.TILE_SIZE / (allPassengers.size + 1)
        val left = tile.center.x - CityMap.TILE_SIZE / 2
        allPassengers.forEachIndexed { index, passenger ->
            passenger.setPos(
                Pos(
                    x = left + increment * (index + 1),
                    y = tile.center.y
                )
            )
        }
        return gameLoop.gameTime.now.value
    }
}

private class RemoveUpsetPassenger(
    val passengerId: Id
): Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val passenger = city.passengers.value.firstOrNull {
            it.id == passengerId && it.car.value == null
        } ?: return
        gameLoop.player.onMissedPassenger(gameLoop, passenger)
        city.removeUnpickedPassenger(passenger)
    }
}

// TODO gotta save what this did as well so that between load / save, it won't go bananas
class CityAILoop(
    lastAddedPassengerTime: Duration = Duration.ZERO,
    lastAddedBusinessTime: Duration = Duration.ZERO,
    lastAddedHouseTime: Duration = Duration.ZERO,
    private val config: Config = Config()
) {
    private val addBusinessConstraint = DurationConstraint(
        min = config.minTimeBetweenNewBusinesses,
        max = config.maxTimeBetweenNewBusinesses,
        lastAction = lastAddedBusinessTime
    )
    // passenger creation constraint per home
    private val housePassengerConstraints: MutableMap<
            Pair<Int, Int>, DurationConstraint> = mutableMapOf()

    private fun passengerCreationConstraintForHome(
        homeTile: CitySnapshot.TileSnapshot
    ): DurationConstraint {
        return housePassengerConstraints.getOrPut(
            homeTile.row to homeTile.col
        ) {
            DurationConstraint(
                min = config.minTimeBetweenNewPassengers,
                max = config.maxTimeBetweenNewPassengers,
                lastAction = Duration.ZERO // TODO this should be saved
            )
        }
    }
    private val addHouseConstraint = DurationConstraint(
        min = config.minTimeBetweenNewHomes,
        max = config.maxTimeBetweenNewHomes,
        lastAction = lastAddedHouseTime
    )
    internal fun doAILoop(
        citySnapshot: CitySnapshot
    ): Event {
        return CompositeEvent(
            listOfNotNull(
                handleBusinessCreation(citySnapshot),
                handleHouseCreation(citySnapshot),
                handlePassengerCreation(citySnapshot),
                handlePassengerMoods(citySnapshot),
                handlePassengerLeave(citySnapshot),
                handleCityExpansion(citySnapshot)
            )
        )
    }

    private fun handleCityExpansion(citySnapshot: CitySnapshot): Event? {
        val minWidth = 10 + 5 * citySnapshot.now.inWholeDays
        val minHeight = 10 + 10 * citySnapshot.now.inWholeDays
        if (citySnapshot.grid.width < minWidth || citySnapshot.grid.height < minHeight) {
            return ExpandCityEvent(
                width = minWidth.toInt(),
                height = minHeight.toInt()
            )
        }
        return null
    }

    private fun handlePassengerCreation(citySnapshot: CitySnapshot): Event? {
        val events = citySnapshot.houseTiles.mapNotNull {
            val constraint = passengerCreationConstraintForHome(it)
            constraint.considerExecution(citySnapshot.now) {
                val (goodChoice, badChoice) = citySnapshot.grid.neighborsOf(it.center).partition {
                    it.content == TileContent.Road
                }
                val candidateTiles = if (goodChoice.isNotEmpty()) {
                    goodChoice
                } else {
                    badChoice
                }.filter {
                    (citySnapshot.availablePassengersByTile[it] ?: emptyList()).size < MAX_PASSENGERS_PER_TILE
                }
                if (candidateTiles.isEmpty()) {
                    null
                } else {
                    val tile = candidateTiles[aiRand.nextInt(candidateTiles.size)]
                    val passengersOnThisTile = citySnapshot.availablePassengersByTile[tile] ?: emptyList()
                    AddPassengerEvent(row = tile.row, col = tile.col,
                        passengersOnThisTile = passengersOnThisTile.map { it.id })

                }
            }
        }
        if (events.isEmpty()) {
            return null
        }
        return CompositeEvent(events)
    }

    private fun handleBusinessCreation(citySnapshot: CitySnapshot): Event? {
        return addBusinessConstraint.considerExecution(
            now = citySnapshot.now
        ) {
            AddBusinessEvent()
        }
    }

    private fun handleHouseCreation(citySnapshot: CitySnapshot): Event? {
        return addHouseConstraint.considerExecution(
            now = citySnapshot.now
        ) {
            AddHomeEvent()
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

        val minTimeBetweenNewHomes: Duration = Duration.hours(4),
        val maxTimeBetweenNewHomes: Duration = Duration.days(1),

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
        fun considerExecution(now: Duration, create : () -> AIEventWithResult<Duration?>?): Event? {
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
            val event = create() ?: return null
            pendingEvent = event
            return event
        }
    }
}