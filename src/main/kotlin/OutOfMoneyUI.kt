import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private enum class MenuUI{
    FIRST,
    ADD_MONEY
}
@Composable
fun OutOfMoneyUI(
    modifier: Modifier,
    exit: (addedMoney: Int) -> Unit
) {
    var ui by remember {
        mutableStateOf(MenuUI.FIRST)
    }
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = modifier.fillMaxWidth(
                .6f
            ).wrapContentSize().background(
                color = MaterialTheme.colors.surface
            ).zIndex(1f).shadow(4.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            when(ui) {
                MenuUI.FIRST -> {
                    Text(
                        text = "Oh no, you've run out of money",
                        style = MaterialTheme.typography.h3
                    )
                    Button(
                        onClick = {
                            ui = MenuUI.ADD_MONEY
                        }
                    ) {
                        Text(
                            text = "Buy money :)"
                        )
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(
                        onClick = {
                            exit(0)
                        }
                    ) {
                        Text(
                            text = "N'ah I'm cheap"
                        )
                    }
                }
                MenuUI.ADD_MONEY -> {
                    Text(
                        text = "Just kidding... how much do you want?",
                        style = MaterialTheme.typography.h3
                    )
                    listOf(500, 1000, 2000).forEach { amount ->
                        Button(
                            onClick = {
                                exit(amount)
                            }
                        ) {
                            Text(
                                text = "$$amount"
                            )
                        }
                        Spacer(modifier = Modifier.size(16.dp))
                    }

                }
            }
        }
    }
}