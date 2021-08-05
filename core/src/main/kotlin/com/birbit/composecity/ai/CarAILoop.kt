package com.birbit.composecity.ai

import com.birbit.composecity.data.*
import com.birbit.composecity.data.snapshot.CitySnapshot
import kotlinx.coroutines.*

class CarAILoop {
    internal suspend fun doAILoop(
        citySnapshot: CitySnapshot
    ): Event {
        val cars = citySnapshot.cars
        val queue = FairSharedQueue<CitySnapshot.TileSnapshot, List<CitySnapshot.TileSnapshot>?>()
        val events = coroutineScope {
            val deferredEvents = cars.map {
                    async {
                        it.doAILoop(citySnapshot, queue)
                    }
                }
            val keepRunning = CompletableDeferred<Unit>()
            val pump = async {
                queue.execute(keepRunning)
            }
            val events = deferredEvents.awaitAll()
            keepRunning.complete(Unit)
            pump.await()
            events
        }


        return CompositeEvent(events)
    }

    private suspend fun CitySnapshot.CarSnapshot.doAILoop(
        citySnapshot: CitySnapshot,
        queue: FairSharedQueue<CitySnapshot.TileSnapshot, List<CitySnapshot.TileSnapshot>?>
    ): Event {
        val closestTile = citySnapshot.grid.findClosest(pos)

        var path = if(passengerTarget != null) {
            citySnapshot.findPathParallel(
                queue =queue,
                start = closestTile,
                canVisit = {
                    it.content.canCarGo() || it == passengerTarget
                },
                isTarget = {
                    it == passengerTarget
                }
            )
        } else {
            citySnapshot.findPathParallel(
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
            )
        }
        // TODO this close enough logic needs to live in more places
        if (path == null && (closestTile != taxiStation || pos.dist(taxiStation.center) >= Path.CLOSE_ENOUGH)) {
            // go back to the taxi station
            path = citySnapshot.findPathParallel(
                queue = queue,
                start = closestTile,
                canVisit = {
                    it.content.canCarGo() || it == taxiStation || it == closestTile
                },
                isTarget = {
                    it == taxiStation
                }
            )
        }
        return path?.let {
            SetPathEvent(
                carId = this.carId,
                path = it
            )
        } ?: ClearPathEvent(carId = carId)
    }

    private var CitySnapshot.reservedTiles: List<CitySnapshot.TileSnapshot>
        get() = RESERVED_PASSENGERS.get(this.blackboard) ?: emptyList()
        set(value) = RESERVED_PASSENGERS.put(this.blackboard, value)

    companion object {
        object RESERVED_PASSENGERS : Blackboard.Key<List<CitySnapshot.TileSnapshot>>
    }
}
