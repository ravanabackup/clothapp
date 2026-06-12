package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.example.data.model.GeocodingResult
import kotlin.math.roundToInt
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchLoading by viewModel.searchLoading.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var searchQuery by remember { mutableStateOf("") }
    var locationRequestedByClick by remember { mutableStateOf(false) }

    // Accompanist location permissions
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) { permissionsMap ->
        val granted = permissionsMap.values.any { it }
        if (granted) {
            viewModel.detectAndLoadCurrentLocation(context)
        } else {
            Toast.makeText(context, "Location permission declined. Using default.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto load current location on first launch if permissions are already given
    LaunchedEffect(Unit) {
        val anyGranted = locationPermissionsState.permissions.any { it.status.isGranted }
        if (anyGranted) {
            viewModel.detectAndLoadCurrentLocation(context)
        } else {
            // Falls back to default place Chandigarh automatically via viewModel init
        }
    }

    // Automated periodic background data refreshing every 60 seconds
    LaunchedEffect(uiState) {
        val currentState = uiState
        if (currentState is UiState.Success) {
            delay(60000)
            viewModel.loadAdvisorData(
                lat = currentState.latitude,
                lon = currentState.longitude,
                name = currentState.locationName,
                isSilent = true
            )
        }
    }

    // Direct background decoration - Premium slate charcoal aesthetic
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1115),
                        Color(0xFF131722),
                        Color(0xFF182030)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Search & Actions section
            HeaderBar(
                searchQuery = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.searchCities(it)
                },
                searchResults = searchResults,
                searchLoading = searchLoading,
                onResultSelected = { result ->
                    searchQuery = ""
                    viewModel.cleanSearchResults()
                    focusManager.clearFocus()
                    val fullName = listOfNotNull(result.name, result.admin1, result.country).joinToString(", ")
                    viewModel.loadAdvisorData(result.latitude, result.longitude, fullName)
                },
                onLocationClick = {
                    locationRequestedByClick = true
                    if (locationPermissionsState.allPermissionsGranted || 
                        locationPermissionsState.permissions.any { it.status.isGranted }) {
                        viewModel.detectAndLoadCurrentLocation(context)
                    } else {
                        locationPermissionsState.launchMultiplePermissionRequest()
                    }
                }
            )

            // Content States
            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Downloading environmental metrics...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color(0x1FFF5252), RoundedCornerShape(24.dp))
                                .border(1.dp, Color(0x7FFF5252), RoundedCornerShape(24.dp))
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Connection Alert",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.message,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.loadAdvisorData(30.7333, 76.7794, "Chandigarh, India") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Text("Retry with Default (Chandigarh)", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Current Location banner
                        item {
                            LocationBanner(
                                state = state,
                                onRefreshClick = {
                                    viewModel.loadAdvisorData(state.latitude, state.longitude, state.locationName)
                                }
                            )
                        }

                        // Split metrics: AQI Gauge left, Laundry Power right
                        item {
                            DualMetricsBlock(state = state, viewModel = viewModel)
                        }

                        // Preset Configurations & Indoors Toggles
                        item {
                            DryingConfigPanel(state = state, viewModel = viewModel)
                        }

                        // Simulation output & Forecast estimates
                        item {
                            ForecastSimulationPanel(state = state)
                        }

                        // Cosmo AI Smart Stylist Suggestion (Gemini output)
                        item {
                            CosmoAiStylistCard(state = state)
                        }

                        // Next 12 Hours drying graph/bars
                        item {
                            Next12HoursSection(state = state)
                        }

                        // 7-Day Drying Outlook
                        item {
                            WeeklyOutlookSection(state = state)
                        }
                    }
                }
                UiState.Idle -> {
                    // Handled as immediate auto load via LaunchedEffect
                }
            }
        }
    }
}

