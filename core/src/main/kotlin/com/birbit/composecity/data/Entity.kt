package com.birbit.composecity.data

class World(
    val tiles: List<TileContent>
) {

}
interface Entity {
    fun doGameLoop(
        deltaTime: Float
    )
}