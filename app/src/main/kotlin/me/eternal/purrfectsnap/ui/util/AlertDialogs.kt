package cock.crest.purrfectsnap.lite.ui.util

import android.content.Context
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cock.crest.purrfectsnap.lite.common.config.ConfigFlag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.github.skydoves.colorpicker.compose.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cock.crest.purrfectsnap.lite.common.Constants
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
import cock.crest.purrfectsnap.lite.common.config.DataProcessors
import cock.crest.purrfectsnap.lite.common.config.PropertyPair
import cock.crest.purrfectsnap.lite.common.ui.AutoClearKeyboardFocus
import cock.crest.purrfectsnap.lite.common.util.ktx.await
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import cock.crest.purrfectsnap.lite.ui.util.purrfectSwitchColors
import cock.crest.purrfectsnap.lite.ui.util.Dialog as StandardDialog


class AlertDialogs(
    private val translation: LocaleWrapper,
){
    @Composable
    fun MessageListPropertyDialog(property: PropertyPair<*>, onDismiss: () -> Unit = {}) {
        val currentValue = property.value.getNullable()?.toString() ?: "[]"
        val propertyName = translation[property.key.propertyName()]
        
        MessageListManagerDialog(
            title = propertyName ?: "",
            messageListJson = currentValue,
            onSave = { newValue: String ->
                property.value.setAny(newValue)
            },
            onDismiss = onDismiss
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AutoOpenScheduleDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit
    ) {
        val windowParts = (property.value.get() as String).split("-")
        val startTime = windowParts.getOrNull(0)?.split(":") ?: listOf("23", "00")
        val endTime = windowParts.getOrNull(1)?.split(":") ?: listOf("07", "00")

        var isEditingEnd by remember { mutableStateOf(false) }
        
        val startState = rememberTimePickerState(
            initialHour = startTime.getOrNull(0)?.toIntOrNull() ?: 23,
            initialMinute = startTime.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        val endState = rememberTimePickerState(
            initialHour = endTime.getOrNull(0)?.toIntOrNull() ?: 7,
            initialMinute = endTime.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )

        DefaultDialogCard {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = translation["auto_open_snaps.auto_open_schedule.title"] ?: "Auto Open Scheduler",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val activeColor = PurrfectPalette.glowPrimary.copy(alpha = 0.25f)
                    val inactiveColor = Color.Transparent
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isEditingEnd) activeColor else inactiveColor)
                            .clickable { isEditingEnd = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${translation["auto_open_snaps.auto_open_schedule.start"] ?: "Start"}: ${String.format("%02d:%02d", startState.hour, startState.minute)}",
                            color = if (!isEditingEnd) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (!isEditingEnd) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isEditingEnd) activeColor else inactiveColor)
                            .clickable { isEditingEnd = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${translation["auto_open_snaps.auto_open_schedule.end"] ?: "End"}: ${String.format("%02d:%02d", endState.hour, endState.minute)}",
                            color = if (isEditingEnd) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (isEditingEnd) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                TimePicker(
                    state = if (isEditingEnd) endState else startState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Color.White.copy(alpha = 0.05f),
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = Color.White.copy(alpha = 0.7f),
                        selectorColor = PurrfectPalette.glowPrimary,
                        periodSelectorBorderColor = PurrfectPalette.glowPrimary,
                        periodSelectorSelectedContainerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.2f),
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = Color.White,
                        periodSelectorUnselectedContentColor = Color.White.copy(alpha = 0.7f),
                        timeSelectorSelectedContainerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.2f),
                        timeSelectorUnselectedContainerColor = Color.White.copy(alpha = 0.05f),
                        timeSelectorSelectedContentColor = Color.White,
                        timeSelectorUnselectedContentColor = Color.White.copy(alpha = 0.7f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = translation["button.negative"], color = Color.White)
                    }
                    Button(
                        onClick = {
                            val startStr = String.format("%02d:%02d", startState.hour, startState.minute)
                            val endStr = String.format("%02d:%02d", endState.hour, endState.minute)
                            property.value.setAny("$startStr-$endStr")
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.35f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = translation["button.positive"])
                    }
                }
            }
        }
    }

    @Composable
    fun DefaultDialogCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        val scrollState = rememberScrollState()
        Surface(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .then(modifier),
            color = Color.White.copy(alpha = 0.06f),
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                    )
                )
            ),
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { content() }
            }
        }
    }

    @Composable
    fun ConfirmDialog(
        title: String,
        message: String? = null,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DefaultDialogCard {
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp),
                color = PurrfectPalette.textPrimary
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PurrfectPalette.textSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.cancel"])
                }
                Button(
                    onClick = { onConfirm() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    fun InfoDialog(
        title: String,
        message: String? = null,
        onDismiss: () -> Unit,
    ) {
        DefaultDialogCard {
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp),
                color = PurrfectPalette.textPrimary
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = PurrfectPalette.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.28f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    fun TranslatedText(property: PropertyPair<*>, key: String, modifier: Modifier = Modifier) {
        Text(
            text = property.key.propertyOption(translation, key),
            modifier = Modifier
                .padding(10.dp, 10.dp, 10.dp, 10.dp)
                .then(modifier)
        )
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun UniqueSelectionDialog(property: PropertyPair<*>) {
        val disabledKey = property.key.params.disabledKey
        val noDisable = property.key.params.flags.contains(ConfigFlag.NO_DISABLE_KEY)
        val keys = (property.value.defaultValues as List<String>).toMutableList().apply {
            if (noDisable) {
                disabledKey?.let { remove(it) }
                remove("null")
            } else {
                val disabledEntry = disabledKey ?: "null"
                remove(disabledEntry)
                add(0, disabledEntry)
                if (disabledKey == null) {
                    remove("null")
                    add(0, "null")
                }
            }
        }

        var selectedValue by remember {
            val currentValue = property.value.getNullable()?.toString()
            mutableStateOf(currentValue ?: if (noDisable) (keys.firstOrNull().orEmpty()) else (disabledKey ?: "null"))
        }

        DefaultDialogCard {
            keys.forEachIndexed { index, item ->
                fun select() {
                    selectedValue = item
                    if (!noDisable && disabledKey != null && item == disabledKey) {
                        property.value.setAny(disabledKey)
                        return
                    }
                    if (!noDisable && disabledKey == null && index == 0) {
                        property.value.setAny(null)
                        return
                    }
                    property.value.setAny(item)
                }

                Row(
                    modifier = Modifier.clickable { select() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TranslatedText(
                        property = property,
                        key = item,
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = selectedValue == item,
                        onClick = { select() },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = PurrfectPalette.glowSecondary,
                            unselectedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun KeyboardInputDialog(property: PropertyPair<*>, dismiss: () -> Unit = {}) {
        val focusRequester = remember { FocusRequester() }
        val context = LocalContext.current

        DefaultDialogCard {
            var fieldValue by remember {
                mutableStateOf(property.value.get().toString().let {
                    TextFieldValue(
                        text = it,
                        selection = TextRange(it.length)
                    )
                })
            }

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp)
                    .onGloballyPositioned {
                        focusRequester.requestFocus()
                    }
                    .focusRequester(focusRequester),
                value = fieldValue,
                onValueChange = { fieldValue = it },
                keyboardOptions = when (property.key.dataType.type) {
                    DataProcessors.Type.INTEGER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    DataProcessors.Type.FLOAT -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    else -> KeyboardOptions(keyboardType = KeyboardType.Text)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PurrfectPalette.glowSecondary
                )
            )

            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = { dismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.cancel"])
                }
                Button(
                    onClick = {
                    if (fieldValue.text.isNotEmpty() && property.key.params.inputCheck?.invoke(fieldValue.text) == false) {
                        Toast.makeText(context, translation["invalid_input_toast"], Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    when (property.key.dataType.type) {
                        DataProcessors.Type.INTEGER -> {
                            runCatching {
                                property.value.setAny(fieldValue.text.toInt())
                            }.onFailure {
                                property.value.setAny(0)
                            }
                        }
                        DataProcessors.Type.FLOAT -> {
                            runCatching {
                                property.value.setAny(fieldValue.text.toFloat())
                            }.onFailure {
                                property.value.setAny(0f)
                            }
                        }
                        else -> property.value.setAny(fieldValue.text)
                    }
                    dismiss()
                },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DatePickerPropertyDialog(property: PropertyPair<*>, dismiss: () -> Unit = {}) {
        val context = LocalContext.current
        val zoneId = remember { ZoneId.systemDefault() }
        val initialSelectedDateMillis = remember(property.value.get()) {
            runCatching {
                LocalDate
                    .parse(property.value.get().toString(), DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialSelectedDateMillis)

        DefaultDialogCard {
            DatePicker(
                state = datePickerState,
                showModeToggle = true
            )

            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = { dismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.cancel"])
                }
                Button(
                    onClick = {
                        val selectedDate = datePickerState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
                        }

                        if (selectedDate == null) {
                            Toast.makeText(context, translation["invalid_input_toast"], Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        property.value.setAny(selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        dismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    fun RawInputDialog(onDismiss: () -> Unit, onConfirm: (value: String) -> Unit) {
        val focusRequester = remember { FocusRequester() }

        DefaultDialogCard {
            val fieldValue = remember {
                mutableStateOf(TextFieldValue())
            }

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 10.dp)
                    .onGloballyPositioned {
                        focusRequester.requestFocus()
                    }
                    .focusRequester(focusRequester),
                value = fieldValue.value,
                onValueChange = {
                    fieldValue.value = it
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PurrfectPalette.glowSecondary
                )
            )

            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.cancel"])
                }
                Button(
                    onClick = {
                    onConfirm(fieldValue.value.text)
                },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = translation["button.ok"])
                }
            }
        }
    }

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun MultipleSelectionDialog(property: PropertyPair<*>) {
        val defaultItems = property.value.defaultValues as List<String>
        val toggledStates = property.value.get() as MutableList<String>
        DefaultDialogCard {
            defaultItems.forEach { key ->
                var state by remember { mutableStateOf(toggledStates.contains(key)) }

                fun toggle(value: Boolean? = null) {
                    state = value ?: !state
                    if (state) {
                        toggledStates.add(key)
                    } else {
                        toggledStates.remove(key)
                    }
                }

                Row(
                    modifier = Modifier.clickable { toggle() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TranslatedText(
                        property = property,
                        key = key,
                        modifier = Modifier
                            .weight(1f)
                    )
                    Switch(
                        checked = state,
                        onCheckedChange = {
                            toggle(it)
                        },
                        colors = purrfectSwitchColors()
                    )
                }
            }
        }
    }

    @Composable
    fun ColorPickerDialog(
        initialColor: Color?,
        setProperty: (Color?) -> Unit,
        dismiss: () -> Unit
    ) {
        var currentColor by remember { mutableStateOf(initialColor) }

        DefaultDialogCard {
            val controller = remember { ColorPickerController().apply {
                if (currentColor == null) {
                    setBrightness(1f, false)
                }
            } }
            var colorHexValue by remember {
                mutableStateOf(currentColor?.toArgb()?.let { Integer.toHexString(it) } ?: "")
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TextField(
                    value = colorHexValue,
                    onValueChange = { value ->
                        colorHexValue = value
                        runCatching {
                            currentColor = Color(android.graphics.Color.parseColor("#$value")).also {
                                controller.selectByColor(it, true)
                                setProperty(it)
                            }
                        }.onFailure {
                            currentColor = null
                        }
                    },
                    label = { Text(text = translation["dialogs.hex_color_label"]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    )
                )
            }
            HsvColorPicker(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(10.dp),
                initialColor = remember { currentColor },
                controller = controller,
                onColorChanged = {
                    if (!it.fromUser) return@HsvColorPicker
                    currentColor = it.color
                    colorHexValue = Integer.toHexString(it.color.toArgb())
                    setProperty(it.color)
                }
            )
            AlphaSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .height(35.dp),
                initialColor = remember { currentColor },
                controller = controller,
            )
            BrightnessSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .height(35.dp),
                initialColor = remember { currentColor },
                controller = controller,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlphaTile(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    controller = controller
                )
                IconButton(onClick = {
                    setProperty(null)
                    dismiss()
                }) {
                    Icon(
                        modifier = Modifier.size(60.dp),
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null
                    )
                }
            }
        }
    }

    @Composable
    fun ColorPickerPropertyDialog(
        property: PropertyPair<*>,
        dismiss: () -> Unit = {},
    ) {
        var currentColor by remember {
            mutableStateOf((property.value.getNullable() as? Int)?.let { Color(it) })
        }

        ColorPickerDialog(
            initialColor = currentColor,
            setProperty = setProperty@{
                currentColor = it
                property.value.setAny(it?.toArgb())
                if (it == null) {
                    property.value.setAny(property.value.defaultValues?.firstOrNull() ?: return@setProperty)
                }
            },
            dismiss = dismiss
        )
    }

    @Composable
    fun ChooseLocationDialog(
        property: PropertyPair<*>,
        marker: MutableState<Marker?> = remember { mutableStateOf(null) },
        mapView: MutableState<MapView?> = remember { mutableStateOf(null) },
        locationSearchProvider: String = "osm",
        googleMapsApiKey: String = "",
        saveCoordinates: (() -> Unit)? = null,
        dismiss: () -> Unit = {}
    ) {
        val betterLocationTranslation = remember { translation.getCategory("manager.sections.better_location") }
        val coordinates = remember {
            (property.value.get() as Pair<*, *>).let {
                it.first.toString().toDouble() to it.second.toString().toDouble()
            }
        }
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        fun dismissKeyboard() {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }

        mapView.value = remember {
            Configuration.getInstance().apply {
                osmdroidBasePath = File(context.cacheDir, "osmdroid")
                load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            }
            MapView(context).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                val tileSource = if (locationSearchProvider == "google_maps") {
                    object : OnlineTileSourceBase(
                        "GoogleMaps",
                        0, 19, 256, ".png",
                        arrayOf("https://mt0.google.com/vt/lyrs=m", "https://mt1.google.com/vt/lyrs=m", "https://mt2.google.com/vt/lyrs=m", "https://mt3.google.com/vt/lyrs=m")
                    ) {
                        override fun getTileURLString(pMapTileIndex: Long): String {
                            return baseUrl + "&x=" + MapTileIndex.getX(pMapTileIndex) + "&y=" + MapTileIndex.getY(pMapTileIndex) + "&z=" + MapTileIndex.getZoom(pMapTileIndex)
                        }
                    }
                } else {
                    TileSourceFactory.MAPNIK
                }
                setTileSource(tileSource)

                val startPoint = GeoPoint(coordinates.first, coordinates.second)
                controller.setZoom(10.0)
                controller.setCenter(startPoint)

                marker.value = Marker(this).apply {
                    isDraggable = true
                    position = startPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }

                overlays.add(object: Overlay() {
                    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                        dismissKeyboard()
                        marker.value?.position = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                        mapView.invalidate()
                        return true
                    }
                })

                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        dismissKeyboard()
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        dismissKeyboard()
                        return false
                    }
                }))

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        dismissKeyboard()
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        dismissKeyboard()
                        return false
                    }
                })

                overlays.add(marker.value)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                mapView.value?.onDetach()
            }
        }

        var customCoordinatesDialog by remember { mutableStateOf(false) }


        val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
        val okHttpClient by lazy { OkHttpClient() }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .fillMaxHeight(fraction = 0.9f),
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = 0.04f),
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.5f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(PurrfectPalette.cardOverlay)
            ) {
                AndroidView(
                    factory = { mapView.value!! },
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var locationName by remember { mutableStateOf<String>("") }
                    var addressResults by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
                    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    val resultsScrollState = rememberScrollState()

                    BackHandler {
                        val shouldDismissKeyboard = locationName.isNotEmpty() || addressResults.isNotEmpty()
                        if (shouldDismissKeyboard) {
                            dismissKeyboard()
                            locationName = ""
                            addressResults = emptyList()
                            searchJob?.cancel()
                            searchJob = null
                        } else {
                            dismiss()
                        }
                    }

                    suspend fun search() {
                        if (locationSearchProvider == "google_maps") {
                            // Google Maps Search
                            okHttpClient.newCall(Request.Builder()
                                .url("https://maps.googleapis.com/maps/api/geocode/json".toUri().buildUpon()
                                    .appendQueryParameter("address", locationName)
                                    .appendQueryParameter("key", googleMapsApiKey)
                                    .build().toString())
                                .build()
                            ).await().use { response ->
                                if (!response.isSuccessful) return@use
                                runCatching {
                                    val jsonResponse = JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject
                                    if (jsonResponse.has("results")) {
                                        val results = jsonResponse.getAsJsonArray("results")
                                        addressResults = results.take(5).map { jsonElement ->
                                            val result = jsonElement.asJsonObject
                                            val geometry = result.getAsJsonObject("geometry").getAsJsonObject("location")
                                            Triple(
                                                result.get("formatted_address").asString,
                                                geometry.get("lat").asString,
                                                geometry.get("lng").asString
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // OSM Nominatim Search (Existing Logic)
                            okHttpClient.newCall(Request.Builder()
                                .url("https://nominatim.openstreetmap.org/search".toUri().buildUpon()
                                    .appendQueryParameter("q", locationName)
                                    .appendQueryParameter("format", "jsonv2")
                                    .build().toString())
                                .header("User-Agent", Constants.OSM_USER_AGENT)
                                .build()
                            ).await().use { response ->
                                if (!response.isSuccessful) return@use
                                runCatching {
                                    val body = JsonParser.parseString(response.body?.string() ?: "[]").asJsonArray
                                    addressResults = body.take(5).map { jsonElement ->
                                        val jsonObject = jsonElement.asJsonObject
                                        Triple(
                                            jsonObject.get("display_name").asString,
                                            jsonObject.get("lat").asString,
                                            jsonObject.get("lon").asString
                                        )
                                    }
                                }
                            }
                        }
                        searchJob = null
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0F1024).copy(alpha = 0.9f),
                        tonalElevation = 0.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF2A2452).copy(alpha = 0.9f),
                                            Color(0xFF1A143A).copy(alpha = 0.7f)
                                        )
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Explore,
                                            contentDescription = null,
                                            tint = PurrfectPalette.glowSecondary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = betterLocationTranslation["choose_location_button"],
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = betterLocationTranslation["search_or_tap_map_hint"],
                                            fontSize = 12.sp,
                                            color = PurrfectPalette.textSecondary
                                        )
                                    }
                                }
                                FilledIconButton(
                                    onClick = dismiss,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = translation["button.cancel"]
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0F1024).copy(alpha = 0.92f),
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth(),
                            value = locationName,
                            onValueChange = {
                                locationName = it.replace("\n", "")
                                if (locationName == "") {
                                    addressResults = emptyList()
                                    searchJob?.cancel()
                                    searchJob = null
                                    return@OutlinedTextField
                                }
                                searchJob?.cancel()
                                searchJob = coroutineScope.launch {
                                    delay(300)
                                    search()
                                }
                            },
                            placeholder = { Text(text = betterLocationTranslation["search_location_placeholder"]) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = betterLocationTranslation["search_icon_description"],
                                    tint = Color.White
                                )
                            },
                            trailingIcon = {
                                if (locationName.isNotEmpty()) {
                                    IconButton(onClick = {
                                        locationName = ""
                                        addressResults = emptyList()
                                        searchJob?.cancel()
                                        searchJob = null
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F1024).copy(alpha = 0.96f),
                                unfocusedContainerColor = Color(0xFF0F1024).copy(alpha = 0.88f),
                                focusedBorderColor = PurrfectPalette.glowSecondary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                cursorColor = PurrfectPalette.glowSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedPlaceholderColor = PurrfectPalette.textSecondary,
                                unfocusedPlaceholderColor = PurrfectPalette.textSecondary
                            )
                        )
                    }

                    AutoClearKeyboardFocus(onFocusClear = {
                        locationName = ""
                        addressResults = emptyList()
                        searchJob?.cancel()
                        searchJob = null
                    })

                    if (addressResults.isNotEmpty() || searchJob?.isActive == true) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    PurrfectPalette.glowPrimary.copy(alpha = 0.4f),
                                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                                )
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PurrfectPalette.cardOverlay)
                                .verticalScroll(resultsScrollState),
                        ) {
                            if (addressResults.isNotEmpty()) {
                                addressResults.forEachIndexed { index, address ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                dismissKeyboard()
                                                marker.value?.position = GeoPoint(address.second.toDouble(), address.third.toDouble())
                                                mapView.value?.controller?.setCenter(marker.value?.position)
                                                mapView.value?.invalidate()
                                                locationName = ""
                                                addressResults = emptyList()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocationOn,
                                            contentDescription = "Location",
                                            tint = PurrfectPalette.glowSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = address.first,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.White
                                        )
                                    }
                                    if (index < addressResults.size - 1) {
                                        HorizontalDivider(
                                            color = Color.White.copy(alpha = 0.2f),
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            } else if (searchJob?.isActive == true) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = PurrfectPalette.glowSecondary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = betterLocationTranslation["searching_label"],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    }
                }


            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.38f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.32f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF241E46).copy(alpha = 0.92f),
                                    Color(0xFF1B1638).copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = {
                            val lat = marker.value?.position?.latitude ?: coordinates.first
                            val lon = marker.value?.position?.longitude ?: coordinates.second
                            property.value.setAny(lat to lon)
                            dismiss()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = PurrfectPalette.glowSecondary.copy(alpha = 0.24f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Filled.Check,
                            contentDescription = null
                        )
                    }
                    saveCoordinates?.let {
                        FilledIconButton(
                            onClick = { it() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                imageVector = Icons.Filled.Save,
                                contentDescription = null
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = { customCoordinatesDialog = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.24f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Filled.EditLocationAlt,
                            contentDescription = null
                        )
                    }
                }
            }

            if (customCoordinatesDialog) {
                val lat = remember { mutableStateOf(coordinates.first.toString()) }
                val lon = remember { mutableStateOf(coordinates.second.toString()) }

                StandardDialog(
                    onDismissRequest = {
                    customCoordinatesDialog = false
                    },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    DefaultDialogCard(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditLocationAlt,
                                    contentDescription = null,
                                    tint = PurrfectPalette.glowSecondary
                                )
                            }
                            Column {
                                Text(
                                    text = betterLocationTranslation["save_coordinates_dialog_title"],
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = betterLocationTranslation["manual_coordinates_hint"],
                                    fontSize = 12.sp,
                                    color = PurrfectPalette.textSecondary
                                )
                            }
                        }
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            value = lat.value,
                            onValueChange = { lat.value = it },
                            label = { Text(text = translation["latitude_dialog_hint"]) },
                            leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedBorderColor = PurrfectPalette.glowSecondary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                cursorColor = PurrfectPalette.glowSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            value = lon.value,
                            onValueChange = { lon.value = it },
                            label = { Text(text = translation["longitude_dialog_hint"]) },
                            leadingIcon = { Icon(Icons.Filled.Navigation, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedBorderColor = PurrfectPalette.glowSecondary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                cursorColor = PurrfectPalette.glowSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        ) {
                            Button(
                                onClick = { customCoordinatesDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) { Text(text = translation["button.cancel"]) }

                            Button(
                                onClick = {
                                    marker.value?.position = GeoPoint(lat.value.toDouble(), lon.value.toDouble())
                                    mapView.value?.controller?.setCenter(marker.value?.position)
                                    mapView.value?.invalidate()
                                    customCoordinatesDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                                    contentColor = Color.White
                                )
                            ) { Text(text = translation["button.ok"]) }
                        }
                    }
                }
            }
        }
    }
}

    @Composable
    fun MessageListManagerDialog(
        title: String,
        messageListJson: String,
        onSave: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var messageList by remember { 
            mutableStateOf(
                try {
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    gson.fromJson(messageListJson, type) ?: listOf<String>()
                } catch (e: Exception) {
                    listOf<String>()
                }
            ) 
        }
        
        var showAddDialog by remember { mutableStateOf(false) }
        var editingIndex by remember { mutableStateOf(-1) }
        var editingText by remember { mutableStateOf("") }

        DefaultDialogCard {
            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (messageList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = translation["bulk_messaging_action.no_messages_found"] ?: "No messages",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(messageList.size) { index ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = messageList[index],
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { 
                                                    editingIndex = index
                                                    editingText = messageList[index]
                                                    showAddDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = translation["common.edit"] ?: "Edit", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(
                                                onClick = { 
                                                    messageList = messageList.toMutableList().apply { removeAt(index) }
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = translation["common.delete"] ?: "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { 
                        editingIndex = -1
                        editingText = ""
                        showAddDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = translation["common.add"] ?: "Add Message")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(text = translation["button.cancel"])
                    }
                    Button(
                        onClick = {
                            val gson = com.google.gson.Gson()
                            onSave(gson.toJson(messageList))
                            onDismiss()
                        }
                    ) {
                        Text(text = translation["button.save"])
                    }
                }
            }
        }

        if (showAddDialog) {
            StandardDialog(
                onDismissRequest = { showAddDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                DefaultDialogCard {
                    Text(
                        text = if (editingIndex == -1) translation["common.add"] ?: "Add Message" else translation["common.edit"] ?: "Edit Message",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                    
                    TextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        label = { Text(translation["common.message"] ?: "Message") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text(translation["common.type_message"] ?: "Type message...") }
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { showAddDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(text = translation["button.cancel"])
                        }
                        Button(
                            onClick = {
                                if (editingText.isNotBlank()) {
                                    messageList = messageList.toMutableList().apply {
                                        if (editingIndex == -1) add(editingText) else set(editingIndex, editingText)
                                    }
                                }
                                showAddDialog = false
                            },
                            enabled = editingText.isNotBlank()
                        ) {
                            Text(text = if (editingIndex == -1) translation["common.add"] ?: "Add" else translation["button.save"])
                        }
                    }
                }
            }
        }
    }
}
