import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.birbit.composecity.data.City
import com.birbit.composecity.data.Tile
import com.birbit.composecity.data.TileContent

private val MINIMAP_SIZE = 4.dp

@Composable
fun MinimapUI(
    city: City,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.width(
            MINIMAP_SIZE.times(city.map.width)
        ).height(
            MINIMAP_SIZE.times(city.map.height)
        ).shadow(elevation = 4.dp).border(
            width = 3.dp,
            color = Color.Black
        )
    ) {
        repeat(city.map.width) { col ->
            repeat(city.map.height) { row ->
                val tile = city.map.tiles.get(row = row, col = col)
                MinimapTile(
                    tile = tile,
                    modifier = Modifier.size(MINIMAP_SIZE).absoluteOffset(
                        x = MINIMAP_SIZE.times(col),
                        y = MINIMAP_SIZE.times(row)
                    )
                )

            }
        }
    }
}

@Composable
private fun MinimapTile(
    tile: Tile,
    modifier: Modifier
) {
    val content by tile.content.collectAsState()
    val color = when(content) {
        TileContent.Road -> Color.Gray
        TileContent.Grass -> Color.Green
        TileContent.House -> Color.Red
        TileContent.Business -> Color.Blue
        else -> Color.White
    }
    Box(
        modifier = modifier.background(color = color)
    )
}