package com.birbit.composecity.data

import com.birbit.composecity.data.snapshot.CitySnapshot
import com.curiouscreature.kotlin.math.*
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
    val normal = normalize(vector)
    val result = this + normal.times(distance.toFloat())
    return result
}
class Car(
    initialPos: Pos = Pos(0f, 0f)
) {
    var speedPerSecond = 100.0
    var targetPath : Path? = null

    @OptIn(ExperimentalTime::class)
    internal fun doGameLoop(city: City, delta: Duration) {
        val path = this.targetPath ?: return
        val radius = speedPerSecond * delta.div(Duration.seconds(1))
        val targetTile = path.target(
            pos = pos.value,
            radius = radius
        ) ?: return
        if (targetTile.contentValue != TileContent.Road) {
            targetPath = null
            return
        }
        val diff = targetTile.center - pos.value
        _orientation.value = (degrees(atan2(y = diff.y, x = diff.x)) + 90).mod(360f)
        _pos.value = pos.value.findPos(
            target =  targetTile.center,
            distance = radius
        )
    }
//
//    internal fun doAILoop(citySnapshot: CitySnapshot): Event? {
//        // find closest food
//        if (!citySnapshot.hasFood) {
//            return ClearPathEvent(car = this)
//        }
//        // this is dump, instead we need some sorth of prioritization here, or some collective AI?
//        val reservedTiles = citySnapshot.blackboard.get(Blackboard.Key.RESERVED_FOOD) ?: emptyList()
//
//        val closestTile = citySnapshot.grid.findClosest(pos.value)
//        val path = citySnapshot.findPath(
//            start = closestTile,
//            canVisit = {
//                it.content.canCarGo()
//            },
//            isTarget = {
//                it.content == CitySnapshot.Content.FOOD &&
//                        !reservedTiles.contains(it)
//            }
//        ) ?: return ClearPathEvent(car = this)
//        citySnapshot.blackboard.put(Blackboard.Key.RESERVED_FOOD,
//        reservedTiles + path.last())
//        return SetPathEvent(
//            car = this,
//            path = path
//        )
//    }

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

class ClearPathEvent(
    val car: Car
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        car.targetPath = null
    }
}

class SetPathEvent(
    val car: Car,
    val path: List<CitySnapshot.TileSnapshot>
) : Event {
    override fun apply(gameLoop: GameLoop, city: City) {
        // TODO set this more cleverly because our car might've moved closer to the next path points if AI took a long
        //  time
        car.targetPath = Path(path.map {
            city.map.tiles.get(
                row = it.row,
                col = it.col
            )
        })
    }

}