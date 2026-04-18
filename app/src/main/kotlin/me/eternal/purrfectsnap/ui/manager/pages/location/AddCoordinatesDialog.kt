package cock.crest.purrfectsnap.lite.ui.manager.pages.location

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import cock.crest.purrfectsnap.lite.bridge.location.LocationCoordinates
import cock.crest.purrfectsnap.lite.common.bridge.wrapper.LocaleWrapper
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette
import cock.crest.purrfectsnap.lite.ui.util.AlertDialogs


@Composable
fun AddCoordinatesDialog(
    alertDialogs: AlertDialogs,
    translation: LocaleWrapper,
    locationCoordinates: LocationCoordinates,
    confirm: (locationCoordinates: LocationCoordinates) -> Unit
) {
    var savedName by remember {
        mutableStateOf(
            (locationCoordinates.name ?: "").let {
                TextFieldValue(it, selection = TextRange(it.length))
            }
        )
    }
    var savedLatitude by remember { mutableStateOf(locationCoordinates.latitude.toFloat().toString()) }
    var savedLongitude by remember { mutableStateOf(locationCoordinates.longitude.toFloat().toString()) }

    alertDialogs.DefaultDialogCard {
        val focusRequester = remember { FocusRequester() }
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = PurrfectPalette.textSecondary,
            unfocusedLabelColor = PurrfectPalette.textSecondary,
            cursorColor = PurrfectPalette.glowSecondary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                PurrfectPalette.cardOverlayColor.copy(alpha = 0.98f),
                                Color(0xFF1A143A).copy(alpha = 0.94f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = translation["save_coordinates_dialog_title"],
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    value = savedName,
                    onValueChange = { savedName = it },
                    label = { Text(translation["saved_name_dialog_hint"]) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )

                LaunchedEffect(Unit) {
                    delay(200)
                    focusRequester.requestFocus()
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = savedLatitude,
                    onValueChange = { savedLatitude = it },
                    label = { Text(translation["latitude_dialog_hint"]) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = savedLongitude,
                    onValueChange = { savedLongitude = it },
                    label = { Text(translation["longitude_dialog_hint"]) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            confirm(LocationCoordinates().apply {
                                this.name = savedName.text
                                this.latitude = savedLatitude.toDoubleOrNull() ?: 0.0
                                this.longitude = savedLongitude.toDoubleOrNull() ?: 0.0
                            })
                        },
                        enabled = savedName.text.isNotBlank() && savedLatitude.isNotBlank() && savedLongitude.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.3f),
                            contentColor = Color.White,
                            disabledContainerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.16f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(translation["save_dialog_button"])
                    }
                }
            }
        }
    }
}
