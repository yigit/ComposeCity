package com.birbit.composecity.data

import com.birbit.composecity.Id
import com.birbit.composecity.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    internal var houseTiles: List<Tile> = map.tiles.data.filter {
        it.content.value == TileContent.House
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

    fun addHouse(tile: Tile): Boolean {
        val mutableTile = _map.mutableTiles.get(row = tile.row, col = tile.col)
        if (mutableTile.content.value == TileContent.Grass) {
            mutableTile.content.value = TileContent.House
            houseTiles = houseTiles + mutableTile
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

    fun toggleTile(gameLoop: GameLoop, player: Player, tile: Tile) {
        val mutableTile = _map.mutableTiles.get(row = tile.row, col = tile.col)
        if (tile.content.value == TileContent.Grass) {
            player.deductMoney(Player.COST_OF_ROAD) {
                mutableTile.content.value = TileContent.Road
                gameLoop.addNotification(Notification.MoneyLost(amount = Player.COST_OF_ROAD, pos = tile.center))
            }
        } else if (mutableTile.content.value == TileContent.Road) {
            mutableTile.content.value = TileContent.Grass
        }
    }

    fun setTilesToRoad(gameLoop: GameLoop, player: Player, tiles: List<Tile>) {
        val valid = tiles.filter {
            it.content.value == TileContent.Grass
        }.map {
            _map.mutableTiles.get(row = it.row, col = it.col)
        }
        if (valid.isEmpty()) return
        val cost = valid.size * Player.COST_OF_ROAD
        player.deductMoney(amount = cost) {
            valid.forEach {
                it.content.value = TileContent.Road
            }
            gameLoop.addNotification(
                Notification.MoneyLost(
                    amount = cost,
                    pos = valid.last().center
                )
            )
        }
    }

    fun addTaxiStation(gameLoop: GameLoop, player: Player, tile: Tile) {
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
            gameLoop.addNotification(Notification.MoneyLost(amount = Player.COST_OF_TAXI_STATION, pos = tile.center))
        }
    }
}