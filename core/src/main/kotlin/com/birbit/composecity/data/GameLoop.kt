package com.birbit.composecity.data

import com.birbit.composecity.ai.CarAILoop
import com.birbit.composecity.data.serialization.SerializedCity
import com.birbit.composecity.data.snapshot.CitySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

interface Event {
    fun apply(gameLoop: GameLoop, city: City)
}

class CompositeEvent(
    val events: List<Event>
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        events.forEach {
            it.apply(gameLoop, city)
        }
    }

}

class ToggleTileEvent(
    private val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        if (tile.contentValue == TileContent.Grass) {
            tile.contentValue = TileContent.Road
        } else {
            tile.contentValue = TileContent.Grass
        }
    }
}

class AddCarEvent(
    private val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val newCar = Car(
            initialPos = tile.center
        )
        // build a path
//        val path = buildFakePath(city, tile)
//        newCar.targetPath = path
        city.addCar(newCar)
    }

    private fun buildFakePath(city: City, tile: Tile): Path? {
        val tiles = mutableListOf<Tile>()
        var row = tile.row + 1
        var col = tile.col + 1
        do {
            val next = city.map.tiles.maybeGet(
                row = tile.row,
                col = col)
            if (next != null) {
                tiles.add(next)
            }
            row ++
            col ++
        } while (next != null)
        return if (tiles.isEmpty()) {
            null
        } else {
            Path(tiles)
        }
    }
}

val SAVE_FILE_NAME = "saved.city"
class SaveEvent : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val serializedCity = SerializedCity.create(city)
        File(SAVE_FILE_NAME).writeBytes(serializedCity.data)
    }
}

class LoadEvent: Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val city = SerializedCity(File(SAVE_FILE_NAME).readBytes()).buildCity()
        gameLoop.changeCity(city)
    }
}

class AddPassangerEvent: Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val roadTiles = city.map.tiles.data.filter {
            it.contentValue == TileContent.Road
        }
        if (roadTiles.isEmpty()) return
        val existing = city.passangers.value
        repeat(100) {
            val tile = roadTiles[gameLoop.rand.nextInt(roadTiles.size)]
            if (existing.none { it.tile == tile }) {
                city.addPassanger(Passanger(tile = tile))
                return
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
class GameLoop {
    internal val rand = Random(System.nanoTime())
    private val _city = MutableStateFlow(
        City(CityMap(width = 20, height = 20))
    )
    val city: StateFlow<City>
        get() = _city

    val cityValue
        get() = _city.value

    internal fun changeCity(city: City) {
        _city.value = city
    }
    private val events = Channel<Event>(
        capacity = Channel.UNLIMITED
    )
    private val aiDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val aiScope = CoroutineScope(aiDispatcher + Job())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun start() {
        events.consumeAsFlow().onEach {
            // there is a possibility that we may not want certain events at the same time
            // we can get some priority OR categorization (probably categorization)
            it.apply(this, cityValue)
        }.launchIn(scope)
        scope.launch {
            timedLoop(
                // well, this should actually sync w/ frame time, but we don't have frame time :) or maybe we do?
                period = Duration.milliseconds(16)
            ) { delta ->
                val city = cityValue
                val cars = city.cars.value
                cars.forEach {
                    it.doGameLoop(city, delta)
                }
                val collectedPassengers = city.passangers.value.filter { passanger ->
                    cars.any { car ->
                        car.pos.value.dist(passanger.tile.center) < Car.CAR_SIZE / 2
                    }
                }
                collectedPassengers.forEach(city::removePassanger)
            }
        }
        aiScope.launch {
            timedLoop(
                period = Duration.milliseconds(250)
            ) { _ ->
                val snapshot = CitySnapshot(cityValue)
                val event = CarAILoop().doAILoop(snapshot)
                addEvent(event)
            }
        }
    }

    fun addEvent(event: Event) {
        events.trySend(event)
    }
}