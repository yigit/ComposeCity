package com.birbit.composecity.data

class CityMap(
    val width: Int,
    val height: Int
) {
    val tiles = buildTiles(width = width, height = height)


    companion object {
        val TILE_SIZE = 64f
        fun buildTiles(
            width: Int,
            height: Int
        ): GridImpl<Tile> {
            val result = mutableListOf<Tile>()
            repeat(height) { h ->
                repeat(width) { w ->
                    result.add(Tile(row = h, col = w))
                }
            }
            return GridImpl(
                width = width,
                height = height,
                data = result
            )
        }
    }
}