package com.birbit.composecity.data

interface Grid<T> {
    val width: Int
    val height: Int
    val data: List<T>
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
    override val data: List<T>
) : Grid<T>