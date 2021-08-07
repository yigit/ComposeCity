package com.birbit.composecity.data


private data class Coordinates(
    val row:Int,
    val col:Int
) {
    private fun isInBounds(
        width: Int,
        height: Int
    ) = row >= 0 && row < height && col >= 0 && col < width

    private fun ifInBounds(width: Int, height: Int) = if (isInBounds(width, height)) this else null

    fun north(
        width: Int,
        height: Int
    ): Coordinates? {
        return Coordinates(
            row = row - 1,
            col = col
        ).ifInBounds(width, height)
    }

    fun south(
        width: Int,
        height: Int
    ): Coordinates? {
        return Coordinates(
            row = row + 1,
            col = col
        ).ifInBounds(width, height)
    }

    fun west(
        width: Int,
        height: Int
    ): Coordinates? {
        return Coordinates(
            row = row,
            col = col - 1
        ).ifInBounds(width, height)
    }

    fun east(
        width: Int,
        height: Int
    ): Coordinates? {
        return Coordinates(
            row = row,
            col = col + 1
        ).ifInBounds(width, height)
    }

}
// TODO: It might make sense to add some utiltiy functions to grid to get things by type etc. maybe at a higher level
interface Grid<T> {
    val width: Int
    val height: Int
    val data: List<T>
    val unitSize: Float


    private fun findClosestCoordinates(
        pos: Pos
    ) : Coordinates = Coordinates(
        row = pos.row.coerceIn(0f, height * unitSize - 1f).div(unitSize).toInt(),
        col = pos.col.coerceIn(0f, width * unitSize - 1f).div(unitSize).toInt()
    )

    private fun findClosestCoordinatesWithoutBoundsCheck(
        pos: Pos
    ) : Coordinates = Coordinates(
        row = pos.row.div(unitSize).toInt(),
        col = pos.col.div(unitSize).toInt()
    )

    suspend fun findPathParallel(
        queue: FairSharedQueue<T, List<T>?>,
        start: T,
        position : (T) -> Pos,
        canVisit : (T)-> Boolean,
        isTarget: (T) -> Boolean
    ): List<T>? {
        val visited = mutableMapOf<T, T>()
        fun buildPath(target: T): List<T> {
            val tiles = mutableListOf<T>()
            var current = target
            while(true) {
                tiles.add(current)
                val prev = visited[current]
                if (prev != null && prev != current && prev != start) {
                    current = prev
                } else {
                    break
                }
            }
            return tiles.reversed()
        }
        visited[start] = start
        return queue.register(start) { scope, tile ->
            if (isTarget(tile)) {
                scope.finish(buildPath(tile))
            } else {
                neighborsOf(position(tile))
                    .filterNot {
                        visited.containsKey(it)
                    }
                    .filter(canVisit).forEach {
                        scope.enqueue(it)
                        visited[it] = tile
                    }
            }
        }
    }

    fun findPath(
        start: T,
        position : (T) -> Pos,
        canVisit : (T)-> Boolean,
        isTarget: (T) -> Boolean
    ): List<T>? {
        val visited = mutableMapOf<T, T>()
        fun buildPath(target: T): List<T> {
            val tiles = mutableListOf<T>()
            var current = target
            while(true) {
                tiles.add(current)
                val prev = visited[current]
                if (prev != null && prev != current && prev != start) {
                    current = prev
                } else {
                    break
                }
            }
            return tiles.reversed()
        }
        val  queue = ArrayDeque<T>()
        queue.addFirst(
            start
        )
        visited[start] = start
        while(queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            if (isTarget(tile)) {
                return buildPath(tile)
            }
            neighborsOf(position(tile))
                .filterNot {
                    visited.containsKey(it)
                }
                .filter(canVisit).forEach {
                    queue.addLast(it)
                    visited[it] = tile
                }
        }
        return null
    }

    fun findClosest(
        pos: Pos
    ): T {
        val coordinates = findClosestCoordinates(pos)
        return get(
            row = coordinates.row,
            col = coordinates.col
        )
    }

    fun findClosestIfInBounds(
        pos: Pos
    ): T? {
        val coordinates = findClosestCoordinatesWithoutBoundsCheck(pos)
        return maybeGet(
            row = coordinates.row,
            col = coordinates.col
        )
    }

    private fun neighborsOf(coordinates: Coordinates) = sequence {
        coordinates.north(width, height)?.let {
            yield(it)
        }
        coordinates.west(width, height)?.let {
            yield(it)
        }
        coordinates.south(width, height)?.let {
            yield(it)
        }
        coordinates.east(width, height)?.let {
            yield(it)
        }
    }
    fun neighborsOf(pos: Pos) : Sequence<T> {
        val coordinates = findClosestCoordinates(pos)
        return neighborsOf(coordinates).map {
            get(it)
        }
    }


    private fun get(
        coordinates: Coordinates
    ) = get(row = coordinates.row, col = coordinates.col)

    fun get(
        row:Int,
        col: Int
    ): T {
        return data[indexOf(row = row, col = col)]
    }
    fun maybeGet(
        row: Int,
        col: Int
    ): T? {
        return maybeIndexOf(
            row = row,
            col = col
        )?.let {
            data[it]
        }
    }

    fun maybeIndexOf(
        row: Int,
        col: Int
    ): Int? {
        if (row < 0 || row >= height) return null
        if (col < 0 || col >= width) return null
        return row * width + col

    }
    fun indexOf(
        row: Int,
        col: Int
    ): Int {
        return checkNotNull(maybeIndexOf(row, col))
    }
}

class GridImpl<T>(
    override val width: Int,
    override val height: Int,
    override val unitSize: Float,
    override val data: List<T>
) : Grid<T>