// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.animation.animateColorAsState
import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.birbit.composecity.GameTime
import com.birbit.composecity.SetGameSpeedEvent
import com.birbit.composecity.ToggleStartStopGameEvent
import com.birbit.composecity.data.*
import com.birbit.composecity.data.CityMap.Companion.TILE_SIZE
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

@Stable
private data class CameraConfig(
    val scale: Float,
    val tileSizeDp: Dp = (TILE_SIZE * scale).dp,
    val carSizeDp: Dp = (Car.CAR_SIZE * scale).dp,
    val passengerSizeDp: Dp = tileSizeDp / 4,
)

@Composable
@Stable
fun Modifier.coordinatesAbsoluteOffset(
    row: Int,
    col: Int,
) = Modifier.absoluteOffset(
    x = cameraConfig.current.tileSizeDp.times(col),
    y = cameraConfig.current.tileSizeDp.times(row)
)

@Composable
@Stable
fun Modifier.positionAbsoluteOffset(
    pos: Pos,
    centerConstraint: Dp = Dp.Hairline
) = Modifier.absoluteOffset(
    x = (pos.col * cameraConfig.current.scale).dp - centerConstraint.div(2),
    y = (pos.row * cameraConfig.current.scale).dp - centerConstraint.div(2)
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameUI(gameLoop: GameLoop, onExit: () -> Unit) {
    val uiControls = GameUIControls()
    val city = gameLoop.city

    val uiCallbacks = object : ControlCallbacks {
        override fun onExit() {
            onExit()
        }

        override fun onKeyEvent(event: KeyEvent): Boolean {
            if (event.type != KeyEventType.KeyDown) {
                return false
            }
            return when {
                event.key == Key.Spacebar -> {
                    gameLoop.addEvent(ToggleStartStopGameEvent())
                    true
                }
                event.key == Key.Escape -> {
                    onExit()
                    true
                }
                else -> false
            }
        }

        override fun onTaxiMenuClick() {
            uiControls.toggleMode(Mode.ADD_TAXI_STATION)
        }

        override fun onAddCar() {
            uiControls.toggleMode(Mode.ADD_CAR)
        }

        override fun onSetMultipleTilesToRoad(tiles: List<Tile>) {
            gameLoop.addEvent(SetTilesToRoad(tiles))
        }

        override fun onTileClick(tile: Tile) {
            // well, this may not match what user was seeing but is fine for now
            when(tile.content.value) {
                TileContent.Road -> {
                    if (uiControls.modeValue == Mode.CHANGE_TILE) {
                        gameLoop.addEvent(ToggleTileEvent(tile))
                    }
                }
                TileContent.TaxiStation -> {
                    if (uiControls.modeValue == Mode.ADD_CAR) {
                        gameLoop.addEvent(AddCarToStationEvent(tile))
                    }
                }
                TileContent.Grass -> {
                    if (uiControls.modeValue == Mode.ADD_TAXI_STATION) {
                        gameLoop.addEvent(AddTaxiStationEvent(tile))
                    } else {
                        gameLoop.addEvent(SetTilesToRoad(listOf(tile)))
                    }
                }
            }
        }

        override fun onSave() {
            gameLoop.addEvent(
                SaveEvent()
            )
        }

        override fun onSetGameSpeed(speed: GameTime.GameSpeed) {
            gameLoop.addEvent(SetGameSpeedEvent(speed))
        }
    }
    val player = gameLoop.player
    val hasMoney by player.money.map {
        it >= 0
    }.distinctUntilChanged().collectAsState(true)
    DesktopMaterialTheme {
        val focusRequester = remember { FocusRequester() }
        Column(
            modifier = Modifier.fillMaxSize(1f)
                .focusRequester(focusRequester)
                .focusable(true)
                .onKeyEvent(uiCallbacks::onKeyEvent)
        ) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            CityMapUI(gameLoop, city, uiCallbacks, modifier = Modifier.weight(1f))
            ControlsUI(
                controls = uiControls,
                callbacks = uiCallbacks,
                player = player,
                gameTime = gameLoop.gameTime
            )
        }
        if (!hasMoney) {
            LaunchedEffect(Unit) {
                uiCallbacks.onSetGameSpeed(GameTime.GameSpeed.PAUSED)
            }
            OutOfMoneyUI(
                modifier = Modifier.fillMaxSize(1f)
            ) { amount ->
                if (amount < 1) {
                    uiCallbacks.onExit()
                } else {
                    gameLoop.addEvent(
                        AddFreeMoneyEvent(amount)
                    )
                }
            }
        }
    }
}


