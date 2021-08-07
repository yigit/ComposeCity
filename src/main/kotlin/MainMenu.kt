import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

interface MainMenuCallbacks {
    fun onNewGame()
    fun onLoadGame()
}

@Preview
@Composable
fun MainMenuUI(
    callbacks: MainMenuCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(
            .6f
        ).background(
            color = MaterialTheme.colors.surface
        ).zIndex(1f).shadow(4.dp).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(
            onClick = callbacks::onNewGame
        ) {
            Text(
                text = "New Game"
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Button(
            onClick = callbacks::onLoadGame
        ) {
            Text(
                text = "Load Game"
            )
        }
    }

}