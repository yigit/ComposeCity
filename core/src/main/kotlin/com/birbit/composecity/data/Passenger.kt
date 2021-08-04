package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class Passenger(
    pos: Pos,
    val target: Tile,
    car: Car? = null
) {
    val initialPos = pos
    private val _pos = MutableStateFlow(pos)
    val pos: StateFlow<Pos>
        get() = _pos

    private val _car = MutableStateFlow(car)

    val car: StateFlow<Car?>
        get() = _car

    fun setCar(car: Car?) {
        _car.value = car
    }

    @OptIn(ExperimentalTime::class)
    fun doGameLoop(city: City, delta: Duration) {
        _car.value?.let {
            _pos.value = it.pos.value
        }
    }
}