package uz.videoavto

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFFB9A8FF))
    } else {
        lightColorScheme(primary = Color(0xFF6C4CF1))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val working = vm.status is Status.Working

    DisposableEffect(working) {
        view.keepScreenOn = working
        onDispose { view.keepScreenOn = false }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setAudio(uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addImages(uris)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Video Avto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Ovoz + rasmlar → tayyor video. Rasmlar ovozdagi pauzalarga qarab avtomatik joylashadi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            SectionCard("1. Ovoz fayli") {
                Text(
                    if (vm.audioUri == null) "Hali tanlanmagan" else vm.audioName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    enabled = !working,
                ) { Text(if (vm.audioUri == null) "Ovoz tanlash" else "Boshqa ovoz tanlash") }
            }
        }

        item {
            SectionCard("2. Rasmlar (${vm.images.size} ta)") {
                Text(
                    "Rasmlar videoda shu tartibda chiqadi. Tartibni o'q tugmalari bilan o'zgartiring.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !working,
                    ) { Text("Rasm qo'shish") }
                    if (vm.images.isNotEmpty()) {
                        OutlinedButton(onClick = { vm.clearImages() }, enabled = !working) { Text("Tozalash") }
                    }
                }
            }
        }

        itemsIndexed(vm.images) { index, uri ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.moveImage(index, -1) }, enabled = !working && index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yuqoriga")
                    }
                    IconButton(
                        onClick = { vm.moveImage(index, 1) },
                        enabled = !working && index < vm.images.size - 1,
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Pastga")
                    }
                    IconButton(onClick = { vm.removeImage(index) }, enabled = !working) {
                        Icon(Icons.Default.Delete, contentDescription = "O'chirish")
                    }
                }
            }
        }

        item {
            SectionCard("3. Matn (subtitr uchun, ixtiyoriy)") {
                OutlinedTextField(
                    value = vm.script,
                    onValueChange = { vm.script = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("Ovozda aytilgan matnni shu yerga qo'ying") },
                    enabled = !working,
                )
            }
        }

        item {
            SectionCard("4. Sozlamalar") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Orientation.values().forEach { o ->
                        FilterChip(
                            selected = vm.orientation == o,
                            onClick = { vm.orientation = o },
                            label = { Text(o.label) },
                            enabled = !working,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Quality.values().forEach { q ->
                        FilterChip(
                            selected = vm.quality == q,
                            onClick = { vm.quality = q },
                            label = { Text(q.label) },
                            enabled = !working,
                        )
                    }
                }
                SwitchRow("Subtitr qo'shish", vm.subtitles, !working) { vm.subtitles = it }
                SwitchRow("Yaqinlashish/siljish effekti", vm.effects, !working) { vm.effects = it }
            }
        }

        item {
            Button(
                onClick = { vm.start() },
                enabled = vm.canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Videoni yaratish", style = MaterialTheme.typography.titleMedium) }
        }

        item {
            when (val s = vm.status) {
                Status.Idle -> {}

                is Status.Working -> SectionCard("Jarayon") {
                    Text(s.message)
                    if (s.progress != null) {
                        LinearProgressIndicator(
                            progress = { s.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${s.progress}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "Ilovani yopmang va ekranni o'chirmang.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { vm.cancel() }) { Text("Bekor qilish") }
                }

                is Status.Done -> SectionCard("Tayyor!") {
                    Text("Video Galereyaga (Movies/VideoAvto) saqlandi.")
                    Text(s.summary, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(s.uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                ctx.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                // video ochuvchi ilova topilmadi
                            }
                        }) { Text("Ochish") }
                        TextButton(onClick = { vm.dismissStatus() }) { Text("Yopish") }
                    }
                }

                is Status.Error -> SectionCard("Xatolik") {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.dismissStatus() }) { Text("Yopish") }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
