package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Tile(
    val row:Int,
    val col:Int
) {
    private val _content = MutableStateFlow<TileContent>(
        TileContent.Grass
    )
    val content: StateFlow<TileContent>
        get() = _content

    var contentValue: TileContent
        get() = _content.value
        set(value) {
            _content.value = value
        }

    val center: Pos = createPos(
        row = row.toFloat() * CityMap.TILE_SIZE + CityMap.TILE_SIZE / 2,
        col = col.toFloat() * CityMap.TILE_SIZE + CityMap.TILE_SIZE / 2
    )

    override fun toString() = "Tile[$row/$col]"
}

sealed class TileContent {
    open fun canCarGo(): Boolean = false

    object Grass : TileContent() {
        override fun toString() = "Grass"
    }
    object Road : TileContent() {
        override fun canCarGo() = true
        override fun toString() = "Road"
    }
    object OutOfBounds: TileContent() {
        override fun toString() = "out of bounds"
    }
}