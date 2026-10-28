package ch.hygro.padelscore.minigame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PadelPongScreen(onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    val context = LocalContext.current
    val storage = remember(context) { PadelPongStorage(context) }
    val engine = remember { PadelPongEngine() }
    val focusRequester = remember { FocusRequester() }
    // =====================================================
// ROTARY CONTROL SETTINGS
// =====================================================
//
// Empfindlichkeit der drehbaren Krone.
//
// 0.0002f = sehr fein
// 0.0003f = fein (empfohlen fuer Galaxy Watch 4)
// 0.0005f = mittel
// 0.0010f = schnell
// 0.0015f = sehr schnell
//
// Kleinerer Wert = kleinere Schlaegerbewegung.
// Groesserer Wert = groessere Schlaegerbewegung.
//
// Nur diesen Wert anpassen, um die Steuerung zu aendern.

    val rotarySensitivity = 0.0005f
    var highScore by remember { mutableIntStateOf(storage.highScore()) }
    // Eigener beobachtbarer Punktestand. Verhindert, dass Text von Canvas-Redraw abhaengt.
    var displayedScore by remember { mutableIntStateOf(engine.score) }
    var gameOver by remember { mutableStateOf(engine.gameOver) }
    // Einzig fuer das Neuzeichnen von Ball, Gegnern und Schlaeger.
    var frame by remember { mutableIntStateOf(0) }

    val lifecycleOwner = context as LifecycleOwner
    var isForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(isForeground) {
        var previous = 0L
        while (isForeground) {
            withFrameNanos { now ->
                if (previous != 0L && !engine.gameOver) {
                    val hit = engine.step((now - previous) / 1_000_000_000f)
                    // Immer direkt vom Engine-Score in Compose-State kopieren.
                    displayedScore = engine.score
                    if (hit || engine.gameOver) highScore = storage.record(engine.score)
                    gameOver = engine.gameOver
                    frame++
                }
                previous = now
            }
            if (engine.gameOver) delay(100.milliseconds)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .onSizeChanged { engine.setFieldSize(it.width.toFloat(), it.height.toFloat()) }
            .background(Color.Black)
            .onRotaryScrollEvent {
                engine.movePaddleBy(it.verticalScrollPixels * rotarySensitivity)
                frame++
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    engine.movePaddleTo(change.position.x / size.width.toFloat())
                    frame++
                    change.consume()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (engine.gameOver) {
                        engine.reset()
                        displayedScore = 0
                        gameOver = false
                        frame++
                    }
                })
            }
    ) {
        val redraw = frame
        Canvas(Modifier.fillMaxSize()) {
            // frame ist hier eine beobachtete Eingabe fuer die Draw-Phase.
            if (redraw >= 0) {
                val w = size.width
                val h = size.height
                val scale = min(w, h)
                val left = PadelPongEngine.COURT_LEFT * w
                val right = PadelPongEngine.COURT_RIGHT * w
                val top = PadelPongEngine.COURT_TOP * h
                val bottom = PadelPongEngine.COURT_BOTTOM * h
                val width = right - left
                val height = bottom - top
                val court = Color(0xFF164D63)
                val line = Color(0xFFB4E5F1)

                drawRoundRect(court, Offset(left, top), Size(width, height), CornerRadius(4.dp.toPx()))
                drawRoundRect(line, Offset(left, top), Size(width, height),
                    CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawLine(line.copy(alpha = 0.55f), Offset(left, h * 0.32f), Offset(right, h * 0.32f), 1.dp.toPx())
                drawLine(line.copy(alpha = 0.55f), Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.5f), 1.dp.toPx())
                drawLine(Color.White, Offset(left, h * 0.5f), Offset(right, h * 0.5f), 2.dp.toPx())
                drawLine(color = line.copy(alpha = 0.55f), start = Offset(left, h * 0.64f), end = Offset(right, h * 0.64f), strokeWidth = 1.dp.toPx())
                drawLine(color = line.copy(alpha = 0.55f), start = Offset(w * 0.5f, h * 0.5f), end = Offset(w * 0.5f, h * 0.64f), strokeWidth = 1.dp.toPx())
                for (i in 1..10) {
                    val x = left + width * i / 11f
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(x, h * 0.49f), Offset(x, h * 0.51f), 1.dp.toPx())
                }
                for ((x, y) in engine.obstaclePositions) {
                    drawCircle(Color(0xFFFF6B62), scale * PadelPongEngine.OPPONENT_RADIUS, Offset(x * w, y * h))
                    drawCircle(Color.White, scale * 0.017f, Offset(x * w, y * h))
                }
                drawCircle(Color(0xFF70FF80), scale * PadelPongEngine.BALL_RADIUS,
                    Offset(engine.ballX * w, engine.ballY * h))
                drawRoundRect(Color.White,
                    Offset((engine.paddleX - PadelPongEngine.PADDLE_HALF_WIDTH) * w,
                        PadelPongEngine.PADDLE_Y * h),
                    Size(PadelPongEngine.PADDLE_HALF_WIDTH * 2f * w,
                        PadelPongEngine.PADDLE_HEIGHT * h),
                    CornerRadius(5.dp.toPx()))
            }
        }
        Text("PADEL PONG", modifier = Modifier.align(Alignment.TopCenter).padding(top = 13.dp),
            color = Color(0xFF70FF80), fontSize = 11.sp, fontWeight = FontWeight.Bold)

        // Score unter dem Court. Padding statt absoluter Y-Koordinate.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 21.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SCORE $displayedScore  |  BEST $highScore", color = Color.White, fontSize = 10.sp)
        }
        if (gameOver) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GAME OVER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("TAP TO RETRY", color = Color(0xFF70FF80), fontSize = 10.sp)
            }
        }
    }
}
