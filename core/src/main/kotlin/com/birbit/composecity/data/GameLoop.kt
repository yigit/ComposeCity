package com.birbit.composecity.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

interface Event {
    fun apply(city: City)
}

class ToggleTileEvent(
    private val tile: Tile
) : Event {
    override fun apply(city: City) {
        if (tile.contentValue == TileContent.Grass) {
            tile.contentValue = TileContent.Road
        } else {
            tile.contentValue = TileContent.Grass
        }
    }
}

class AddCarEvent(
    private val tile: Tile
) : Event {
    override fun apply(city: City) {
        val newCar = Car(
            initialPos = tile.center
        )
        // build a path
        val path = buildFakePath(city, tile)
        newCar.targetPath = path
        city.addCar(newCar)
    }

    private fun buildFakePath(city: City, tile: Tile): Path? {
        val tiles = mutableListOf<Tile>()
        var row = tile.row + 1
        do {
            val next = city.map.tiles.maybeGet(
                row = row,
                col = tile.col)
            if (next != null) {
                tiles.add(next)
            }
            row ++
        } while (next != null)
        return if (tiles.isEmpty()) {
            null
        } else {
            Path(tiles)
        }
    }

}

@OptIn(ExperimentalTime::class)
class GameLoop(
    private val city: City
) {
    private val events = Channel<Event>(
        capacity = Channel.UNLIMITED
    )
    private val lastTime: Duration? = null
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun start() {
        events.consumeAsFlow().onEach {
            it.apply(city)
        }.launchIn(scope)
        scope.launch {
            timedLoop(
                // well, this should actually sync w/ frame time, but we don't have frame time :) or maybe we do?
                period = Duration.milliseconds(16)
            ) { delta ->
                city.cars.value.forEach {
                    it.doGameLoop(city, delta)
                }
            }
        }
    }

    fun addEvent(event: Event) {
        events.trySend(event)
    }
}