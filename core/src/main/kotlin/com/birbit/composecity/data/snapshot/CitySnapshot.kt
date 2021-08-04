package com.birbit.composecity.data.snapshot

import com.birbit.composecity.data.*

class CitySnapshot(
    city: City
) {
    val blackboard: Blackboard = BlackboardImpl()
    val grid: Grid<TileSnapshot>
    val cars: List<CarSnapshot>
    val availablePassengers: List<PassangerSnapshot>
    val hasAvailablePassengers
        get() = availablePassengers.isNotEmpty()
    init {
        // this is super inefficient but we don't care, for now...
        // we can make this mutable, track changes in the city and update efficiently, easily.
        val gridData = city.map.tiles.data.map {
            TileSnapshot(
                tile = it,
                _passangers = mutableListOf(),
                _cars = mutableListOf()
            )
        }
        grid = GridImpl(
            width = city.map.width,
            height = city.map.height,
            unitSize = CityMap.TILE_SIZE,
            data = gridData
        )

        cars = city.cars.value.map { car ->
            val carSnapshot = CarSnapshot(
                car = car,
                passenger = car.passenger?.let {
                    PassangerSnapshot(
                        pos = it.pos.value,
                        target = grid.findClosest(it.target.center)
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
        availablePassengers = city.passangers.value.mapNotNull {
            val car = it.car.value
            if (car == null) {
                PassangerSnapshot(
                    pos = it.pos.value,
                    target = grid.findClosest(it.pos.value)
                ).also {
                    grid.findClosest(it.pos)._passangers.add(it)
                }
            } else {
                null
            }
        }
    }

    data class TileSnapshot(
        private val tile: Tile,
        val content: TileContent = tile.contentValue,
        internal val _passangers: MutableList<PassangerSnapshot>,
        internal val _cars: MutableList<CarSnapshot>
    ) {
        fun hasPassanger() = _passangers.isNotEmpty()

        val row
            get() = tile.row
        val col
            get() = tile.col
        val center
            get() = tile.center
        val passangers: List<PassangerSnapshot>
            get() = _passangers
        val cars: List<CarSnapshot>
            get() = _cars
    }

    class CarSnapshot(
        internal val car: Car,
        val pos: Pos = car.pos.value,
        val passenger: PassangerSnapshot? = null,
        val taxiStation: TileSnapshot
    )

    class PassangerSnapshot(
        val pos: Pos,
        val target: TileSnapshot
    )

    fun findPath(
        start: TileSnapshot,
        canVisit : (TileSnapshot)-> Boolean,
        isTarget: (TileSnapshot) -> Boolean
    ): List<TileSnapshot>? {
        return grid.findPath(
            start = start,
            position = { it.center },
            canVisit = canVisit,
            isTarget = isTarget
        )
    }

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