package cock.crest.purrfectsnap.lite.ui.util

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

var showThankYouDialog = mutableStateOf(false)

@Composable
fun ThankYouDialog() {
    if (showThankYouDialog.value) {
        AlertDialog(
            onDismissRequest = { showThankYouDialog.value = false },
            title = { Text("Thank You!") },
            text = { Text("The bypass feature has been successfully unlocked.") },
            confirmButton = {
                Button(onClick = { showThankYouDialog.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}
