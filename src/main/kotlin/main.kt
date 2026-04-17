import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ui.App
import java.awt.Dimension
import javax.imageio.ImageIO

private val logger: Logger = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
    configureLogLevel(args)
    logger.info("Kafka Tool 1.2.0 starting")

    val iconPainter: BitmapPainter? = Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream("icon.png")
        ?.let { stream -> BitmapPainter(ImageIO.read(stream).toComposeImageBitmap()) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kafka Tool 1.2.0",
            icon = iconPainter,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        ) {
            SideEffect { window.minimumSize = Dimension(1280, 800) }
            App()
        }
    }
}

private fun configureLogLevel(args: Array<String>) {
    val level: Level = when {
        args.contains("-vv") -> Level.DEBUG
        args.contains("-v") -> Level.INFO
        else -> return
    }
    val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).level = level
}
