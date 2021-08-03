package com.birbit.composecity.data.snapshot

import com.birbit.composecity.data.*
import kotlinx.coroutines.Deferred

class CitySnapshot(
    city: City
) {
    val blackboard: Blackboard = BlackboardImpl()
    val grid: Grid<TileSnapshot>
    val cars: List<CarSnapshot>
    val foods: List<FoodSnapshot>
    val hasFood = city.foods.value.isNotEmpty()
    init {
        // this is super inefficient but we don't care, for now...
        // we can make this mutable, track changes in the city and update efficiently, easily.
        val gridData = city.map.tiles.data.map {
            TileSnapshot(
                tile = it,
                _foods = mutableListOf(),
                _cars = mutableListOf()
            )
        }
        grid = GridImpl(
            width = city.map.width,
            height = city.map.height,
            unitSize = CityMap.TILE_SIZE,
            data = gridData
        )
        foods = city.foods.value.map { food ->
            val tile = grid.findClosest(
                food.tile.center
            )
            val foodSnapshot = FoodSnapshot(tile)
            tile._foods.add(foodSnapshot)
            foodSnapshot
        }
        cars = city.cars.value.map { car ->
            val carSnapshot = CarSnapshot(car)
            grid.findClosest(
                car.pos.value
            )._cars.add(carSnapshot)
            carSnapshot
        }
    }

    data class TileSnapshot(
        private val tile: Tile,
        val content: TileContent = tile.contentValue,
        internal val _foods: MutableList<FoodSnapshot>,
        internal val _cars: MutableList<CarSnapshot>
    ) {
        fun hasFood() = _foods.isNotEmpty()

        val row
            get() = tile.row
        val col
            get() = tile.col
        val center
            get() = tile.center
        val foods: List<FoodSnapshot>
            get() = _foods
        val cars: List<CarSnapshot>
            get() = _cars
    }

    class CarSnapshot(
        internal val car: Car,
        val pos: Pos = car.pos.value
    )

    class FoodSnapshot(
        val tile: TileSnapshot
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

    suspend fun findPath2(
        queue: FairSharedQueue<TileSnapshot, List<TileSnapshot>?>,
        start: TileSnapshot,
        canVisit : (TileSnapshot)-> Boolean,
        isTarget: (TileSnapshot) -> Boolean
    ): List<TileSnapshot>? {
        return grid.findPath2(
            queue = queue,
            start = start,
            position = { it.center },
            canVisit = canVisit,
            isTarget = isTarget
        )
    }
}