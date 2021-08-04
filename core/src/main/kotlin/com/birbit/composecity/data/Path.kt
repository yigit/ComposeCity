package com.birbit.composecity.data

class Path(
    private val positions : List<Pos>
) {
    private var index = 0
    fun target(pos: Pos): Pos? {
        if (index >= positions.size) return null
        val target = positions[index]
        val distance = target.dist(pos)
        if (distance <= CLOSE_ENOUGH) {
            index ++
            return positions[index - 1]
        }
        return target
    }

    fun isFinished(): Boolean {
        return index >= positions.size
    }

    companion object {
        const val CLOSE_ENOUGH = 1f
    }
}