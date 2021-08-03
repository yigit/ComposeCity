package com.birbit.composecity.data

class Path(
    private val tiles : List<Tile>
) {
    private var index = 0
    fun target(pos: Pos, radius: Double): Tile? {
        if (index >= tiles.size) return null
        val target = tiles[index]
        val distance = target.center.dist(pos)
        if (distance <= radius) {
            index ++
            // can make it to it in this frame, pick second one if available
            return if (index < tiles.size) {
                tiles[index]
            } else {
                target
            }
        }
        return target
    }

}