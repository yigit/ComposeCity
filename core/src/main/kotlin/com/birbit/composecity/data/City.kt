package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class City(
    val map: CityMap
) {
    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    private val _passangers = MutableStateFlow<List<Passanger>>(emptyList())
    val cars: StateFlow<List<Car>>
        get() = _cars
    val passangers: StateFlow<List<Passanger>>
        get() = _passangers

    fun addCar(car: Car) {
        _cars.value = _cars.value + car
    }

    fun addPassanger(passanger: Passanger) {
        _passangers.value = _passangers.value + passanger
    }

    fun removePassanger(passanger: Passanger) {
        _passangers.value = _passangers.value.filter { it != passanger }
    }
}