package com.birbit.composecity.data

import com.birbit.composecity.Id
import com.birbit.composecity.data.snapshot.CitySnapshot
import com.curiouscreature.kotlin.math.Float2
import com.curiouscreature.kotlin.math.degrees
import com.curiouscreature.kotlin.math.length
import com.curiouscreature.kotlin.math.normalize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
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
    val available = minOf(distance, length(vector).toDouble())
    if (available < 0.2) {
        return this
    }
    val normal = normalize(vector)
    return this + normal.times(available.toFloat())
}
class Car(
    val id:Id,
    initialPos: Pos = Pos(0f, 0f),
    val taxiStation: Tile
) {
    var speedPerMinute = 20.0
    var passenger: Passenger? = null
    var targetPath : Path? = null

    @OptIn(ExperimentalTime::class)
    internal fun doGameLoop(city: City, delta: Duration) {
        val path = this.targetPath ?: return
        var remaining = speedPerMinute * delta.div(Duration.minutes(1))
        do {
            val targetPos = path.target(
                pos = pos.value
            ) ?: return
            val closestTile = city.map.value.tiles.findClosest(targetPos)
            if (closestTile.content.value != TileContent.Road && closestTile.content.value != TileContent.Business &&
                closestTile != taxiStation) {
                targetPath = null
                return
            }
            val diff = targetPos - pos.value
            _orientation.value = (degrees(atan2(y = diff.y, x = diff.x)) + 90).mod(360f)
            val prevPosition = _pos.value
            val newPos = pos.value.findPos(
                target = targetPos,
                distance = remaining
            )
            _pos.value = newPos
            val consumedLength = newPos.dist(prevPosition)
            remaining -= consumedLength
        } while(remaining > 1)

    }

    private val _pos = MutableStateFlow(initialPos)
    private val _orientation = MutableStateFlow(0f)

    val pos: StateFlow<Pos>
        get() = _pos

    val orientation: StateFlow<Float>
        get() = _orientation

    companion object {
        val CAR_SIZE = 32
    }
}

class ClearPathEvent(
    private val carId: Id
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        val car = city.cars.value.firstOrNull {
            it.id == carId
        } ?: return
        car.targetPath = null
    }
}

class SetPathEvent(
    val carId: Id,
    val path: List<CitySnapshot.TileSnapshot>
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        // TODO set this more cleverly because our car might've moved closer to the next path points if AI took a long
        //  time
        val car = city.cars.value.firstOrNull {
            it.id == carId
        } ?: return
        car.targetPath = Path(path.map {
            it.center
        })
    }

}