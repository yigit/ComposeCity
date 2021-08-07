package com.birbit.composecity.data.snapshot

import com.birbit.composecity.Id
import com.birbit.composecity.data.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class CitySnapshot(
    gameLoop: GameLoop,
    city: City
) {
    val blackboard: Blackboard = BlackboardImpl()
    val grid: Grid<TileSnapshot>
    val cars: List<CarSnapshot>
    val availablePassengers: List<PassengerSnapshot>
    val now = gameLoop.gameTime.now.value
    val businessTiles: List<TileSnapshot>
    val houseTiles: List<TileSnapshot>

    init {
        // this is super inefficient but we don't care, for now...
        // we can make this mutable, track changes in the city and update efficiently, easily.
        val gridData = city.map.tiles.data.map {
            TileSnapshot(
                tile = it,
                _passengers = mutableListOf(),
                _cars = mutableListOf()
            )
        }
        grid = GridImpl(
            width = city.map.width,
            height = city.map.height,
            unitSize = CityMap.TILE_SIZE,
            data = gridData
        )
        houseTiles = city.houseTiles.map {
            grid.get(row = it.row, col = it.col)
        }

        businessTiles = city.businessTiles.map {
            grid.get(row = it.row, col = it.col)
        }

        cars = city.cars.value.map { car ->
            val carSnapshot = CarSnapshot(
                carId = car.id,
                pos = car.pos.value,
                passengerTarget = car.passenger?.target?.let { passengerTarget ->
                    grid.get(
                        row = passengerTarget.row,
                        col = passengerTarget.col
                    )
                },
                taxiStation = grid.get(
                    row = car.taxiStation.row,
                    col = car.taxiStation.col
                )
            )
            grid.findClosest(
                car.pos.value
            )._cars.add(carSnapshot)
            carSnapshot
        }
        availablePassengers = city.passengers.value.mapNotNull {
            val car = it.car.value
            if (car == null) {
                PassengerSnapshot(
                    id = it.id,
                    pos = it.pos.value,
                    mood = it.mood.value,
                    creationTime = it.creationTime
                ).also {
                    grid.findClosest(it.pos)._passengers.add(it)
                }
            } else {
                null
            }
        }
    }

    val availablePassengersByTile by lazy(LazyThreadSafetyMode.NONE) {
        availablePassengers.groupBy {
            grid.findClosest(it.pos)
        }
    }

    data class TileSnapshot(
        private val tile: Tile,
        val content: TileContent = tile.content.value,
        internal val _passengers: MutableList<PassengerSnapshot>,
        internal val _cars: MutableList<CarSnapshot>
    ) {
        fun hasPassanger() = _passengers.isNotEmpty()

        val row
            get() = tile.row
        val col
            get() = tile.col
        val center
            get() = tile.center
    }

    class CarSnapshot(
        val carId: Id,
        val pos: Pos,
        val passengerTarget: TileSnapshot?,
        val taxiStation: TileSnapshot
    )

    @OptIn(ExperimentalTime::class)
    class PassengerSnapshot(
        val id: Id,
        val pos: Pos,
        val mood: Passenger.Mood,
        val creationTime: Duration
    )


    suspend fun findPathParallel(
        queue: FairSharedQueue<TileSnapshot, List<TileSnapshot>?>,
        start: TileSnapshot,
        canVisit : (TileSnapshot)-> Boolean,
        isTarget: (TileSnapshot) -> Boolean
    ): List<TileSnapshot>? {
        return grid.findPathParallel(
            queue = queue,
            start = start,
            position = { it.center },
            canVisit = canVisit,
            isTarget = isTarget
        )
    }
}