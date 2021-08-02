package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class City(
    val map: CityMap
) {
    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    val cars: StateFlow<List<Car>>
        get() = _cars

    fun addCar(car: Car) {
        _cars.value = _cars.value + car
    }
}