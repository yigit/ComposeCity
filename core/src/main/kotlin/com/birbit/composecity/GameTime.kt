package com.birbit.composecity

import com.birbit.composecity.data.City
import com.birbit.composecity.data.Event
import com.birbit.composecity.data.GameLoop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class ToggleStartStopGameEvent : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        if (gameLoop.gameTime.gameSpeed.value == GameTime.GameSpeed.PAUSED) {
            gameLoop.gameTime.setGameSpeed(GameTime.GameSpeed.NORMAL)
        } else {
            gameLoop.gameTime.setGameSpeed(GameTime.GameSpeed.PAUSED)
        }
    }
}
class SetGameSpeedEvent(
    val gameSpeed: GameTime.GameSpeed
): Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        gameLoop.gameTime.setGameSpeed(gameSpeed)
    }
}

@OptIn(ExperimentalTime::class)
class GameTime(
    startTime: Duration = START_TIME
) {
    private val _now = MutableStateFlow(startTime)
    val passedTIme = _now.map {
        it - START_TIME
    }


    private val _gameSpeed = MutableStateFlow(GameSpeed.PAUSED)

    val gameSpeed: StateFlow<GameSpeed>
        get() = _gameSpeed

    internal fun setGameSpeed(gameSpeed: GameSpeed) {
        _gameSpeed.value = gameSpeed
    }

    val now: StateFlow<Duration>
        get() = _now

    /**
     * Returns time passed
     */
    internal fun gameTick(): Duration {
        val tickDuration = _gameSpeed.value.tickDuration
        _now.value += tickDuration
        return tickDuration
    }

    companion object {
        private val START_TIME = Duration.ZERO
    }

    enum class GameSpeed(
        val tickDuration: Duration
    ) {
        PAUSED(tickDuration = Duration.ZERO),
        SLOW(tickDuration = Duration.seconds(5)),
        NORMAL(tickDuration = Duration.seconds(10)),
        FAST(tickDuration = Duration.seconds(20))
    }
}