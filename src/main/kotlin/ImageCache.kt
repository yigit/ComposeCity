import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource

object ImageCache {
    private val resourceCache = mutableMapOf<String, ImageBitmap>()
    fun loadResource(name:String) : ImageBitmap {
        return resourceCache.getOrPut(name) {
            useResource(name) { loadImageBitmap(it) }
        }
    }
}