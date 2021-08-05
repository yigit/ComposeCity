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

enum class TileContent(
    val id:Int,
    val canCarGo: Boolean
) {
    Grass(id = 1, canCarGo = false),
    Road(id = 2, canCarGo = true),
    Business(id = 3, canCarGo = false),
    TaxiStation(id = 4, canCarGo = false), // only taxis that were housed here can go
    House(id = 5, canCarGo = false);
    fun canCarGo(): Boolean = canCarGo

    companion object {
        private val byId = values().associateBy { it.id }
        fun fromId(id:Int) = byId[id] ?: error("invalid tile id: $id")
    }
}