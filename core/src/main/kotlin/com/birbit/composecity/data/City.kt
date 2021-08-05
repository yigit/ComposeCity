package com.birbit.composecity.data

import com.birbit.composecity.Id
import com.birbit.composecity.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TODO: what would help us clean things is that we would have two interfaces of each class one of which provides
 * mutability such that we can enclose mutations.
 */
class City(
    map: CityMapImpl,
    startId: Id = Id(0)
) {
    private val _map = map
    val map: CityMap = map
    internal var businessTiles: List<Tile> = map.tiles.data.filter {
        it.content.value == TileContent.Business
    }
        private set

    internal val idGenerator: IdGenerator = IdGenerator(startId)
    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    private val _passengers = MutableStateFlow<List<Passenger>>(emptyList())
    val cars: StateFlow<List<Car>>
        get() = _cars
    val passengers: StateFlow<List<Passenger>>
        get() = _passengers

    fun addCar(car: Car) {
        _cars.value = _cars.value + car
    }

    fun addPassenger(passenger: Passenger) {
        _passengers.value = _passengers.value + passenger
    }

    fun addBusiness(tile: Tile): Boolean {
        val mutableTile = _map.mutableTiles.get(row = tile.row, col = tile.col)
        if (mutableTile.content.value == TileContent.Grass) {
            mutableTile.content.value = TileContent.Business
            businessTiles = businessTiles + tile
            return true
        }
        return false
    }

    fun associatePassengerWithCar(
        passenger: Passenger,
        car: Car
    ) {
        car.passenger = passenger
        passenger.setCar(car)
    }

    fun removePassenger(passenger: Passenger) {
        _passengers.value = _passengers.value.filter { it != passenger }
        passenger.car.value?.let {  car ->
            car.passenger = null
        }
    }

    fun removeUnpickedPassenger(passenger: Passenger) {
        _passengers.value -= passenger
    }

    fun toggleTile(player: Player, tile: Tile) {
        val mutableTile = _map.mutableTiles.get(row = tile.row, col = tile.col)
        if (tile.content.value == TileContent.Grass) {
            player.deductMoney(Player.COST_OF_ROAD) {
                mutableTile.content.value = TileContent.Road
            }
        } else if (mutableTile.content.value == TileContent.Road) {
            mutableTile.content.value = TileContent.Grass
        }
    }

    fun addTaxiStation(player: Player, tile: Tile) {
        @Suppress("NAME_SHADOWING")
        val tile = _map.mutableTiles.get(row = tile.row, col = tile.col)
        if (tile.content.value != TileContent.Grass) {
            return
        }
        player.deductMoney(Player.COST_OF_TAXI_STATION) {
            tile.content.value = TileContent.TaxiStation
            val newCar = Car(
                id = idGenerator.nextId(),
                initialPos = tile.center,
                taxiStation = tile
            )
            addCar(newCar)
        }
    }
}