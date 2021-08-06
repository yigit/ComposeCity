package com.birbit.composecity.data

import java.util.*

class RemoveNotificationEvent(
    val notificationId: String
): Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        gameLoop.removeNotification(notificationId)
    }
}



sealed class Notification(
    val id: String = UUID.randomUUID().toString()
) {
    class MoneyMade(
        val amount: Int,
        val pos: Pos
    ) : Notification()

    class MoneyLost(
        val amount: Int,
        val pos:Pos
    ): Notification()
}