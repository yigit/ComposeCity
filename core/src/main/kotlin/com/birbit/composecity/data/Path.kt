package com.birbit.composecity.data

class Path(
    private val positions : List<Pos>
) {
    private var index = 0
    fun target(pos: Pos, radius: Double): Pos? {
        if (index >= positions.size) return null
        val target = positions[index]
        val distance = target.dist(pos)
        if (distance <= radius) {
            index ++
            // can make it to it in this frame, pick second one if available
            return if (index < positions.size) {
                positions[index]
            } else {
                target
            }
        }
        return target
    }

}