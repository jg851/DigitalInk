package tw.edu.pu.csim.tcyang.digitalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.digitalink.recognition.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Doodle(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Doodle(modifier: Modifier = Modifier) {
    val defaultPrompt = "請在畫布區手寫，再按「開始辨識」按鈕"
    var recognizedText by remember { mutableStateOf(defaultPrompt) }
    var strokeCount by remember { mutableIntStateOf(0) }
    var hasActiveStroke by remember { mutableStateOf(false) }

    // 建立一個路徑物件（使用 apply 簡化，保留初始三角形）
    var path by remember {
        mutableStateOf(
            Path().apply {
                moveTo(50f, 100f)  // 移動到起始點 (50f, 100f)
                lineTo(200f, 500f) // 畫一條線到 (200f, 500f)
                lineTo(450f, 400f) // 畫一條線到 (450f, 400f)
                close()            // 閉合路徑
            }
        )
    }

    // ML Kit 墨水與筆劃收集器 ----------
    // inkBuilder 負責收集「整頁的全部筆劃」（把多個單一筆劃組合在一起）。
    // strokeBuilder 負責記錄「單一筆劃」（手指從按下到抬起，抬起後就歸零）。
    var inkBuilder by remember {
        mutableStateOf<Ink.Builder>(Ink.builder())
    }

    var strokeBuilder by remember {
        mutableStateOf<Ink.Stroke.Builder?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .border(2.dp, Color.Gray)
                .clipToBounds()
                .pointerInput(Unit) {
                    // 監聽拖曳手勢
                    detectDragGestures(
                        onDragStart = { offset -> /* 手指按下 */
                            // 按下：記錄 Path 起點與建立單一筆劃收集器
                            val x = offset.x
                            val y = offset.y
                            val t = System.currentTimeMillis()
                            path.moveTo(x, y)
                            strokeBuilder = Ink.Stroke.builder().apply {
                                addPoint(Ink.Point.create(x, y, t))
                            }
                            hasActiveStroke = true
                        },
                        onDrag = { change, dragAmount -> /* 手指移動 */
                            @Suppress("UNUSED_VARIABLE")
                            val dx = dragAmount.x
                            @Suppress("UNUSED_VARIABLE")
                            val dy = dragAmount.y

                            // 消費拖曳事件（避免影響父層元件手勢）
                            // 這個移動手勢已經被 Canvas 處理，請不要再傳給外層的其他元件
                            change.consume()

                            // 移動：拉線並加入點座標
                            val x = change.position.x
                            val y = change.position.y
                            val t = System.currentTimeMillis()
                            path.lineTo(x, y)
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))

                            // 複製出一個內容一模一樣、但記憶體位址全新的 Path 物件
                            // 並指派回 path，藉此強制 Compose 重繪 Canvas 畫面
                            val tempPath = Path()
                            tempPath.addPath(path)
                            path = tempPath
                        },
                        onDragEnd = { /* 手指離開 */
                            strokeBuilder?.let { builder ->
                                inkBuilder.addStroke(builder.build())
                            }
                            strokeBuilder = null
                            if (hasActiveStroke) {
                                strokeCount++
                                hasActiveStroke = false
                            }
                        },
                        onDragCancel = { /* 手勢中斷 */
                            strokeBuilder = null
                            hasActiveStroke = false
                        }
                    )
                }
        ) {
            // 繪製黑色路徑，透明度 1（不透明），線條粗細 10
            drawPath(
                path = path,
                color = Color.Black,
                alpha = 1f,
                style = Stroke(
                    width = 10f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 開始辨識
            Button(
                onClick = {
                    recognizedText = if (strokeCount == 0) {
                        "尚未偵測到筆畫，請先在畫布區手寫"
                    } else {
                        val ink = inkBuilder.build()
                        "已收集 ${ink.strokes.size} 筆手寫軌跡，準備辨識..."
                    }
                }
            ) {
                Text("開始辨識")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 清除畫布
            Button(
                onClick = {
                    path = Path()
                    strokeCount = 0
                    hasActiveStroke = false
                    strokeBuilder = null
                    inkBuilder = Ink.builder()
                    recognizedText = defaultPrompt
                }
            ) {
                Text("清除")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = recognizedText,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}