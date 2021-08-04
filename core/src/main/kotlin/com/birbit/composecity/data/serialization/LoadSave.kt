package com.birbit.composecity.data.serialization

import com.birbit.composecity.GameTime
import com.birbit.composecity.Id
import com.birbit.composecity.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Serializable
private data class SerializedPos(
    val row: Float,
    val col: Float
)

@Serializable
private data class SerializedCoordinates(
    val row: Int,
    val col: Int
)

@Serializable
private data class SerializedPassenger(
    val id: Id,
    val initialPos: SerializedPos,
    val target: SerializedCoordinates
)

@Serializable
private data class SerializedGameTime(
    val timeInSeconds : Long
)

@Serializable
private data class SerializedCar(
    val id: Id,
    val pos: SerializedPos,
    val taxiStation: SerializedCoordinates
)

@Serializable
private data class SerializedCity(
    val lastGeneratedId: Id,
    val tiles: List<Int>,
    val width: Int,
    val height: Int,
    val carPassengerMapping : Map<Id, Id>,
    val passengers: List<SerializedPassenger>,
    val cars: List<SerializedCar>
)

@Serializable
private data class SerializedPlayer(
    // TODO save pending distance
    val money: Int
)

@Serializable
private data class SerializedGame(
    val player: SerializedPlayer,
    val city: SerializedCity,
    val gameTime: SerializedGameTime
)

class LoadSave(
    val data: String
) {
    @OptIn(ExperimentalTime::class)
    fun create(): Triple<City, Player, Duration> {
        val decoded = Json.decodeFromString<SerializedGame>(data)
        val city = decoded.city.toGameObject()
        val player = decoded.player.toGameObject()
        val gameTime = decoded.gameTime.timeInSeconds
        // TODO use a proper data class
        return Triple(city, player, Duration.seconds(gameTime))
    }
    companion object {
        private fun Player.toSerialized() = SerializedPlayer(
            money = this.money.value
        )
        private fun Pos.toSerialized() = SerializedPos(
            row = row,
            col = col
        )

        private fun Passenger.toSerialized() = SerializedPassenger(
            id = id,
            initialPos = initialPos.toSerialized(),
            target = SerializedCoordinates(
                row = target.row,
                col = target.col
            )
        )

        private fun Car.toSerialized() = SerializedCar(
            id = id,
            pos = pos.value.toSerialized(),
            taxiStation = SerializedCoordinates(
                row = taxiStation.row,
                col = taxiStation.col
            )
        )

        private fun City.toSerialized(): SerializedCity {
            val carPassengerMapping = this.cars.value.filter {
                it.passenger != null
            }.associate {
                it.id to it.passenger!!.id
            }
            return SerializedCity(
                lastGeneratedId = this.idGenerator.lastId,
                width = this.map.width,
                height = this.map.height,
                tiles = this.map.tiles.data.map {
                    it.contentValue.id
                },
                passengers = this.passengers.value.map {
                    it.toSerialized()
                },
                cars = this.cars.value.map {
                    it.toSerialized()
                },
                carPassengerMapping = carPassengerMapping
            )
        }
        @OptIn(ExperimentalTime::class)
        private fun GameTime.toSerialized() = SerializedGameTime(
            timeInSeconds = this.now.value.inWholeSeconds
        )
        fun load(file: File): LoadSave? {
            if (!file.exists()) return null
            return LoadSave(file.readText(Charsets.UTF_8))
        }
        fun create(gameLoop: GameLoop): LoadSave {
            val serialized = SerializedGame(
                player = gameLoop.player.toSerialized(),
                city = gameLoop.city.toSerialized(),
                gameTime = gameLoop.gameTime.toSerialized()
            )
            val json = Json.encodeToString(serialized)
            return LoadSave(
                data = json
            )
        }

        private fun SerializedPlayer.toGameObject() =
            Player(
                initialMoney = money
            )

        private fun SerializedPos.toGameObject() = createPos(row = row, col = col)
        private fun SerializedPassenger.toGameObject(
            map: Grid<Tile>
        ) =
            Passenger(
                id = id,
                pos = initialPos.toGameObject(),
                target = map.get(
                    row = target.row,
                    col = target.col
                ),
                car = null // assigned later
            )
        private fun SerializedCar.toGameObject(
            map: Grid<Tile>
        ) = Car(
            id = id,
            initialPos = pos.toGameObject(),
            taxiStation = map.get(
                row = taxiStation.row,
                col = taxiStation.col
            )
        )
        private fun SerializedCity.toGameObject(): City {
            val mapData = this.tiles.map {
                TileContent.fromId(it)
            }
            val city = City(
                map = CityMap(
                    width = width,
                    height = height,
                    content = mapData
                ),
                startId = lastGeneratedId
            )
            val passengersById = this.passengers.map {
                it.toGameObject(city.map.tiles).also {
                    city.addPassenger(it)
                }
            }.associateBy {
                it.id
            }
            val carsById = this.cars.map {
                it.toGameObject(city.map.tiles).also {
                    city.addCar(it)
                }
            }.associateBy {
                it.id
            }
            this.carPassengerMapping.forEach { (carId, passengerId) ->
                val passenger = passengersById[passengerId] ?: error("cannot find passenger $passengerId")
                val car = carsById[carId] ?: error("cannot find car $carId")
                city.associatePassengerWithCar(passenger, car)
            }
            return city
        }
        const val VERSION = 3
    }
}