package com.birbit.composecity.data

import com.curiouscreature.kotlin.math.Float2
import com.curiouscreature.kotlin.math.length
import com.curiouscreature.kotlin.math.normalize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

typealias Pos = Float2
fun createPos(
    row: Float,
    col: Float
) = Pos(
    x = col,
    y = row
)
val Pos.row:Float
    get() = y

val Pos.col:Float
    get() = x

fun Pos.dist(other: Pos) = length(
    other.minus(this)
)

fun Pos.findPos(
    target: Pos,
    distance: Double
): Pos {
    val vector = target.minus(this)
    val normal = normalize(vector)
    val result = this + normal.times(distance.toFloat())
    return result
}
class Car(
    initialPos: Pos = Pos(0f, 0f)
) {
    var speedPerSecond = 30.0
    var targetPath : Path? = null

    @OptIn(ExperimentalTime::class)
    internal fun doGameLoop(city: City, delta: Duration) {
        val path = this.targetPath ?: return
        val radius = speedPerSecond * delta.div(Duration.seconds(1))
        val targetPos = path.target(
            pos = pos.value,
            radius = radius
        ) ?: return
        _pos.value = pos.value.findPos(
            target =  targetPos,
            distance = radius
        )
        //_orientation.value = (_orientation.value + 1f).mod(360f)
    }

    private val _pos = MutableStateFlow<Pos>(initialPos)
    private val _orientation = MutableStateFlow<Float>(0f)

    val pos: StateFlow<Pos>
        get() = _pos

    val orientation: StateFlow<Float>
        get() = _orientation

    companion object {
        val CAR_SIZE = 16
    }
}