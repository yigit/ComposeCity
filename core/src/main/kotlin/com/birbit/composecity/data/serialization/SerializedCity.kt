package com.birbit.composecity.data.serialization

import com.birbit.composecity.data.City
import com.birbit.composecity.data.CityMap
import com.birbit.composecity.data.TileContent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SerializedCity(
    val data: ByteArray
) {
    fun buildCity(): City {
        val bis = ByteArrayInputStream(data)
        val version = bis.read()
        check(version == VERSION)
        val width = bis.read()
        val height = bis.read()
        val tilesContent = (0 until width * height).map {
            when(val value = bis.read()) {
                1 -> TileContent.Road
                2 -> TileContent.Grass
                3 -> TileContent.OutOfBounds
                else -> error("unexpected value $value")
            }
        }
        val map = CityMap(
            width = width,
            height = height,
            content = tilesContent
        )

        return City(
            map
        )
    }
    companion object {
        fun create(city: City): SerializedCity {
            val bos = ByteArrayOutputStream()
            bos.write(VERSION)
            bos.write(city.map.width)
            bos.write(city.map.height)
            city.map.tiles.data.forEach {
                val value = when(it.contentValue) {
                    TileContent.Road -> 1
                    TileContent.Grass -> 2
                    TileContent.OutOfBounds -> 3
                }
                bos.write(value)
            }
            return SerializedCity(bos.toByteArray())
        }

        const val VERSION = 1
    }
}