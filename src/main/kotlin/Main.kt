// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.birbit.composecity.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.forEach

val SCALE = 1f
val TILE_SIZE_DP = (CityMap.TILE_SIZE * SCALE).dp
val CAR_SIZE_DP = (Car.CAR_SIZE * SCALE).dp

fun main() = Window {
    val uiControls = UIControls()
    val city = City(
        map = CityMap(width = 20, height = 20)
    )
    val gameLoop = GameLoop(city)
    gameLoop.start()

    val uiCallbacks = object : ControlCallbacks {
        override fun onCarMenuClick() {
            if (uiControls.modeValue == Mode.ADD_CAR) {
                uiControls.setMode(Mode.CHANGE_TILE)
            } else {
                uiControls.setMode(Mode.ADD_CAR)
            }
        }

        override fun onTileClick(tile: Tile) {
            if (uiControls.modeValue == Mode.CHANGE_TILE) {
                gameLoop.addEvent(ToggleTileEvent(tile))
            } else if (uiControls.modeValue == Mode.ADD_CAR && tile.contentValue == TileContent.Road) {
                gameLoop.addEvent(AddCarEvent(tile))
            }
        }
    }
    MaterialTheme {
        Box {
            CityMapUI(city.map, city.cars, uiCallbacks)
            ControlsUI(
                controls = uiControls,
                callbacks = uiCallbacks,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

    }
}

interface ControlCallbacks {
    fun onCarMenuClick()
    fun onTileClick(tile: Tile)
}

@Composable
fun ControlsUI(
    controls: UIControls,
    callbacks: ControlCallbacks,
    modifier: Modifier = Modifier
) {
    val mode by controls.mode.collectAsState()

    Box(
        modifier = modifier.fillMaxWidth(.8f).height(TILE_SIZE_DP)
    ) {
        Row(
            modifier = Modifier.wrapContentSize(
                align = Alignment.Center
            ).background(
                Color.LightGray
            )
        ) {
            Button(
                onClick = callbacks::onCarMenuClick
            ) {
                Image(
                    bitmap = ImageCache.loadResource("car.png"),
                    contentDescription = "toggle car",
                    colorFilter = ColorFilter.tint(
                        color = if (mode == Mode.ADD_CAR) {
                            Color.Black
                        } else {
                            Color.Gray
                        }
                    )
                )
            }

        }
    }
}

@Composable
fun CityMapUI(
    cityMap: CityMap,
    cars: StateFlow<List<Car>>,
    controlCallbacks: ControlCallbacks
) {
    val currentCars by cars.collectAsState()
    Box {
        Column {
            repeat(cityMap.height) { row ->
                Row {
                    repeat(cityMap.width) { col ->
                        TileUI(
                            cityMap,
                            cityMap.tiles.get(row = row, col = col),
                            controlCallbacks::onTileClick
                        )
                    }
                }
            }
        }
        currentCars.forEach {
            CarUI(cityMap, it)
        }
    }
}

@Composable
fun CarUI(
    cityMap: CityMap,
    car: Car
) {
    val pos by car.pos.collectAsState()
    val rotation by car.orientation.collectAsState()
    Image(
        bitmap = ImageCache.loadResource("car.png"),
        contentDescription = "car",
        modifier = Modifier.absoluteOffset(
            x = (pos.col * SCALE).dp - CAR_SIZE_DP,
            y = (pos.row * SCALE).dp - CAR_SIZE_DP
        ).rotate(
            rotation
        )
    )
}

@Composable
fun TileUI(
    cityMap: CityMap,
    tile: Tile,
    onClickHandler: (Tile) -> Unit
) {
    val content by tile.content.collectAsState()
    Box(
        modifier = Modifier.size(TILE_SIZE_DP).clickable {
            onClickHandler(tile)
        }
    ) {
        when (content) {
            TileContent.Grass -> Box(
                modifier = Modifier.background(Color.Green).fillMaxSize()
            )
            is TileContent.Road -> Image(
                bitmap = getTileBitmap(
                    cityMap = cityMap.tiles,
                    tile = tile
                ),
                contentDescription = null,
            )
            is TileContent.OutOfBounds -> {
                // nada
            }
        }

    }
}

private val baseState = MutableStateFlow(TileContent.OutOfBounds)
private fun TileContent.roadMask(shift: Int) = if (isRoad()) {
    1.shl(shift)
} else {
    0
}

private fun TileContent.isRoad() = this == TileContent.Road

@Composable
private fun getTileBitmap(
    cityMap: Grid<Tile>,
    tile: Tile
): ImageBitmap {
    check(tile.contentValue == TileContent.Road)
    val north by (cityMap.maybeGet(
        tile.row - 1,
        tile.col
    )?.content ?: baseState).collectAsState()
    val south by (cityMap.maybeGet(
        tile.row + 1,
        tile.col
    )?.content ?: baseState).collectAsState()
    val west by (cityMap.maybeGet(
        tile.row,
        tile.col - 1
    )?.content ?: baseState).collectAsState()
    val east by (cityMap.maybeGet(
        tile.row,
        tile.col + 1
    )?.content ?: baseState).collectAsState()
    val mask = north.roadMask(0)
        .or(
            west.roadMask(1)
        )
        .or(
            south.roadMask(2)
        ).or(
            east.roadMask(3)
        )
    return ImageCache.loadResource(
        roadAssetMapping[mask] ?: "sample.png"
    )
}

private val roadAssetMapping = mutableMapOf<Int, String>(
    0b1111 to "4way.png",
    0b1110 to "all-but-north.png",
    0b1101 to "all-but-west.png",
    0b1011 to "all-but-south.png",
    0b0111 to "all-but-east.png",
    0b0011 to "north-west.png",
    0b1001 to "north-east.png",
    0b0110 to "south-west.png",
    0b1100 to "south-east.png",
    0b0101 to "vertical.png",
    0b0100 to "vertical.png",
    0b0001 to "vertical.png",
    0b1010 to "horizontal.png",
    0b0010 to "horizontal.png",
    0b1000 to "horizontal.png",
)
