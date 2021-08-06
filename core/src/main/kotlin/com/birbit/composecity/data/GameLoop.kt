package com.birbit.composecity.data

import com.birbit.composecity.GameTime
import com.birbit.composecity.ai.CarAILoop
import com.birbit.composecity.ai.CityAILoop
import com.birbit.composecity.data.serialization.LoadSave
import com.birbit.composecity.data.snapshot.CitySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

// TODO add ability to return value for events.
interface Event {
    fun apply(gameLoop: GameLoop, city: City)
}

class AddCarToStationEvent(
    val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        if (tile.content.value == TileContent.TaxiStation) {
            gameLoop.player.deductMoney(
                Player.COST_OF_CAR
            ) {
                city.addCar(
                    Car(
                        id = city.idGenerator.nextId(),
                        initialPos = tile.center,
                        taxiStation = tile
                    )
                )
            }
        }
    }

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
        // TODO it is ugly that we are passing player here... need to find a better way
        city.toggleTile(gameLoop.player, tile)
    }
}

class AddCarEvent(
    private val tile: Tile
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val newCar = Car(
            id = city.idGenerator.nextId(),
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
        city.addTaxiStation(gameLoop.player, tile)
    }
}

val SAVE_FILE_NAME = "saved.city"
class SaveEvent : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val loadSave = LoadSave.create(gameLoop)
        File(SAVE_FILE_NAME).writeText(loadSave.data)
    }
}

@OptIn(ExperimentalTime::class)
// TODO separate GAME from GameLoop
class GameLoop(
    val player: Player,
    startDuration: Duration,
    val city: City
) {
    val gameTime = GameTime(startDuration)
    internal val rand = Random(System.nanoTime())

    private val events = Channel<Event>(
        capacity = Channel.UNLIMITED
    )
    // TODO this needs to be saved
    private val cityAILoop = CityAILoop()
    private val aiDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val aiScope = CoroutineScope(aiDispatcher + Job())
    private val gameScope = CoroutineScope(Dispatchers.Main + Job())
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())

    val notifications: StateFlow<List<Notification>>
        get() = _notifications

    init {
        start()
    }

    internal fun addNotification(notification: Notification) {
        _notifications.value += notification
    }

    internal fun removeNotification(notificationId: String) {
        _notifications.value = _notifications.value.filterNot {
            it.id == notificationId
        }
    }

    fun close() {
        gameScope.cancel()
        aiScope.cancel()
    }



    private fun start() {
        events.consumeAsFlow().onEach {
            // there is a possibility that we may not want certain events at the same time
            // we can get some priority OR categorization (probably categorization)
            it.apply(this, city)
        }.launchIn(gameScope)
        gameScope.launch {
            timedLoop(
                // well, this should actually sync w/ frame time, but we don't have frame time :) or maybe we do?
                period = Duration.milliseconds(16)
            ) { _ ->
                if (gameTime.gameSpeed.value == GameTime.GameSpeed.PAUSED) {
                    return@timedLoop
                }
                val delta = gameTime.gameTick()
                val city = city
                val cars = city.cars.value
                val passengers = city.passengers.value
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
                arrivedPassengers.forEach {
                    player.onDeliveredPassenger(this@GameLoop, it)
                }
                arrivedPassengers.forEach(city::removePassenger)
                player.onDistanceTraveledByCars(distanceTraveledByCars)
            }
        }
        aiScope.launch {
            // TODO this should use game time!!
            timedLoop(
                period = Duration.milliseconds(250)
            ) { _ ->
                val snapshot = withContext(gameScope.coroutineContext) {
                    if (gameTime.gameSpeed.value == GameTime.GameSpeed.PAUSED) {
                        null
                    } else {
                        // TODO we need to eventually make this snapshot incremental.
                        CitySnapshot(this@GameLoop, city)
                    }
                }
                snapshot?.let {
                    val carEvents = CarAILoop().doAILoop(snapshot)
                    val businessEvents = cityAILoop.doAILoop(snapshot)
                    addEvent(CompositeEvent(listOf(carEvents, businessEvents)))
                }

            }
        }
    }

    private fun pickUp(
        city: City,
        passenger: Passenger,
        car: Car?
    ) {
        if (car == null) return
        city.associatePassengerWithCar(passenger, car)
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
                        city, passanger, cars.getOrNull(index)
                    )
                }
            }
        }
    }

    fun addEvent(event: Event) {
        events.trySend(event)
    }
}