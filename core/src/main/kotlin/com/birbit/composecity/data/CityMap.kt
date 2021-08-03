package com.birbit.composecity.data

class CityMap(
    val width: Int,
    val height: Int,
    content: List<TileContent>? = null
) {
    val tiles = buildTiles(width = width, height = height, content)


    companion object {
        val TILE_SIZE = 64f
        fun buildTiles(
            width: Int,
            height: Int,
            contents : List<TileContent>? = null
        ): GridImpl<Tile> {
            val result = mutableListOf<Tile>()
            repeat(height) { h ->
                repeat(width) { w ->
                    result.add(Tile(row = h, col = w))
                }
            }
            if (contents != null) {
                result.forEachIndexed { index, tile ->
                    tile.contentValue = contents[index]
                }
            }
            return GridImpl(
                width = width,
                height = height,
                data = result,
                unitSize = CityMap.TILE_SIZE
            )
        }
    }
}