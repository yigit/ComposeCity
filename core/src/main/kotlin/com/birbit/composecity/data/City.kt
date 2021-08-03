package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class City(
    val map: CityMap
) {
    private val _cars = MutableStateFlow<List<Car>>(emptyList())
    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    val cars: StateFlow<List<Car>>
        get() = _cars
    val foods: StateFlow<List<Food>>
        get() = _foods

    fun addCar(car: Car) {
        _cars.value = _cars.value + car
    }

    fun addFood(food: Food) {
        _foods.value = _foods.value + food
    }

    fun removeFood(food: Food) {
        _foods.value = _foods.value.filter { it != food }
    }
}