package ch.hygro.padelscore.minigame

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PadelPongEngine {
    companion object {
        // SPIEL-EINSTELLUNGEN: Werte zwischen 0f und 1f sind Bildschirmanteile.
        const val COURT_LEFT = 0.16f       // Linker Spielfeldrand (16 %)
        const val COURT_RIGHT = 0.84f      // Rechter Spielfeldrand (84 %)
        const val COURT_TOP = 0.16f        // Oberer Spielfeldrand
        const val COURT_BOTTOM = 0.80f     // Unterer Spielfeldrand; Platz fuer Score darunter
        const val PADDLE_Y = COURT_BOTTOM - 0.04f         // Vertikale Schlaegerposition
        const val PADDLE_HALF_WIDTH = 0.08f // Halbe Schlaegerbreite: 0.10 = 20 % insgesamt
        const val PADDLE_HEIGHT = 0.018f   // Schlaegerdicke relativ zur Hoehe
        const val BALL_RADIUS = 0.016f     // Ballradius relativ zur kuerzeren Displayseite
        const val OPPONENT_RADIUS = 0.044f // Radius der beiden roten Hindernisse
        const val START_SPEED_X = 0.22f    // Seitliche Ballgeschwindigkeit pro Sekunde
        const val START_SPEED_Y = 0.52f    // Vertikale Ballgeschwindigkeit pro Sekunde
        const val SPEED_INCREASE = 0.08f   // +8 % pro 5 Treffer
        const val MAX_SPEED_FACTOR = 2.50f // Obergrenze fuer Beschleunigung
        const val BOUNCE_STEERING = 0.30f  // Einfluss der Trefferposition auf den Winkel
    }

    var ballX = 0.5f
        private set
    var ballY = 0.22f
        private set
    var paddleX = 0.5f
        private set
    var score = 0
        private set
    var gameOver = false
        private set

    private var fieldWidth = 1f
    private var fieldHeight = 1f
    private var velocityX = START_SPEED_X
    private var velocityY = START_SPEED_Y

    private class Obstacle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    private val obstacles = listOf(
        Obstacle(0.35f, 0.35f, 0.07f, 0.05f, COURT_LEFT, 0.5f, COURT_TOP, 0.48f),
        Obstacle(0.65f, 0.40f, -0.05f, 0.08f, 0.5f, COURT_RIGHT, COURT_TOP, 0.48f)
    )

    // Gegnerpositionen: X/Y als Bildschirmanteile.
    val obstaclePositions: List<Pair<Float, Float>>
        get() = obstacles.map { it.x to it.y }

    fun setFieldSize(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            fieldWidth = width
            fieldHeight = height
            movePaddleTo(paddleX)
        }
    }

    fun reset() {
        ballX = 0.5f
        ballY = 0.22f
        paddleX = 0.5f
        score = 0
        gameOver = false
        velocityX = START_SPEED_X
        velocityY = START_SPEED_Y
        
        obstacles[0].apply { x = 0.35f; y = 0.35f; vx = 0.07f; vy = 0.05f }
        obstacles[1].apply { x = 0.65f; y = 0.40f; vx = -0.05f; vy = 0.08f }
    }

    fun movePaddleTo(x: Float) {
        paddleX = x.coerceIn(COURT_LEFT + PADDLE_HALF_WIDTH, COURT_RIGHT - PADDLE_HALF_WIDTH)
    }

    fun movePaddleBy(delta: Float) = movePaddleTo(paddleX + delta)

    // Liefert true, wenn genau ein erfolgreicher Schlaegertreffer erfolgt ist.
    fun step(dtSeconds: Float): Boolean {
        if (gameOver) return false
        val dt = dtSeconds.coerceIn(0f, 0.05f)
        val steps = max(1, (dt / 0.005f).toInt() + 1)
        val subDt = dt / steps
        val ballRadiusPx = min(fieldWidth, fieldHeight) * BALL_RADIUS
        val rx = ballRadiusPx / fieldWidth
        val ry = ballRadiusPx / fieldHeight
        val opponentRadiusPx = min(fieldWidth, fieldHeight) * OPPONENT_RADIUS
        val contactY = PADDLE_Y - ry

        repeat(steps) {
            val previousX = ballX
            val previousY = ballY
            ballX += velocityX * subDt
            ballY += velocityY * subDt

            val obsRx = opponentRadiusPx / fieldWidth
            val obsRy = opponentRadiusPx / fieldHeight

            for (obs in obstacles) {
                obs.x += obs.vx * subDt
                obs.y += obs.vy * subDt

                if (obs.x - obsRx < obs.minX) {
                    obs.x = obs.minX + obsRx
                    obs.vx = abs(obs.vx)
                } else if (obs.x + obsRx > obs.maxX) {
                    obs.x = obs.maxX - obsRx
                    obs.vx = -abs(obs.vx)
                }

                if (obs.y - obsRy < obs.minY) {
                    obs.y = obs.minY + obsRy
                    obs.vy = abs(obs.vy)
                } else if (obs.y + obsRy > obs.maxY) {
                    obs.y = obs.maxY - obsRy
                    obs.vy = -abs(obs.vy)
                }
            }

            // Schlaeger VOR den Waenden pruefen, sonst werden Treffer verschluckt.
            if (velocityY > 0f && previousY < contactY && ballY >= contactY) {
                val t = ((contactY - previousY) / (ballY - previousY)).coerceIn(0f, 1f)
                val impactX = previousX + (ballX - previousX) * t
                if (abs(impactX - paddleX) <= PADDLE_HALF_WIDTH + rx) {
                    ballX = impactX
                    ballY = contactY
                    score += 1
                    val factor = (1f + (score / 5) * SPEED_INCREASE).coerceAtMost(MAX_SPEED_FACTOR)
                    velocityY = -START_SPEED_Y * factor
                    val offset = ((impactX - paddleX) / PADDLE_HALF_WIDTH).coerceIn(-1f, 1f)
                    velocityX = (offset * BOUNCE_STEERING + if (offset >= 0f) 0.07f else -0.07f) * factor
                    return true
                }
                gameOver = true
                return false
            }

            if (ballX - rx < COURT_LEFT) {
                ballX = COURT_LEFT + rx
                velocityX = abs(velocityX)
            } else if (ballX + rx > COURT_RIGHT) {
                ballX = COURT_RIGHT - rx
                velocityX = -abs(velocityX)
            }
            if (ballY - ry < COURT_TOP) {
                ballY = COURT_TOP + ry
                velocityY = abs(velocityY)
            }

            // Kreisfoermige Gegner, mit Reflexion in echten Pixelkoordinaten.
            for (obs in obstacles) {
                val ox = obs.x
                val oy = obs.y
                val dx = (ballX - ox) * fieldWidth
                val dy = (ballY - oy) * fieldHeight
                val distance = hypot(dx, dy)
                val minimumDistance = opponentRadiusPx + ballRadiusPx
                if (distance < minimumDistance) {
                    val nx = if (distance > 0.0001f) dx / distance else 0f
                    val ny = if (distance > 0.0001f) dy / distance else 1f
                    ballX = ox + nx * minimumDistance / fieldWidth
                    ballY = oy + ny * minimumDistance / fieldHeight
                    val vx = velocityX * fieldWidth
                    val vy = velocityY * fieldHeight
                    val dot = vx * nx + vy * ny
                    if (dot < 0f) {
                        velocityX = (vx - 2f * dot * nx) / fieldWidth
                        velocityY = (vy - 2f * dot * ny) / fieldHeight
                    }
                }
            }
        }
        return false
    }
}
