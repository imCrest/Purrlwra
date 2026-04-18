package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Button
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.data.ContentType
import cock.crest.purrfectsnap.lite.core.ModContext
import cock.crest.purrfectsnap.lite.core.wrapper.impl.Message
import cock.crest.purrfectsnap.lite.core.wrapper.impl.getMessageText

class TranslationUI(private val context: ModContext) {
    private val translationManager by lazy { TranslationManager(context) }
    private val config by lazy { context.config.messaging.instantTranslation }
    
    @Composable
    fun TranslationOverlay(
        message: Message,
        onDismiss: () -> Unit
    ) {
        var originalText by remember { mutableStateOf("") }
        var translatedText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        
        val coroutineScope = rememberCoroutineScope()
        
        LaunchedEffect(message) {
            originalText = message.messageContent?.content?.getMessageText(ContentType.CHAT) ?: ""
            if (originalText.isNotBlank()) {
                isLoading = true
                error = null
                
                coroutineScope.launch {
                    try {
                        val translation = translationManager.translateText(
                            originalText,
                            "auto",
                            "en"
                        )
                        translatedText = translation ?: ""
                        if (translation == null) {
                            error = "Translation failed"
                        }
                    } catch (e: Exception) {
                        error = "Translation error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Translation",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else if (error != null) {
                    Text(
                        text = error!!,
                        color = Color.Red,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Text(
                        text = "Original:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = originalText,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = "Translation:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = translatedText,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Close")
                    }
                    
                    if (translatedText.isNotBlank()) {
                        Button(
                            onClick = {
                                copyToClipboard(translatedText)
                            }
                        ) {
                            Text("Copy Translation")
                        }
                    }
                }
            }
        }
    }
    
    fun createTranslationButton(
        message: Message,
        parentView: View
    ): Button {
        return Button(parentView.context).apply {
            text = "Translate"
            setOnClickListener {
                showTranslationDialog(message)
            }
        }
    }
    
    private fun showTranslationDialog(message: Message) {
        val originalText = message.messageContent?.content?.getMessageText(ContentType.CHAT) ?: return
        
        context.coroutineScope.launch {
            val translatedText = translationManager.translateText(originalText, "auto", "en") ?: return@launch
            
            context.runOnUiThread {
                val dialog = android.app.AlertDialog.Builder(context.mainActivity!!)
                    .setTitle("Translation")
                    .setMessage("Original: $originalText\n\nTranslation: $translatedText")
                    .setPositiveButton("Copy") { _, _ ->
                        copyToClipboard(translatedText)
                    }
                    .setNegativeButton("Close", null)
                    .create()
                
                dialog.show()
            }
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = context.androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Translation", text)
        clipboard.setPrimaryClip(clip)
    }
} 