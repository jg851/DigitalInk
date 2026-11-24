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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
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

private fun translateShapeLabelToChinese(label: String): String {
    return when (label.trim().lowercase()) {
        "star" -> "星星"
        "sun" -> "太陽"
        "moon" -> "月亮"
        "heart" -> "愛心"
        "cloud" -> "雲朵"
        "tree" -> "樹"
        "flower" -> "花"
        "circle" -> "圓形"
        "square" -> "正方形"
        "triangle" -> "三角形"
        "line" -> "直線"
        "house" -> "房子"
        "car" -> "汽車"
        "smiley face", "face" -> "笑臉"
        "lightning" -> "閃電"
        "rainbow" -> "彩虹"
        "apple" -> "蘋果"
        "cat" -> "貓"
        "dog" -> "狗"
        "fish" -> "魚"
        "bird" -> "鳥"
        "cup" -> "杯子"
        "key" -> "鑰匙"
        "umbrella" -> "雨傘"
        else -> "其他圖案（$label）"
    }
}

@Composable
fun Doodle(modifier: Modifier = Modifier) {
    val defaultPrompt = "請在畫布區手寫，再按「開始辨識」按鈕"
    var recognizedText by remember { mutableStateOf(defaultPrompt) }

    // 建立一個路徑物件
    var path by remember { mutableStateOf(Path()) }

    // ML Kit 墨水與筆劃收集器 ----------
    // inkBuilder 負責收集「整頁的全部筆劃」（把多個單一筆劃組合在一起）。
    // strokeBuilder 負責記錄「單一筆劃」（手指從按下到抬起，抬起後就歸零）。
    var inkBuilder by remember {
        mutableStateOf<Ink.Builder>(Ink.builder())
    }

    var strokeBuilder by remember {
        mutableStateOf<Ink.Stroke.Builder?>(null)
    }

    // 記憶 ML Kit 模型與 Recognizer 實體（草圖辨識 zxx-Zsym-x-autodraw）----------
    val modelTag = "zxx-Zsym-x-autodraw"

    // 取得圖案辨識碼
    val modelIdentifier = remember(modelTag) {
        try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(modelTag)
        } catch (e: Exception) {
            null
        }
    }

    // 建立模型物件
    val model = remember(modelIdentifier) {
        modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    // 建立手寫/圖案辨識引擎
    val recognizer = remember(model) {
        model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
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
                            val x = offset.x
                            val y = offset.y
                            val t = System.currentTimeMillis()

                            path.moveTo(x, y)

                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                        },
                        onDrag = { change, dragAmount -> /* 手指移動 */
                            @Suppress("UNUSED_VARIABLE")
                            val dx = dragAmount.x
                            @Suppress("UNUSED_VARIABLE")
                            val dy = dragAmount.y

                            change.consume()

                            val x = change.position.x
                            val y = change.position.y
                            val t = System.currentTimeMillis()
                            path.lineTo(x, y)
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))

                            val tempPath = Path()
                            tempPath.addPath(path)
                            path = tempPath
                        },
                        onDragEnd = { /* 手指離開 */
                            strokeBuilder?.build()?.let { stroke ->
                                inkBuilder.addStroke(stroke)
                            }
                            strokeBuilder = null
                        },
                        onDragCancel = { /* 手勢中斷 */
                            strokeBuilder = null
                        }
                    )
                }
        ) {
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
                    val ink = inkBuilder.build()

                    if (ink.strokes.isEmpty()) {
                        recognizedText = "請先書寫內容"
                        return@Button
                    }

                    recognizedText = "正在處理中..."

                    if (model != null) {
                        RemoteModelManager.getInstance()
                            .download(model, DownloadConditions.Builder().build())
                            .addOnSuccessListener {
                                recognizer?.recognize(ink)
                                    ?.addOnSuccessListener { result ->
                                        if (result.candidates.isNotEmpty()) {
                                            val rawLabel = result.candidates[0].text
                                            val chineseLabel = translateShapeLabelToChinese(rawLabel)
                                            recognizedText = "辨識結果: $chineseLabel"
                                        } else {
                                            recognizedText = "辨識結果: 無法辨識"
                                        }
                                    }
                                    ?.addOnFailureListener { recognizedText = "辨識失敗: ${it.message}" }
                            }
                            .addOnFailureListener { recognizedText = "模型下載失敗" }
                    } else {
                        recognizedText = "模型建立失敗"
                    }
                }
            ) {
                Text("開始辨識")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 清除畫布
            Button(onClick = {
                path = Path()
                inkBuilder = Ink.builder()
                strokeBuilder = null
                recognizedText = "請在畫布區手寫，再按「開始辨識」按鈕"
            }) {
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