// Custom Premium top search & actions bar
@Composable
fun HeaderBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<GeocodingResult>,
    searchLoading: Boolean,
    onResultSelected: (GeocodingResult) -> Unit,
    onLocationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A0D).copy(alpha = 0.5f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text("Search cities (e.g. Chandigarh, Mumbai)", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                },
                trailingIcon = {
                    if (searchLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF00E5FF))
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 56.dp)
                    .testTag("location_search_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E222D),
                    unfocusedContainerColor = Color(0xFF13161C),
                    focusedBorderColor = Color(0xFF00E5FF).copy(alpha = 0.8f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            // Current location detection button
            IconButton(
                onClick = onLocationClick,
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), CircleShape)
                    .testTag("gps_detect_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Detect Position From GPS",
                    tint = Color(0xFF00E5FF)
                )
            }
        }

        // Suggestions Dropdown List under the search textfield
        AnimatedVisibility(
            visible = searchResults.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .heightIn(max = 240.dp)
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181C26)),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(searchResults) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(city) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF).copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = city.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val subtitle = listOfNotNull(city.admin1, city.country).joinToString(", ")
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}

// Current Location Banner displays the selected location's name and coordinates
@Composable
fun LocationBanner(
    state: UiState.Success,
    onRefreshClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131824).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1.0f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state.locationName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Coordinates: ${String.format("%.4f", state.latitude)}°N, ${String.format("%.4f", state.longitude)}°E",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // Pulse live indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FFB2))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE FORECAST SYNC ACTIVE (60S REFRESH)",
                    color = Color(0xFF00FFB2).copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val summaryText = """
                        👕 LAUNDRY DRYING & WEATHER SUMMARY
                        📍 Location: ${state.locationName} (${String.format("%.4f", state.latitude)}°N, ${String.format("%.4f", state.longitude)}°E)
                        ⏰ Evaporation Power: ${state.dryingScore}% (${state.dryingStateLabel})
                        🌡️ Temp: ${state.currentTemp}°C | RH: ${state.currentRh}% | Wind: ${state.currentWind} km/h
                        🧺 Est. Drying Duration: ${String.format("%.1f", state.estimatedDryingHours)} hours
                        🕒 Expected Finish: ${state.finishPredictionLabel}
                        🌈 Best Window (12H): ${state.bestWindow12h}
                        🛡️ Clothes Protection: ${state.apparelRecommendation}
                        💨 Peak US-AQI: ${state.currentAqi} (${state.aqiLabel})
                        ✨ Stylist Brief: ${state.aiStylistSuggestion}
                    """.trimIndent()
                    
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Laundry Weather Summary", summaryText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Summary copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .testTag("copy_summary_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Summary to Clipboard",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync reports",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Displays Dual metrics: interactive Air Quality index gauge and Laundry drying index meter side by side
@Composable
fun DualMetricsBlock(
    state: UiState.Success,
    viewModel: MainViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Air Quality Index Card
        Card(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 230.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161A26)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(state.aqiRating.colorHex).copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Little title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AIR QUALITY",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Pulse dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(state.aqiRating.colorHex))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Circular AQI Arc Gauge
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Canvas circles
                    val colorRating = Color(state.aqiRating.colorHex)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background track
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.minDimension / 2.2f,
                            style = strokeWidth(8.dp.toPx())
                        )
                        // Active colored sweep arc
                        val sweepAngle = (state.currentAqi.toFloat() / 500f * 360f).coerceAtMost(360f)
                        drawArc(
                            color = colorRating,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = strokeWidth(8.dp.toPx()),
                            size = size * 0.9f,
                            topLeft = Offset(size.width * 0.05f, size.height * 0.05f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.currentAqi.toString(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "US-AQI",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = state.aqiRating.title,
                    color = Color(state.aqiRating.colorHex),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = state.aqiRating.severity,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Clothes Drying Index Card (Vapor Pressure Deficit)
        Card(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 230.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161A26)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header of drying index card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LAUNDRY SPEED",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.LocalLaundryService,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF).copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Drying Power Score Gauge
                val gaugeColor = when {
                    state.currentPrecip > 0.0 -> Color(0xFFF44336) // Red if raining
                    state.dryingScore > 75 -> Color(0xFF00E5FF) // cyan
                    state.dryingScore > 45 -> Color(0xFF4CAF50) // green
                    else -> Color(0xFFFFB300) // Amber
                }
                
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background track
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.minDimension / 2.2f,
                            style = strokeWidth(8.dp.toPx())
                        )
                        // Active sweep
                        val sweepAngle = (state.dryingScore.toFloat() / 100f * 360f).coerceIn(0f, 360f)
                        drawArc(
                            color = gaugeColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = strokeWidth(8.dp.toPx()),
                            size = size * 0.9f,
                            topLeft = Offset(size.width * 0.05f, size.height * 0.05f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.dryingScore}%",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "DRY POWER",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (state.currentPrecip > 0.0) "Rain Intercepted" else state.dryingStateLabel,
                    color = gaugeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (state.currentPrecip > 0.0) "Precipitation ruins outdoor fabric drying." else "Est. duration: ${String.format("%.1f", state.estimatedDryingHours)} hours",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Helper for strokeWidth
private fun strokeWidth(width: Float) = androidx.compose.ui.graphics.drawscope.Stroke(width = width)

// Panel displaying controls for fabric type (wetness coefficient) and indoor/outdoor toggle
@Composable
fun DryingConfigPanel(
    state: UiState.Success,
    viewModel: MainViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.7f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "ADJUST DRIER PARAMETERS",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(14.dp))

            // Fabric presets selection row
            Text(
                "Fabric Preset (Thickness Coefficient)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FabricPreset.values().forEach { preset ->
                    val isSelected = state.selectedPreset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1C2230))
                            .border(
                                1.dp, 
                                if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.05f), 
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.updateFabricPreset(preset) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.displayName.substringBefore(" "),
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Moisture Level range slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Initial Fabric Wetness Mode",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(state.initialMoisture * 100).roundToInt()}% (Moist)",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = state.initialMoisture,
                onValueChange = { viewModel.updateInitialMoisture(it) },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("initial_moisture_slider")
            )
            Text(
                text = "Simulates moisture removal rate from lightly damp (10%) to fully drenched/soaked (100%).",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            // Indoor vs Outdoor Selector Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Indoor Drying Setup",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Divides wind speed factor and isolates fabric from outdoor dust/rain.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                
                Switch(
                    checked = state.isIndoorDrying,
                    onCheckedChange = { viewModel.toggleIndoorDrying(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00E5FF),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color(0xFF1F2532)
                    )
                )
            }
        }
    }
}