private interface ControlCallbacks {
    fun onTaxiMenuClick()
    fun onAddCar()
    fun onTileClick(tile: Tile)
    fun onSave()
    fun onSetGameSpeed(speed: GameTime.GameSpeed)
    fun onExit()
    fun onKeyEvent(event: KeyEvent): Boolean
    fun onSetMultipleTilesToRoad(tiles: List<Tile>)
}

@OptIn(ExperimentalTime::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun ControlsUI(
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
    val deliveredPassengers by player.deliveredPassengers.collectAsState()
    val missedPassengers by player.missedPassengers.collectAsState()
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
                Text("$$money")
            },
            label = {
                Text("$deliveredPassengers / $missedPassengers")
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
                Text("add car")
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

private val cameraConfig = compositionLocalOf<CameraConfig> { error("cannot find display config!") }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CityMapUI(
    gameLoop: GameLoop,
    city: City,
    controlCallbacks: ControlCallbacks,
    modifier: Modifier
) {
    val cityMap by city.map.collectAsState()
    val currentCars by city.cars.collectAsState()
    val currentFood by city.passengers.collectAsState()
    BoxWithConstraints(
        modifier = modifier
    ) {
        val density = LocalDensity.current.density

        val currentConfig = remember(
            this.constraints,
            density,
            cityMap
        ) {
            val heightPerTile = this.constraints.maxHeight.toFloat() / cityMap.height
            val widthPerTile = this.constraints.maxWidth.toFloat() / cityMap.width
            val scale = minOf(heightPerTile, widthPerTile) / TILE_SIZE / density
            CameraConfig(scale)
        }
        var mouseDragTiles by remember {
            mutableStateOf(emptyList<Tile>())
        }

        CompositionLocalProvider(
            cameraConfig provides currentConfig
        ) {
            repeat(cityMap.height) { row ->
                repeat(cityMap.width) { col ->
                    TileUI(
                        cityMap = cityMap,
                        tile = cityMap.tiles.get(row = row, col = col),
                        modifier = Modifier.coordinatesAbsoluteOffset(
                            row = row,
                            col = col
                        ).alpha(
                            if (mouseDragTiles.isEmpty()) 1f else 0.5f
                        )
                    )
                }
            }
            currentCars.forEach {
                CarUI(cityMap, it)
            }
            currentFood.forEach {
                PassangerUI(it)
            }
            NotificationsUI(gameLoop)
            MinimapUI(
                cityMap = cityMap,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
            val scale = cameraConfig.current.scale

            Box(modifier = Modifier.fillMaxSize().pointerInput(scale) {
                coroutineScope {
                    launch {
                        detectTapGestures {
                            val pos = it.toPos(scale)
                            cityMap.tiles.findClosestIfInBounds(pos)?.let { tile ->
                                controlCallbacks.onTileClick(tile)
                                mouseDragTiles = emptyList()
                            }

                        }
                    }
                    launch {
                        detectDragGestures(
                            onDragEnd = {
                                if (mouseDragTiles.isNotEmpty()) {
                                    controlCallbacks.onSetMultipleTilesToRoad(mouseDragTiles)
                                }
                                mouseDragTiles = emptyList()
                            }
                        ) { change, dragAmount ->
                            val pos = change.position.toPos(scale)
                            cityMap.tiles.findClosestIfInBounds(pos)?.let { tile ->
                                if (tile.content.value == TileContent.Road ||
                                    tile.content.value == TileContent.Grass
                                ) {
                                    if (!mouseDragTiles.contains(tile)) {
                                        mouseDragTiles = mouseDragTiles + tile
                                    }
                                }
                            }
                        }
                    }
                }
            }) {
                mouseDragTiles.forEach { tile ->
                    Box(
                        modifier = Modifier.coordinatesAbsoluteOffset(
                            row = tile.row,
                            col = tile.col
                        ).size(cameraConfig.current.tileSizeDp)
                            .alpha(0.3f)
                            .background(color = Color.Black)
                    )
                }

            }
        }
    }
}


private fun Passenger.Mood.color() = when (this) {
    Passenger.Mood.NEW -> Color.Black
    Passenger.Mood.OK -> Color.Blue
    Passenger.Mood.GETTING_UPSET -> Color(0xFFFFA500) // orange
    Passenger.Mood.UPSET -> Color.Red
}

@Composable
private fun PassangerUI(
    passenger: Passenger
) {
    val mood by passenger.mood.collectAsState()
    val tintColor by animateColorAsState(
        targetValue = mood.color()
    )
    val inCar by passenger.car.map {
        it != null
    }.collectAsState(false)
    val imageScale = if (inCar) {
        1f
    } else {
        2f
    }
    val pos by passenger.pos.collectAsState()
    Image(
        bitmap = ImageCache.loadResource("passenger.png"),
        colorFilter = ColorFilter.tint(
            color = tintColor
        ),
        contentDescription = "passenger",
        modifier = Modifier.positionAbsoluteOffset(
            pos = if (inCar) {
                pos + 10f
            } else {
                pos
            },
            centerConstraint = cameraConfig.current.passengerSizeDp.times(imageScale)
        ).scale(
            imageScale
        ),

        )
}

@Composable
private fun NotificationsUI(
    gameLoop: GameLoop
) {
    val notifications by gameLoop.notifications.collectAsState()
    notifications.forEach { notification ->
        when (notification) {
            is Notification.MoneyMade -> MoneyChangeNotificationUI(
                gameLoop = gameLoop,
                notificationId = notification.id,
                backgroundColor = MaterialTheme.colors.primary,
                textColor = MaterialTheme.colors.onPrimary,
                amount = notification.amount,
                pos = notification.pos
            )
            is Notification.MoneyLost -> MoneyChangeNotificationUI(
                gameLoop = gameLoop,
                notificationId = notification.id,
                backgroundColor = Color.Red,
                textColor = Color.White,
                amount = notification.amount,
                pos = notification.pos
            )
        }
    }
}

@Composable
private fun MoneyChangeNotificationUI(
    gameLoop: GameLoop,
    notificationId: String,
    backgroundColor: Color,
    textColor: Color,
    pos: Pos,
    amount: Int,
) {
    // TODO maybe use transition to also fade out?
    val dy = remember {
        androidx.compose.animation.core.Animatable(initialValue = 0f)
    }
    Card(
        backgroundColor = backgroundColor,
        elevation = 4.dp,
        modifier = Modifier.positionAbsoluteOffset(
            pos = Pos(pos.x, pos.y + dy.value)
        ).padding(12.dp)
    ) {
        Text(
            text = "$${amount}",
            color = textColor
        )
    }
    LaunchedEffect(notificationId) {
        dy.animateTo(-TILE_SIZE)
        delay(1_000)
        gameLoop.addEvent(RemoveNotificationEvent(notificationId))
    }
}

@Composable
private fun CarUI(
    cityMap: CityMap,
    car: Car
) {
    val pos by car.pos.collectAsState()
    val rotation by car.orientation.collectAsState()
    Image(
        bitmap = ImageCache.loadResource("car.png"),
        contentDescription = "car",
        modifier = Modifier.positionAbsoluteOffset(
            pos = pos,
            centerConstraint = cameraConfig.current.carSizeDp
        ).size(cameraConfig.current.carSizeDp).rotate(
            rotation
        )
    )
}

@Stable
private fun Dp.roundUp(): Dp {
    return ceil(this.value).dp
}

@Composable
private fun TileUI(
    cityMap: CityMap,
    tile: Tile,
    modifier: Modifier = Modifier
) {
    val content by tile.content.collectAsState()
    Box(
        modifier = modifier.size(cameraConfig.current.tileSizeDp.roundUp())
    ) {
        when (content) {
            TileContent.Grass -> Image(
                bitmap = ImageCache.loadResource("grass.png"),
                contentDescription = "grass",
                modifier = Modifier.fillMaxSize(1f)
            )
            TileContent.Business -> Image(
                bitmap = ImageCache.loadResource("business.png"),
                contentDescription = "business",
                modifier = Modifier.fillMaxSize(1f)
            )
            TileContent.Road -> Image(
                bitmap = getTileBitmap(
                    cityMap = cityMap.tiles,
                    tile = tile
                ),
                contentDescription = "road",
                modifier = Modifier.fillMaxSize(1f)
            )
            TileContent.House -> Image(
                bitmap = ImageCache.loadResource("house.png"),
                colorFilter = ColorFilter.tint(
                    color = Color.Red,
                ),
                contentDescription = "house",
                modifier = Modifier.fillMaxSize(1f)
            )
            TileContent.TaxiStation -> Image(
                bitmap = ImageCache.loadResource("taxi-station.png"),
                contentDescription = "taxi station",
                modifier = Modifier.fillMaxSize(1f)
            )
        }
    }
}


private val baseState = MutableStateFlow(TileContent.Grass)
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

private val roadAssetMapping = mutableMapOf(
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

private fun Offset.toPos(scale: Float) = Pos(x = x / scale, y = y / scale)