import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.singleWindowApplication
import com.birbit.composecity.data.*
import com.birbit.composecity.data.serialization.LoadSave
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private enum class CurrentScreen {
    MENU,
    GAME
}

@OptIn(ExperimentalTime::class)
private class GameController {
    val currentScreen = MutableStateFlow(CurrentScreen.MENU)
    val currentGame = MutableStateFlow<GameLoop?>(null)

    val mainMenuCallbacks = object : MainMenuCallbacks {
        override fun onNewGame() {
            val gameLoop = GameLoop(
                player = Player(1000),
                city = City(
                    map = CityMap(width = 20, height = 20)
                ),
                startDuration = Duration.ZERO
            )
            currentGame.value = gameLoop
        }

        override fun onLoadGame() {
            LoadSave.load(File(SAVE_FILE_NAME))?.let {
                val (city, player, startDuration) = it.create()
                val gameLoop = GameLoop(
                    player = player,
                    city = city,
                    startDuration = startDuration
                )
                currentGame.value = gameLoop
            }
        }

    }

}

private val gameController = GameController()

fun main() = application{
    val windowState = rememberWindowState(width = Dp.Unspecified, height = Dp.Unspecified)
    Window(
        onCloseRequest = {exitApplication()},
        title = "Compose City",
        state = windowState,
    ) {
        val currentScreen by gameController.currentScreen.collectAsState()
        val gameLoop by gameController.currentGame.collectAsState()
        val game = gameLoop
        DesktopMaterialTheme {
            when {
                game == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MainMenuUI(
                            callbacks = gameController.mainMenuCallbacks
                        )
                    }
                }
                else -> {
                    GameUI(game)
                }
            }
        }
    }
}