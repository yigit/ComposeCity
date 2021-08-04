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

class ToggleBusinessEvent(
    private val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        if (tile.contentValue == TileContent.Business) {
            tile.contentValue = TileContent.Grass
        } else {
            tile.contentValue = TileContent.Business
        }
    }
}

class ToggleTileEvent(
    private val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        if (tile.contentValue == TileContent.Grass) {
            gameLoop.player.deductMoney(Player.COST_OF_ROAD) {
                tile.contentValue = TileContent.Road
            }
        } else if (tile.contentValue == TileContent.Road) {
            tile.contentValue = TileContent.Grass
        }
    }
}

class AddCarEvent(
    private val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val newCar = Car(
            initialPos = tile.center,
            taxiStation = tile
        )
        city.addCar(newCar)
    }
}

class AddTaxiStationEvent(
    private val tile: Tile
): Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        if (tile.contentValue != TileContent.Grass) {
            return
        }
        gameLoop.player.deductMoney(Player.COST_OF_TAXI_STATION) {
            tile.contentValue = TileContent.TaxiStation
            val newCar = Car(
                initialPos = tile.center,
                taxiStation = tile
            )
            city.addCar(newCar)
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
        val businessTiles = city.map.tiles.data.filter {
            it.contentValue == TileContent.Business
        }
        if(businessTiles.isEmpty()) return
        val roadTiles = city.map.tiles.data.filter {
            it.contentValue == TileContent.Road
        }
        if (roadTiles.isEmpty()) return
        val passengersWithTiles = city.passangers.value.map {
            city.map.tiles.findClosest(it.pos.value)
        }
        repeat(100) {
            val tile = roadTiles[gameLoop.rand.nextInt(roadTiles.size)]
            if (!passengersWithTiles.contains(tile)) {
                // find business
                val target = businessTiles.get(
                    gameLoop.rand.nextInt(businessTiles.size)
                )
                city.addPassanger(Passenger(pos = tile.center, target = target))
                return
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
class GameLoop {
    val player = Player()
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
                val passengers = city.passangers.value
                var distanceTraveledByCars = 0f
                cars.forEach {
                    val initalPos = it.pos.value
                    it.doGameLoop(city, delta)
                    val finalPos=  it.pos.value
                    distanceTraveledByCars += finalPos.dist(initalPos)
                }
                passengers.forEach {
                    it.doGameLoop(city, delta)
                }
                pickUpPassengers(cars, city, passengers)
                val arrivedPassengers = passengers.filter {
                    it.target.center.dist(it.pos.value) < CityMap.TILE_SIZE / 2
                }
                arrivedPassengers.forEach(player::onDeliveredPassenger)
                arrivedPassengers.forEach(city::removePassanger)
                player.onDistanceTraveledByCars(distanceTraveledByCars)
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

    private fun pickUp(
        passenger: Passenger,
        car: Car?
    ) {
        if (car == null) return
        car.passenger = passenger
        passenger.setCar(car)
    }

    private fun pickUpPassengers(
        cars: List<Car>,
        city: City,
        passengers: List<Passenger>
    ) {
        val carTiles = cars.filter { it.passenger == null }.groupBy {
            city.map.tiles.findClosest(it.pos.value)
        }
        val passengerTiles = passengers.filter { it.car.value == null }.groupBy {
            city.map.tiles.findClosest(it.pos.value)
        }
        carTiles.entries.forEach { (tile, cars) ->
            val freePassengers = passengerTiles[tile]
            if (freePassengers != null) {
                freePassengers.forEachIndexed { index, passanger ->
                    pickUp(
                        passanger, cars.getOrNull(index)
                    )
                }
            }
        }
    }

    fun addEvent(event: Event) {
        events.trySend(event)
    }
}