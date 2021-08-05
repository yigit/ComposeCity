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
    val map: CityMap,
    startId: Id = Id(0)
) {
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
}