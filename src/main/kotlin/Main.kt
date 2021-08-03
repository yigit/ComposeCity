// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.birbit.composecity.data.*
import kotlinx.coroutines.flow.MutableStateFlow

val SCALE = 1f
val TILE_SIZE_DP = (CityMap.TILE_SIZE * SCALE).dp
val CAR_SIZE_DP = (Car.CAR_SIZE * SCALE).dp
val PASSENGER_SIZE_DP = TILE_SIZE_DP / 4
fun main() = Window {
    val uiControls = UIControls()

    val gameLoop = GameLoop()
    gameLoop.start()
    val city by gameLoop.city.collectAsState()

    val uiCallbacks = object : ControlCallbacks {
        override fun onCarMenuClick() {
            if (uiControls.modeValue == Mode.ADD_CAR) {
                uiControls.setMode(Mode.CHANGE_TILE)
            } else {
                uiControls.setMode(Mode.ADD_CAR)
            }
        }

        override fun onBusinessMenuClick() {
            if (uiControls.modeValue == Mode.ADD_BUSINESS) {
                uiControls.setMode(Mode.CHANGE_TILE)
            } else {
                uiControls.setMode(Mode.ADD_BUSINESS)
            }
        }

        override fun onTileClick(tile: Tile) {
            if (uiControls.modeValue == Mode.CHANGE_TILE) {
                gameLoop.addEvent(ToggleTileEvent(tile))
            } else if (uiControls.modeValue == Mode.ADD_CAR && tile.contentValue == TileContent.Road) {
                gameLoop.addEvent(AddCarEvent(tile))
            } else if (uiControls.modeValue == Mode.ADD_BUSINESS) {
                gameLoop.addEvent(ToggleBusinessEvent(tile))
            }
        }

        override fun onSave() {
            gameLoop.addEvent(
                SaveEvent()
            )
        }

        override fun onLoad() {
            gameLoop.addEvent(LoadEvent())
        }

        override fun onAddPassanger() {
            gameLoop.addEvent(AddPassangerEvent())
        }
    }
    MaterialTheme {
        Box {
            CityMapUI(city, uiCallbacks)
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
    fun onBusinessMenuClick()
    fun onTileClick(tile: Tile)
    fun onSave()
    fun onLoad()
    fun onAddPassanger()
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
                            MaterialTheme.colors.onPrimary
                        } else {
                            MaterialTheme.colors.primaryVariant
                        }
                    )
                )
            }
            Button(
                onClick = callbacks::onBusinessMenuClick
            ) {
                Image(
                    modifier = Modifier.size(TILE_SIZE_DP / 2),
                    bitmap = ImageCache.loadResource("business.png"),
                    contentDescription = "add business",
                    colorFilter = ColorFilter.tint(
                        color = if (mode == Mode.ADD_BUSINESS) {
                            MaterialTheme.colors.onPrimary
                        } else {
                            MaterialTheme.colors.primaryVariant
                        }
                    )
                )
            }
            Button(
                onClick = callbacks::onAddPassanger
            ) {
                Text(text = "Add Passenger")
            }
            Button(
                onClick = callbacks::onSave
            ) {
                Text(text = "Save")
            }
            Button(
                onClick = callbacks::onLoad
            ) {
                Text(text = "Load")
            }
        }
    }
}

@Composable
fun CityMapUI(
    city: City,
    controlCallbacks: ControlCallbacks
) {
    val cityMap = city.map
    val currentCars by city.cars.collectAsState()
    val currentFood by city.passangers.collectAsState()
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
        currentFood.forEach {
            PassangerUI(cityMap, it)
        }
    }
}

@Composable
fun PassangerUI(
    cityMap: CityMap,
    passanger: Passanger
) {
    Image(
        bitmap = ImageCache.loadResource("passenger.png"),
        colorFilter = ColorFilter.tint(
            color = Color.Blue
        ),
        contentDescription = "passenger",
        modifier = Modifier.absoluteOffset(
            // TODO food size
            x = (passanger.pos.x * SCALE).dp - PASSENGER_SIZE_DP,
            y = (passanger.pos.y * SCALE).dp - PASSENGER_SIZE_DP
        ),
    )
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
            TileContent.Business -> Image(
                bitmap = ImageCache.loadResource("business.png"),
                contentDescription = "business",
            )
            TileContent.Road -> Image(
                bitmap = getTileBitmap(
                    cityMap = cityMap.tiles,
                    tile = tile
                ),
                contentDescription = "road",
            )
            TileContent.OutOfBounds -> {
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
//    check(tile.contentValue == TileContent.Road) {
//        "why are we getting bitmap for ${tile.contentValue}"
//    }
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