// Simulation outcomes details panel
@Composable
fun ForecastSimulationPanel(
    state: UiState.Success
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Detailed Pollutants Table Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "FINE POLLUTANTS (REPORTS)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PollutantSmallCard(name = "PM2.5", level = state.currentPm25, unit = "µg/m³", modifier = Modifier.weight(1f))
                    PollutantSmallCard(name = "PM10", level = state.currentPm10, unit = "µg/m³", modifier = Modifier.weight(1f))
                    PollutantSmallCard(name = "Ozone", level = state.currentO3, unit = "µg/m³", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PollutantSmallCard(name = "NO₂", level = state.currentNo2, unit = "µg/m³", modifier = Modifier.weight(1f))
                    PollutantSmallCard(name = "SO₂", level = state.currentSo2, unit = "µg/m³", modifier = Modifier.weight(1f))
                    PollutantSmallCard(name = "CO", level = state.currentCo, unit = "mg/m³", modifier = Modifier.weight(1f))
                }
            }
        }

        // Protection advisory and Clothes wear suggestions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "PROTECTION & CLOTHES ADVISOR",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                AdvisoryItem(
                    icon = Icons.Outlined.Checkroom,
                    title = "Recommended Apparel",
                    desc = state.apparelRecommendation,
                    iconColor = Color(0xFFFFB74D)
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                AdvisoryItem(
                    icon = Icons.Outlined.MedicalServices,
                    title = "Mask Recommendation",
                    desc = state.maskRecommendation,
                    iconColor = Color(0xFFE57373)
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                AdvisoryItem(
                    icon = Icons.Outlined.FitnessCenter,
                    title = "Activities Rating",
                    desc = state.activityRecommendation,
                    iconColor = Color(0xFF81C784)
                )
            }
        }

        // Live evaporative simulation results
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "SIMULATION OUTCOMES (48H HORIZON)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Drying Completion Time", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(
                            text = state.finishPredictionLabel,
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Best Window (Next 12 Hours)", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(
                            text = state.bestWindow12h,
                            color = Color(0xFF81C784),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.finishNote,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))

                // Rain tracker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Next Rain Forecast Hour", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(
                            text = state.nextRainTime,
                            color = Color(0xFF64B5F6),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.nextRainAmt,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// Small pollutant card indicator
