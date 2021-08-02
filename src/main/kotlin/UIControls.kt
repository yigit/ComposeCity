import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UIControls {
    private val _mode = MutableStateFlow(Mode.CHANGE_TILE)
    val mode: StateFlow<Mode>
        get() = _mode
    val modeValue: Mode
        get() = _mode.value
    fun setMode(mode: Mode) {
        _mode.value = mode
    }
}

enum class Mode {
    CHANGE_TILE,
    ADD_CAR
}