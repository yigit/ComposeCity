import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.imageFromResource

object ImageCache {
    private val resourceCache = mutableMapOf<String, ImageBitmap>()
    fun loadResource(name:String) : ImageBitmap {
        return resourceCache.getOrPut(name) {
            imageFromResource(name)
        }
    }
}