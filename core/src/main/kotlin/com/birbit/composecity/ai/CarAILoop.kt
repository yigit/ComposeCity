package com.birbit.composecity.ai

import com.birbit.composecity.data.*
import com.birbit.composecity.data.snapshot.CitySnapshot
import kotlinx.coroutines.*

class CarAILoop {
    internal suspend fun doAILoop(
        citySnapshot: CitySnapshot
    ): Event {
        val cars = citySnapshot.cars
        if (cars.isEmpty() || citySnapshot.hasPassanger == false) {
            return CompositeEvent(
                cars.map {
                    ClearPathEvent(it.car)
                }
            )
        }
        val queue = FairSharedQueue<CitySnapshot.TileSnapshot, List<CitySnapshot.TileSnapshot>?>()
        val deferredEvents = coroutineScope {
            cars.map {
                async {
                    it.doAILoop(citySnapshot, queue)
                }
            }.also {
                // TODO relying on yield to ensure all asyncs suspended is ugly.
                yield()
                queue.execute()
            }
        }
        val events = deferredEvents.awaitAll()

        return CompositeEvent(events)
    }

    private suspend fun CitySnapshot.CarSnapshot.doAILoop(
        citySnapshot: CitySnapshot,
        queue: FairSharedQueue<CitySnapshot.TileSnapshot, List<CitySnapshot.TileSnapshot>?>
    ): Event {
        val closestTile = citySnapshot.grid.findClosest(pos)
        val path = citySnapshot.findPathParallel(
            queue = queue,
            start = closestTile,
            canVisit = {
                it.content.canCarGo()
            },
            isTarget = {
                val result = it.hasPassanger() && !citySnapshot.reservedTiles.contains(it)
                if (result) {
                    citySnapshot.reservedTiles = citySnapshot.reservedTiles + it
                }
                result
            }
        ) ?: return ClearPathEvent(car = this.car)
        return SetPathEvent(
            car = this.car,
            path = path
        )
    }

    private var CitySnapshot.reservedTiles: List<CitySnapshot.TileSnapshot>
        get() = RESERVED_PASSENGERS.get(this.blackboard) ?: emptyList()
        set(value) = RESERVED_PASSENGERS.put(this.blackboard, value)

    companion object {
        object RESERVED_PASSENGERS : Blackboard.Key<List<CitySnapshot.TileSnapshot>>
    }
}
