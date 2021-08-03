package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class City(
    val map: CityMap
) {
    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    private val _passangers = MutableStateFlow<List<Passenger>>(emptyList())
    val cars: StateFlow<List<Car>>
        get() = _cars
    val passangers: StateFlow<List<Passenger>>
        get() = _passangers

    fun addCar(car: Car) {
        _cars.value = _cars.value + car
    }

    fun addPassanger(passenger: Passenger) {
        _passangers.value = _passangers.value + passenger
    }

    fun removePassanger(passenger: Passenger) {
        _passangers.value = _passangers.value.filter { it != passenger }
        passenger.car.value?.let {  car ->
            car.passenger = null
        }
    }
}