import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.birbit.composecity.data.*
import com.birbit.composecity.data.serialization.LoadSave
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private class GameController {

    val currentGame = MutableStateFlow<GameLoop?>(null)
    fun exitCurrentGame() {
        val current = currentGame.value ?: return
        currentGame.value = null
        current.close()
    }
    val mainMenuCallbacks = object : MainMenuCallbacks {
        override fun onNewGame() {
            val gameLoop = GameLoop(
                player = Player(
                    initialMoney = 1000,
                    deliveredPassengers = 0,
                    missedPassengers = 0
                ),
                city = City(
                    map = CityMapImpl(width = 30, height = 20)
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

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application{
    val windowState = rememberWindowState(width = Dp.Unspecified, height = Dp.Unspecified)
    Window(
        onCloseRequest = {exitApplication()},
        title = "Compose City",
        state = windowState,
    ) {
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
                    GameUI(game) {
                        gameController.exitCurrentGame()
                    }
                }
            }
        }
    }
}