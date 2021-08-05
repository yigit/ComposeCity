package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface Tile {
    val row: Int
    val col: Int
    val content: StateFlow<TileContent>
    val center: Pos
}

interface MutableTile: Tile {
    override val content: MutableStateFlow<TileContent>
}

data class TileImpl(
    override val row:Int,
    override val col:Int
) : MutableTile {
    override val content = MutableStateFlow<TileContent>(
        TileContent.Grass
    )

    override val center: Pos = createPos(
        row = row.toFloat() * CityMap.TILE_SIZE + CityMap.TILE_SIZE / 2,
        col = col.toFloat() * CityMap.TILE_SIZE + CityMap.TILE_SIZE / 2
    )

    override fun toString() = "Tile[$row/$col]"
}

sealed class TileContent(
    val id:Int
) {
    open fun canCarGo(): Boolean = false

    object Grass : TileContent(1) {
        override fun toString() = "Grass"
    }
    object Road : TileContent(2) {
        override fun canCarGo() = true
        override fun toString() = "Road"
    }
    object OutOfBounds: TileContent(3) {
        override fun toString() = "out of bounds"
    }
    object Business: TileContent(4) {
        override fun toString() = "business"
    }
    object TaxiStation : TileContent(5) {
        override fun toString() = "taxi station"
    }
    companion object {
        fun fromId(id:Int) = when(id) {
            1 -> Grass
            2 -> Road
            3 -> OutOfBounds
            4 -> Business
            5 -> TaxiStation
            else -> error("invalid tile id: $id")
        }
    }

}