package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

class Player(
    initialMoney: Int
) {
    private val _money = MutableStateFlow(initialMoney)
    private var _pendingDistance = 0f

    val money: StateFlow<Int>
        get() = _money

    fun onDeliveredPassenger(passenger: Passenger) {
        _money.value += computeDeliveryFee(passenger)
    }

    fun onDistanceTraveledByCars(distance: Float) {
        _pendingDistance += distance
        val cost = (_pendingDistance / FUEL_COST_BUCKET).roundToInt()
        if (cost > 0) {
            _money.value -= cost
            _pendingDistance -= FUEL_COST_BUCKET * cost
        }

    }

    private fun computeDeliveryFee(passenger: Passenger): Int {
        val distance = passenger.initialPos.dist(passenger.pos.value) / 10
        return TRIP_BASE_COST + distance.roundToInt().coerceAtLeast(1)
    }

    fun deductMoney(
        amount: Int,
        action: () -> Unit
    ): Boolean {
        if (_money.value < amount) return false
        _money.value -= amount
        action()
        return true
    }

    companion object {
        // TODO we should have rent for each taxi station. that requires some game time :) but that would help fine tune
        //  economy
        private val TRIP_BASE_COST = 5
        private val FUEL_COST_BUCKET = 75
        const val COST_OF_TAXI_STATION = 750
        const val COST_OF_ROAD = 5
    }

}