@Composable
fun PollutantSmallCard(
    name: String,
    level: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2333)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format("%.1f", level),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(unit, color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp)
        }
    }
}

// Single structured advisory item row
@Composable
fun AdvisoryItem(
    icon: ImageVector,
    title: String,
    desc: String,
    iconColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// Cosmo AI smart stylist card styled with gradient bounds to convey Gemini AI feel
@Composable
fun CosmoAiStylistCard(
    state: UiState.Success
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF8E24AA).copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF261835),
                            Color(0xFF131722),
                            Color(0xFF12222E)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFE040FB),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COSMO SMART AI STYLIST",
                        color = Color(0xFFE040FB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (state.isAiLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFE040FB)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = state.aiStylistSuggestion,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// Next 12 Hours drying graphs & precipitation timeline
@Composable
fun Next12HoursSection(
    state: UiState.Success
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "NEXT 12 HOURS TIMELINE",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Cyan Curve: drying power. Blue block headers: active precipitation rain.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable timeline columns
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.next12HoursDrying) { point ->
                        HourlyTimelineItem(point = point)
                    }
                }
            }
        }
    }
}

// Single timeline element displaying dry score and precipitation
@Composable
fun HourlyTimelineItem(
    point: HourlyDryingPoint
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .background(Color(0xFF1B2230), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = point.timeLabel,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Evaporative dry power vertical bar indicator
        Box(
            modifier = Modifier
                .height(70.dp)
                .width(16.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = (point.dryingScore / 100f).coerceIn(0f, 1f))
                    .background(Color(0xFF00E5FF), RoundedCornerShape(8.dp))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${point.dryingScore}%",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Precipitation value
        if (point.precipitation > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33448AFF))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${point.precipitation}mm",
                    color = Color(0xFF448AFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        } else {
            Text(
                "0.0",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Weekly forecast overview section
@Composable
fun WeeklyOutlookSection(
    state: UiState.Success
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "7-DAY OUTLOOK SUMMARY",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            state.weeklyOutlook.forEach { daily ->
                WeeklyOutlookRow(daily = daily)
            }
        }
    }
}

// Single Daily row item
@Composable
fun WeeklyOutlookRow(
    daily: DailyOutlookPoint
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day Name
            Column(modifier = Modifier.width(90.dp)) {
                Text(
                    text = daily.dayLabel,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${daily.tempMax.roundToInt()}° / ${daily.tempMin.roundToInt()}°C",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Peak AQI rating badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(daily.aqiRating.colorHex).copy(alpha = 0.15f))
                    .border(1.dp, Color(daily.aqiRating.colorHex).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .width(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AQI: ${daily.maxAqi} (${daily.aqiRating.title})",
                    color = Color(daily.aqiRating.colorHex),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Drying rating and percentage badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = daily.dryingStatus,
                    color = if (daily.avgDryingScore > 50) Color(0xFF00E5FF) else Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dry: ${daily.avgDryingScore}%",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
