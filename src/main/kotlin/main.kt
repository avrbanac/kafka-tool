import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ui.App
import java.awt.Dimension
import javax.imageio.ImageIO

fun main() {
    val iconPainter: BitmapPainter? = Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream("icon.png")
        ?.let { stream -> BitmapPainter(ImageIO.read(stream).toComposeImageBitmap()) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kafka Tool",
            icon = iconPainter,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        ) {
            SideEffect { window.minimumSize = Dimension(1280, 800) }
            App()
        }
    }
}
