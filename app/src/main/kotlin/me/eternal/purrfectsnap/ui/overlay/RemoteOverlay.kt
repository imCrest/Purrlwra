@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
package cock.crest.purrfectsnap.lite.ui.overlay

import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.arthenica.ffmpegkit.Packages.getPackageName
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.RemoteSideContext
import cock.crest.purrfectsnap.lite.common.ui.AppMaterialTheme
import cock.crest.purrfectsnap.lite.common.ui.ThemeMode
import cock.crest.purrfectsnap.lite.common.ui.createComposeView
import cock.crest.purrfectsnap.lite.ui.manager.Navigation
import cock.crest.purrfectsnap.lite.ui.manager.Routes
import cock.crest.purrfectsnap.lite.ui.manager.theme.PurrfectPalette


class RemoteOverlay(
    private val context: RemoteSideContext
) {
    private lateinit var dialog: Dialog
    private var dismissCallback: (() -> Boolean)? = null

    private fun checkForPermissions(): Boolean {
        if (!Settings.canDrawOverlays(context.androidContext)) {
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            myIntent.setData(Uri.parse("package:" + getPackageName()))
            context.androidContext.startActivity(myIntent)
            return false
        }
        return true
    }

    @Composable
    private fun OverlayContent(startRoute: (Routes) -> Routes.Route) {
        val navHostController = rememberNavController()

        LaunchedEffect(Unit) {
            dismissCallback = { navHostController.popBackStack() }
        }

        val navigation = remember { Navigation(context, navHostController) }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = { navigation.TopBar() }
        ) { innerPadding ->
            navigation.NavContent(
                innerPadding,
                startDestination = remember { startRoute(navigation.routes).routeInfo.id }
            )
        }
    }

    fun close() {
        if (!::dialog.isInitialized || !dialog.isShowing) return
        dismissCallback = null
        context.sharedPreferences.edit().putBoolean("overlay_active", false).apply()
        context.androidContext.mainExecutor.execute {
            dialog.dismiss()
        }
    }

    fun show(route: (Routes) -> Routes.Route) {
        if (!checkForPermissions()) {
            return
        }

        if (::dialog.isInitialized && dialog.isShowing) {
            return
        }

        context.sharedPreferences.edit().putBoolean("overlay_active", true).apply()
        context.androidContext.mainExecutor.execute {
            dialog = object: Dialog(context.androidContext, R.style.FullscreenOverlayDialog) {
                override fun dismiss() {
                    dismissCallback?.also {
                        if (it()) return
                    }
                    super.dismiss()
                    this@RemoteOverlay.context.sharedPreferences.edit()
                        .putBoolean("overlay_active", false)
                        .apply()
                    this@RemoteOverlay.context.config.writeConfig()
                }
            }
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.Transparent.value.toInt()))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }

            dialog.setContentView(
                createComposeView(context.androidContext) {
                    AppMaterialTheme(themeMode = ThemeMode.DARK) {
                        CompositionLocalProvider(
                            LocalContentColor provides Color.White,
                            LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(color = Color.White))
                        ) {
                            val overlayShape = MaterialTheme.shapes.large
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 12.dp, end = 12.dp)
                                    .clip(overlayShape),
                                shape = overlayShape,
                                color = Color.Transparent,
                                contentColor = Color.White,
                                tonalElevation = 0.dp,
                                shadowElevation = 10.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(overlayShape)
                                        .background(PurrfectPalette.backgroundGradient)
                                ) {
                                    OverlayContent(route)
                                }
                            }
                        }
                    }
                }
            )

            dialog.setCancelable(true)
            dialog.show()
        }
    }
}
