package com.birbit.composecity.data

import com.birbit.composecity.Id
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Passenger constructor(
    val id: Id,
    pos: Pos,
    val target: Tile,
    val creationTime: Duration,
    car: Car? = null
) {
    val initialPos = pos
    private val _pos = MutableStateFlow(pos)
    val pos: StateFlow<Pos>
        get() = _pos

    private val _car = MutableStateFlow(car)

    val car: StateFlow<Car?>
        get() = _car

    private val _mood = MutableStateFlow(Mood.NEW)

    val mood: StateFlow<Mood>
        get() = _mood

    fun setCar(car: Car?) {
        _car.value = car
    }

    @OptIn(ExperimentalTime::class)
    fun doGameLoop(city: City, delta: Duration) {
        _car.value?.let {
            _pos.value = it.pos.value
        }
    }

    fun setMood(mood: Mood) {
        _mood.value = mood
    }

    fun setPos(pos: Pos) {
        _pos.value = pos
    }

    enum class Mood {
        NEW,
        OK,
        GETTING_UPSET,
        UPSET
    }
}