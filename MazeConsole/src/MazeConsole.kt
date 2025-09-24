import kotlin.random.Random
import kotlin.system.exitProcess

// ======================
// Maze game (console) in Kotlin
// - Move with N,S,E,W (cardinal points)
// - Each level generates a new maze (harder)
// - Limited number of maps; win after clearing all
// - Singleton map creator using `object`
// - Stopwatch runs on a separate Thread
// ======================

// ---- Data classes ----
data class Position(var r: Int, var c: Int)

data class Maze(
    val rows: Int,
    val cols: Int,
    val grid: Array<CharArray>,
    val start: Position,
    val exit: Position
)

// ---- Singleton maze creator (only one instance!) ----
object MazeFactory {
    private val rnd = Random(System.nanoTime())

    fun create(requestedRows: Int, requestedCols: Int): Maze {
        val rows = if (requestedRows % 2 == 0) requestedRows + 1 else requestedRows
        val cols = if (requestedCols % 2 == 0) requestedCols + 1 else requestedCols

        val grid = Array(rows) { CharArray(cols) { '█' } }

        // Carve passages using iterative DFS (recursive backtracker) on odd coordinates
        val startR = randomOdd(1, rows - 2)
        val startC = randomOdd(1, cols - 2)
        grid[startR][startC] = ' '

        val stack = ArrayDeque<Position>()
        stack.addLast(Position(startR, startC))

        val dirs = arrayOf(
            intArrayOf(-2, 0), // N
            intArrayOf(2, 0),  // S
            intArrayOf(0, -2), // W
            intArrayOf(0, 2)   // E
        )

        while (stack.isNotEmpty()) {
            val current = stack.last()
            val neighbors = mutableListOf<Position>()

            for (d in dirs) {
                val nr = current.r + d[0]
                val nc = current.c + d[1]
                if (nr in 1 until rows - 1 && nc in 1 until cols - 1 && grid[nr][nc] == '█') {
                    neighbors.add(Position(nr, nc))
                }
            }

            if (neighbors.isNotEmpty()) {
                neighbors.shuffle(rnd)
                val next = neighbors.first()
                val wallR = (current.r + next.r) / 2
                val wallC = (current.c + next.c) / 2
                grid[wallR][wallC] = ' '
                grid[next.r][next.c] = ' '
                stack.addLast(next)
            } else {
                stack.removeLast()
            }
        }

        // Create entrance (left) and exit (right)
        val possibleStarts = (1 until rows - 1).filter { grid[it][1] == ' ' }
        val sRow = if (possibleStarts.isNotEmpty()) possibleStarts.random(rnd) else 1
        grid[sRow][0] = ' ' // opening on the border
        val start = Position(sRow, 1)

        val possibleExits = (1 until rows - 1).filter { grid[it][cols - 2] == ' ' }
        val eRow = if (possibleExits.isNotEmpty()) possibleExits.random(rnd) else rows - 2
        grid[eRow][cols - 1] = ' ' // opening on the border
        val exit = Position(eRow, cols - 2)

        return Maze(rows, cols, grid, start, exit)
    }

    private fun randomOdd(minInclusive: Int, maxInclusive: Int): Int {
        var x = rnd.nextInt(minInclusive, maxInclusive + 1)
        if (x % 2 == 0) x += if (x + 1 <= maxInclusive) 1 else -1
        return x
    }
}

// ---- Stopwatch running on a Thread ----
class Stopwatch {
    @Volatile private var running = false
    @Volatile var elapsedSeconds: Int = 0
        private set
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        elapsedSeconds = 0
        thread = Thread {
            try {
                while (running) {
                    Thread.sleep(1000)
                    elapsedSeconds += 1
                }
            } catch (_: InterruptedException) {}
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }
}

// ---- Game controller ----
object Game {
    private const val TOTAL_LEVELS = 3
    private const val BASE_ROWS = 15
    private const val BASE_COLS = 29
    private const val SIZE_STEP = 6 // increase hardness by making the maze bigger each level

    private val stopwatch = Stopwatch()

    private fun clearScreen() {
        // ANSI clear; works on most terminals
        print("\u001b[H\u001b[2J")
        System.out.flush()
    }

    private fun draw(maze: Maze, player: Position, level: Int) {
        clearScreen()
        println("🧭 Labirinto – N/S/E/W para mover, Q para sair")
        println("Nível: $level / $TOTAL_LEVELS    Tempo: ${formatTime(stopwatch.elapsedSeconds)}")
        println()
        for (r in 0 until maze.rows) {
            val line = StringBuilder()
            for (c in 0 until maze.cols) {
                val ch = maze.grid[r][c]
                val toDraw = when {
                    r == player.r && c == player.c -> 'P'
                    r == maze.start.r && c == maze.start.c -> 'S'
                    r == maze.exit.r && c == maze.exit.c -> 'E'
                    else -> ch
                }
                line.append(toDraw)
            }
            println(line.toString())
        }
        println()
        println("Comandos: W (norte), S (sul), A (leste), D (oeste), Q (sair)")
    }

    private fun moveIfPossible(dir: Char, maze: Maze, player: Position) {
        val (dr, dc) = when (dir.uppercaseChar()) {
            'W' -> -1 to 0
            'S' -> 1 to 0
            'A' -> 0 to -1
            'D' -> 0 to 1
            else -> 0 to 0
        }
        val nr = player.r + dr
        val nc = player.c + dc
        if (nr in 0 until maze.rows && nc in 0 until maze.cols && maze.grid[nr][nc] != '█') {
            player.r = nr
            player.c = nc
        }
    }

    private fun levelSize(level: Int): Pair<Int, Int> {
        val rows = BASE_ROWS + (level - 1) * SIZE_STEP
        val cols = BASE_COLS + (level - 1) * SIZE_STEP
        return rows to cols
    }

    private fun formatTime(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    fun run() {
        clearScreen()
        println("==== JOGO DO LABIRINTO (Kotlin) ====")
        println("Regras: use N,S,E,W para se mover. Alcance a saída 'E' em cada nível.")
        println("Existe uma única classe criadora de mapas (singleton): MazeFactory.")
        println("Pressione ENTER para começar…")
        readLine()

        stopwatch.start()

        for (level in 1..TOTAL_LEVELS) {
            val (rows, cols) = levelSize(level)
            val maze = MazeFactory.create(rows, cols)
            val player = Position(maze.start.r, maze.start.c)

            while (true) {
                draw(maze, player, level)
                print("> ")
                val input = readLine()?.trim()?.uppercase() ?: ""
                if (input.isEmpty()) continue
                val ch = input.first()
                if (ch == 'Q') {
                    stopwatch.stop()
                    println("Saindo… Tempo total: ${formatTime(stopwatch.elapsedSeconds)}")
                    exitProcess(0)
                }
                moveIfPossible(ch, maze, player)

                if (player.r == maze.exit.r && player.c == maze.exit.c) {
                    // Level cleared
                    draw(maze, player, level)
                    println("\n✅ Você encontrou a saída do nível $level!")
                    if (level < TOTAL_LEVELS) {
                        println("Gerando próximo mapa… Pressione ENTER para continuar.")
                        readLine()
                    }
                    break
                }
            }
        }

        stopwatch.stop()
        clearScreen()
        println("🏆 PARABÉNS! Você venceu todos os $TOTAL_LEVELS labirintos!")
        println("Tempo total: ${formatTime(stopwatch.elapsedSeconds)}")
        println("Obrigado por jogar.")
    }
}

fun main() {
    Game.run()
}

