package com.birbit.composecity.data

class Path(
    private val tiles : List<Tile>
) {
    private var index = 0
    fun target(pos: Pos, radius: Double): Pos? {
        if (index >= tiles.size) return null
        val target = tiles[index].center
        val distance = target.dist(pos)
        if (distance <= radius) {
            index ++
            // can make it to it in this frame, pick second one if available
            return if (index < tiles.size) {
                tiles[index].center
            } else {
                target
            }
        }
        return target
    }

}