package com.muhammadnoman11.videograb.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.muhammadnoman11.videograb.R
import com.muhammadnoman11.videograb.ui.viewmodel.MainViewModel
import com.muhammadnoman11.videograb.ui.screens.downloads.DownloadsScreen
import com.muhammadnoman11.videograb.ui.screens.home.HomeScreen
import com.muhammadnoman11.videograb.ui.theme.VideoDownloaderTheme
import dagger.hilt.android.AndroidEntryPoint
import com.muhammadnoman11.videograb.ui.screens.permissions.PermissionHandler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUrl = extractSharedUrl(intent)

        setContent {
            VideoDownloaderTheme {
                PermissionHandler {
                    AppContent(sharedUrl = sharedUrl)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    // Extract a URL from a share intent (e.g. "Check this out: https://…")
    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://[^\\s]+").find(text)?.value ?: text
    }
}

// App content (composed after permissions granted)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(sharedUrl: String?) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

    // Handle share intent
    LaunchedEffect(sharedUrl) {
        sharedUrl?.takeIf { it.startsWith("http") }?.let { viewModel.setUrlFromShare(it) }
    }

    Scaffold(
        containerColor = Color(0xFF0F0F1A),
        bottomBar = {
            AppBottomBar(
                navController = navController,
                activeDownloadCount = activeDownloads.count {
                    it.status in listOf("DOWNLOADING", "QUEUED")
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable("home") { HomeScreen(viewModel) }
            composable("downloads") { DownloadsScreen(viewModel) }
        }
    }
}

@Composable
private fun AppBottomBar(
    navController: NavController,
    activeDownloadCount: Int
) {
    val stack by navController.currentBackStackEntryAsState()
    val current = stack?.destination?.route

    NavigationBar(containerColor = Color(0xFF1A1A2E), tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = current == "home",
            onClick = {
                navController.navigate("home") {
                    popUpTo(navController.graph.startDestinationId); launchSingleTop = true
                }
            },
            icon = {
                Icon(
                    painterResource(
                        if (current == "home") R.drawable.ic_homee else R.drawable.outline_home_24
                    ),
                    contentDescription = "Home",
                    modifier = Modifier.size(24.dp),
                )
            },
            label = {
                Text(
                    "Home", fontSize = 11.sp,
                    fontWeight = if (current == "home") FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = navBarColors()
        )

        NavigationBarItem(
            selected = current == "downloads",
            onClick = {
                navController.navigate("downloads") {
                    popUpTo(navController.graph.startDestinationId); launchSingleTop = true
                }
            },
            icon = {
                BadgedBox(badge = {
                    if (activeDownloadCount > 0) {
                        Badge(containerColor = Color(0xFF6C63FF)) {
                            Text(
                                activeDownloadCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }) {
                    Icon(
                        painterResource(
                            if (current == "downloads") R.drawable.ic_download else R.drawable.outline_download_24
                        ),
                        contentDescription = "Downloads",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            label = {
                Text(
                    "Downloads", fontSize = 11.sp,
                    fontWeight = if (current == "downloads") FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = navBarColors()
        )
    }
}

@Composable
private fun navBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFF6C63FF),
    selectedTextColor = Color(0xFF6C63FF),
    unselectedIconColor = Color.White.copy(0.4f),
    unselectedTextColor = Color.White.copy(0.4f),
    indicatorColor = Color(0xFF6C63FF).copy(0.15f)
)
