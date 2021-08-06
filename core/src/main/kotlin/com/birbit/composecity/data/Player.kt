package com.birbit.composecity.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

class Player(
    initialMoney: Int,
    deliveredPassengers: Int,
    missedPassengers: Int
) {
    private val _money = MutableStateFlow(initialMoney)
    private val _deliveredPassengers = MutableStateFlow(deliveredPassengers)
    private val _missedPassengers = MutableStateFlow(missedPassengers)
    private var _pendingDistance = 0f

    val money: StateFlow<Int>
        get() = _money

    val deliveredPassengers: StateFlow<Int>
        get() = _deliveredPassengers

    val missedPassengers: StateFlow<Int>
        get() = _missedPassengers

    fun onDeliveredPassenger(gameLoop: GameLoop, passenger: Passenger) {
        _deliveredPassengers.value += 1
        val fee = computeDeliveryFee(passenger)
        _money.value += fee
        gameLoop.addNotification(Notification.MoneyMade(fee, passenger.pos.value))
    }

    fun onMissedPassenger(gameLoop: GameLoop, passenger: Passenger) {
        _missedPassengers.value += 1
        deductFailedPassengerPenalty(gameLoop, passenger)

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
        val distance = passenger.initialPos.dist(passenger.pos.value) / 20
        val tip = when(passenger.mood.value) {
            Passenger.Mood.NEW -> 10
            Passenger.Mood.OK -> 3
            else -> 0
        }
        return TRIP_BASE_COST + tip + distance.roundToInt().coerceAtLeast(1)
    }

    fun deductFailedPassengerPenalty(gameLoop: GameLoop, passenger: Passenger) {
        _money.value -= FAILED_PASSENGER_PENALTY
        gameLoop.addNotification(Notification.MoneyLost(amount = -FAILED_PASSENGER_PENALTY, pos = passenger.pos.value))
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
        const val COST_OF_CAR = 100
        const val FAILED_PASSENGER_PENALTY = 80
    }

}