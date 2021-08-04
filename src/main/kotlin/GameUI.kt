// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.birbit.composecity.GameTime
import com.birbit.composecity.SetGameSpeedEvent
import com.birbit.composecity.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.DecimalFormat
import java.util.*
import kotlin.time.ExperimentalTime

val SCALE = 1f
val TILE_SIZE_DP = (CityMap.TILE_SIZE * SCALE).dp
val CAR_SIZE_DP = (Car.CAR_SIZE * SCALE).dp
val PASSENGER_SIZE_DP = TILE_SIZE_DP / 4

@Composable
fun GameUI(gameLoop: GameLoop, onExit: () -> Unit) {
    val uiControls = GameUIControls()
    val city = gameLoop.city

    val uiCallbacks = object : ControlCallbacks {
        override fun onExit() {
            onExit()
        }
        
        override fun onTaxiMenuClick() {
            uiControls.toggleMode(Mode.ADD_TAXI_STATION)
        }

        override fun onAddCar() {
            uiControls.toggleMode(Mode.ADD_CAR)
        }

        override fun onTileClick(tile: Tile) {
            if (uiControls.modeValue == Mode.CHANGE_TILE) {
                gameLoop.addEvent(ToggleTileEvent(tile))
            } else if (uiControls.modeValue == Mode.ADD_CAR) {
                gameLoop.addEvent(AddCarToStationEvent(tile))
            } else if (uiControls.modeValue == Mode.ADD_BUSINESS) {
                gameLoop.addEvent(CreateBusinessEvent(tile))
            } else if (uiControls.modeValue == Mode.ADD_TAXI_STATION) {
                gameLoop.addEvent(AddTaxiStationEvent(tile))
            }
        }

        override fun onSave() {
            gameLoop.addEvent(
                SaveEvent()
            )
        }

        override fun onAddPassanger() {
            gameLoop.addEvent(AddPassangerEvent())
        }

        override fun onSetGameSpeed(speed: GameTime.GameSpeed) {
            gameLoop.addEvent(SetGameSpeedEvent(speed))
        }
    }
    val player = gameLoop.player
    DesktopMaterialTheme {
        Box {
            CityMapUI(city, uiCallbacks)
            ControlsUI(
                controls = uiControls,
                callbacks = uiCallbacks,
                modifier = Modifier.align(Alignment.BottomCenter),
                player = player,
                gameTime = gameLoop.gameTime
            )
        }

    }
}


interface ControlCallbacks {
    fun onTaxiMenuClick()
    fun onAddCar()
    fun onTileClick(tile: Tile)
    fun onSave()
    fun onAddPassanger()
    fun onSetGameSpeed(speed: GameTime.GameSpeed)
    fun onExit()
}

@OptIn(ExperimentalTime::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun ControlsUI(
    controls: GameUIControls,
    gameTime: GameTime,
    callbacks: ControlCallbacks,
    player: Player,
    modifier: Modifier = Modifier
) {
    val mode by controls.mode.collectAsState()
    val money by player.money.collectAsState()
    var showGameSpeeds by mutableStateOf(false)
    val passedTime by gameTime.passedTIme
        .map {
            val days = it.inWholeDays + 1
            val hours = it.inWholeHours % 24
            val minutes = it.inWholeMinutes % 60
            String.format("Day %d, %02d:%02d", days, hours.toInt(), minutes.toInt())
        }
        .collectAsState("00:00")
    val gameSpeed by gameTime.gameSpeed.collectAsState()
    // TODO navigation rail si not available, where is it?
    BottomNavigation(
        modifier = modifier
    ) {
        BottomNavigationItem(
            selected = false,
            onClick = {},
            enabled = false,
            alwaysShowLabel = true,
            icon = {
                Text("$money")
            },
            label = {
                Text("$$$$")
            }
        )
        BottomNavigationItem(
            selected = false,
            onClick = {
                showGameSpeeds = !showGameSpeeds
            },
            enabled = true,
            alwaysShowLabel = false,
            icon = {
                Text(passedTime)
            },
            label = {

            }
        )
        if (showGameSpeeds) {
            GameTime.GameSpeed.values().forEach { speed ->
                val resourceName = "game-speed-" + when (speed) {
                    GameTime.GameSpeed.NORMAL -> "normal"
                    GameTime.GameSpeed.PAUSED -> "paused"
                    GameTime.GameSpeed.FAST -> "fast"
                    GameTime.GameSpeed.SLOW -> "slow"
                } + ".png"
                BottomNavigationItem(
                    selected = gameSpeed == speed,
                    onClick = {
                        callbacks.onSetGameSpeed(speed)
                    },
                    enabled = speed != gameSpeed,
                    alwaysShowLabel = false,
                    icon = {
                        Icon(
                            bitmap = ImageCache.loadResource(resourceName),
                            contentDescription = speed.name
                        )
                    },
                    label = {
                        Text(speed.name.lowercase(Locale.US))
                    }
                )
            }
        }
        BottomNavigationItem(
            selected = mode == Mode.ADD_TAXI_STATION,
            onClick = callbacks::onTaxiMenuClick,
            alwaysShowLabel = false,
            icon = {
                Icon(
                    bitmap = ImageCache.loadResource("taxi-station.png"),
                    contentDescription = "add taxi",
                    modifier = Modifier.fillMaxSize(.5f)
                )
            },
            enabled = money > Player.COST_OF_TAXI_STATION,
            label = {
                Text("add taxi station")
            }
        )
        BottomNavigationItem(
            selected = mode == Mode.ADD_CAR,
            onClick = callbacks::onAddCar,
            alwaysShowLabel = false,
            icon = {
                Icon(
                    bitmap = ImageCache.loadResource("car.png"),
                    contentDescription = "add car"
                )
            },
            enabled = true,
            label = {
                Text("add business")
            }
        )
        BottomNavigationItem(
            selected = false,
            onClick = callbacks::onSave,
            icon = {
                Text("SAVE")
            },
            alwaysShowLabel = false,
            enabled = true,
            label = {
            }
        )
        BottomNavigationItem(
            selected = false,
            onClick = callbacks::onExit,
            icon = {
                Text("EXIT")
            },
            alwaysShowLabel = false,
            enabled = true,
            label = {
            }
        )
    }
}

val timeFormat = DecimalFormat("$$")


@Composable
fun CityMapUI(
    city: City,
    controlCallbacks: ControlCallbacks
) {
    val cityMap = city.map
    val currentCars by city.cars.collectAsState()
    val currentFood by city.passengers.collectAsState()
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
    passenger: Passenger
) {
    val pos by passenger.pos.collectAsState()
    Image(
        bitmap = ImageCache.loadResource("passenger.png"),
        colorFilter = ColorFilter.tint(
            color = Color.Blue
        ),
        contentDescription = "passenger",
        modifier = Modifier.absoluteOffset(
            // TODO food size
            x = (pos.x * SCALE).dp - PASSENGER_SIZE_DP,
            y = (pos.y * SCALE).dp - PASSENGER_SIZE_DP
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
            TileContent.TaxiStation -> Image(
                bitmap = ImageCache.loadResource("taxi-station.png"),
                contentDescription = "taxi station"
            )
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
