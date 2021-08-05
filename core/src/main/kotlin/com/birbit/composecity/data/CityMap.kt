package com.birbit.composecity.data

interface CityMap {
    val width: Int
    val height: Int
    val tiles: Grid<Tile>

    companion object {
        const val TILE_SIZE = 64f
    }
}

interface MutableCityMap: CityMap {
    val mutableTiles: Grid<MutableTile>
}

class CityMapImpl(
    override val width: Int,
    override val height: Int,
    content: List<TileContent>? = null
) : MutableCityMap {
    override val mutableTiles = buildTiles(width = width, height = height, content)
    @Suppress("UNCHECKED_CAST")
    override val tiles: Grid<Tile> = mutableTiles as Grid<Tile>

    companion object {
        fun buildTiles(
            width: Int,
            height: Int,
            contents : List<TileContent>? = null
        ): GridImpl<MutableTile> {
            val result = mutableListOf<TileImpl>()
            repeat(height) { h ->
                repeat(width) { w ->
                    result.add(TileImpl(row = h, col = w))
                }
            }
            if (contents != null) {
                result.forEachIndexed { index, tile ->
                    tile.content.value = contents[index]
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