package com.manjano.bus.ui.screens.home

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.FirebaseDatabase
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.manjano.bus.R
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

data class Child(
    val name: String = "",
    val photoUrl: String = "",
    val status: String = "",
    val eta: String = "",
    val active: Boolean = true
)

private val defaultPhotoUrl =
    "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"

@Composable
fun ParentDashboardScreen(
    navController: NavHostController,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?, // <-- New parameter to retrieve args
    viewModel: ParentDashboardViewModel = hiltViewModel()
) {

    // CRITICAL FIX: Manually retrieve arguments from the NavBackStackEntry
    val parentName = navBackStackEntry?.arguments?.getString("parentName") ?: ""
    val childrenNames = navBackStackEntry?.arguments?.getString("childrenNames") ?: ""
    val initialStatus = navBackStackEntry?.arguments?.getString("initialStatus") ?: "On Route"
    // DON'T create nodes from this list - it has wrong names!
    // Instead, get the CORRECT names from somewhere else

    // Option 1: Get from a reliable source (database, API)
    // Option 2: Let Firebase auto-rename handle it
    // Option 3: Pass correct names as parameter

    // For now, REMOVE this block entirely:
    // LaunchedEffect(sortedNames) {
    //     if (sortedNames.isNotEmpty()) {
    //         Log.d("🔥", "Creating Firebase nodes for ${sortedNames.size} children")
    //         viewModel.initializeChildrenFromList(sortedNames)
    //     }
    // }

    // Instead, just load existing Firebase children

    // 1. CRITICAL: Initialize the ViewModel with the retrieved arguments
    LaunchedEffect(parentName) {
        if (parentName.isNotEmpty()) {
            viewModel.initializeParent(parentName)
            // The childrenNames string is no longer used for creation, but only to ensure we have the full list
            // of names the user entered for quick display until live data loads.
            Log.d("🔥", "Dashboard Initialized for Parent: $parentName")
        }
    }


    val database = FirebaseDatabase.getInstance().reference
    val selectedAction = remember { mutableStateOf("Contact Driver") }
    val showTextBox = remember { mutableStateOf(false) }
    val textInput = remember { mutableStateOf("") }
    val quickActionExpanded = remember { mutableStateOf(false) }
    val selectedStatus = remember { mutableStateOf(initialStatus) }

    // CRITICAL: Remove all logic relying on splitting the childrenNames parameter string
    val sortedDisplayNamesFromParam = childrenNames.split(",")
        .map { it.replace('+', ' ').trim() }
        .filter { it.isNotEmpty() }
        .sorted()

    // We will use the live list of keys from the ViewModel to determine the initial child.
    val childrenKeys by viewModel.childrenKeys.collectAsState(initial = emptyList())
    val childrenDisplayMap = remember { mutableStateMapOf<String, String>() }

    // Use the *first* name from the *passed* parameter for the initial UI state name,
    // which will be quickly replaced by the live Firebase data in the next step.
    val initialChildName = sortedDisplayNamesFromParam.firstOrNull() ?: ""

// Create selectedChild state once
    val selectedChild = remember {
        // Initialize with the first available name (best guess) and the guaranteed default photo URL
        mutableStateOf(
            Child(
                name = initialChildName,
                photoUrl = defaultPhotoUrl, // Start with default URL to trigger image loading immediately
                status = initialStatus
            )
        )
    }

// Child dropdown expanded state
    val childExpanded = remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var storageFiles by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val storage =
            com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("Children Images")
        storage.listAll().addOnSuccessListener { listResult ->
            storageFiles = listResult.items.map { it.name }
            Log.d("ParentDashboard", "✅ Listed ${storageFiles.size} files from Storage")
            // Repair is now done ONLY once in the ViewModel on startup – we no longer call it from the screen
        }.addOnFailureListener {
            Log.e("ParentDashboard", "❌ Failed to list files from Storage", it)
        }
    }


    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val uiSizes = object {
        val isTablet = screenWidthDp > 600.dp
        val dropdownWidth = if (isTablet) 200.dp else 160.dp
        val photoSize = if (isTablet) 120.dp else 80.dp
        val mapHeight = if (isTablet) 300.dp else 200.dp
        val verticalSpacing = if (isTablet) 12.dp else 8.dp
        val topBarHeight = 80.dp
    }

    Log.d(
        "🔥",
        "ParentDashboard: Route hit! parent=$parentName, children=$childrenNames, status=$initialStatus"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(uiSizes.topBarHeight)
                    .padding(top = 30.dp)
                    .background(Color(0xFF800080))
            ) {
                Text(
                    text = "Parent Dashboard",
                    fontSize = 28.sp,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .semantics { contentDescription = "Parent Dashboard header" },
                    textAlign = TextAlign.Center
                )
            }
        },
        content = { innerPadding ->

            val scrollState = rememberScrollState()
            val messageBoxOffsetY = remember { mutableStateOf(0) }
            val density = LocalDensity.current

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(if (uiSizes.isTablet) 24.dp else 16.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // NEW CODE for "Hello" Greeting (FIXED: Handles URL-encoded names like "John+Doe")
                val displayParentName = remember(parentName) {
                    // 1. Replace "+" with a space (decoding URL param)
                    // 2. Trim whitespace
                    // 3. Split by space and take the first name only
                    parentName
                        .replace('+', ' ')
                        .trim()
                        .split(" ")
                        .firstOrNull() ?: parentName // Fallback to original if something goes wrong
                }

                Text(
                    text = "Hello $displayParentName",
                    fontSize = if (uiSizes.isTablet) 24.sp else 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .semantics {
                            contentDescription = "Greeting: Hello $displayParentName"
                        },
                    textAlign = TextAlign.Center
                )

                // FIXED CODE: Handles URL-encoded names and correctly formats a list of first names

                val firstNames = remember(childrenNames) {
                    childrenNames.split(",")
                        // 1. Decode URL parameter by replacing '+' with space
                        .map { it.replace('+', ' ').trim() }
                        // 2. Extract only the first name (word)
                        .map { it.split(" ").firstOrNull() ?: it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct() // Ensure unique names in the list
                        .sorted()
                }

                val trackingText = remember(firstNames) {
                    when (firstNames.size) {
                        0 -> ""
                        1 -> "Tracking ${firstNames[0]}"
                        2 -> "Tracking ${firstNames[0]} & ${firstNames[1]}"
                        else -> "Tracking " + firstNames.dropLast(1)
                            .joinToString(", ") + " & ${firstNames.last()}"
                    }
                }

                Text(
                    text = trackingText,
                    fontSize = if (uiSizes.isTablet) 18.sp else 16.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = trackingText },
                    textAlign = TextAlign.Center
                )

                val childrenKeys by viewModel.childrenKeys.collectAsState(initial = emptyList())
                val sortedChildrenKeys = childrenKeys.sorted()
                val childrenDisplayMap = remember { mutableStateMapOf<String, String>() }
                val childrenPhotoMap = remember { mutableStateMapOf<String, String>() }

                // CRITICAL FIX: Merge initial names with live names for full display/tracking coverage
                val allChildrenNamesForDisplay by remember(childrenDisplayMap.entries, sortedDisplayNamesFromParam) {
                    // Start with all live names from Firebase
                    val liveNames = childrenDisplayMap.values.toMutableSet()

                    // Add any initial names that are NOT yet in the live map
                    // This covers children whose data (key/display name) hasn't fully loaded yet (e.g., Mtu Mzii)
                    sortedDisplayNamesFromParam.forEach { initialName ->
                        // Check if the initial name (e.g., "Mtu Mzii") is present as a value in the live map
                        val isNameAlreadyLive = childrenDisplayMap.containsValue(initialName)

                        // Also, check if a *normalized* version of the initial name is present as a key
                        val normalizedInitialName = initialName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
                        val isKeyAlreadyLive = childrenKeys.contains(normalizedInitialName)

                        if (!isNameAlreadyLive && !isKeyAlreadyLive) {
                            liveNames.add(initialName)
                        }
                    }

                    // Return a sorted list of all unique names
                    mutableStateOf(liveNames.toList().sorted())
                }

                LaunchedEffect(childrenKeys) {
                    childrenKeys.forEach { key ->
                        scope.launch {
                            viewModel.getDisplayNameFlow(key).collect { displayName ->
                                if (displayName != "Loading...") {
                                    childrenDisplayMap[key] = displayName
                                }
                            }
                        }

                        scope.launch {
                            viewModel.getPhotoUrlFlow(key).collect { url ->
                                // CRITICAL FIX: Update map with the URL (now guaranteed to be real or default)
                                if (url.isNotBlank()) {
                                    childrenPhotoMap[key] = url
                                } else {
                                    // Ensure it falls back to the default if for any reason it's blank
                                    childrenPhotoMap[key] = defaultPhotoUrl
                                }
                            }
                        }
                    }
                }


                // Sync selected child's name instantly, prioritizing the first child if none is selected
                LaunchedEffect(sortedChildrenKeys, childrenDisplayMap.entries) {
                    val currentName = selectedChild.value.name

                    // 1. Determine the target key: Current selection or the first available key.
                    // Prioritize the key matching the current selection, otherwise default to the first key available for *this* parent.
                    val targetKey = sortedChildrenKeys.find { key ->
                        childrenDisplayMap[key]?.equals(currentName, ignoreCase = true) == true
                    } ?: sortedChildrenKeys.firstOrNull()

                    if (targetKey != null) {
                        val liveName = childrenDisplayMap[targetKey] ?: targetKey.replace("_", " ").let {
                            // Fallback to capitalizing the key if display name is still loading
                            it.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                        }
                        val livePhotoUrl = childrenPhotoMap[targetKey] ?: defaultPhotoUrl

                        // 2. Determine if a name update is needed (force update if current name is from the URL param)
                        val needsUpdate = currentName.isEmpty() || // Forces first selection on load
                                currentName == initialChildName || // Force update if current name is still the initial URL parameter
                                selectedChild.value.name != liveName

                        if (needsUpdate) {
                            selectedChild.value = selectedChild.value.copy(
                                name = liveName,
                                photoUrl = livePhotoUrl // Also update photo URL here
                            )
                            Log.d("🔥", "Updated selected child to: $liveName (Key: $targetKey)")
                        }
                    }
                    // Ghost cleanup
                    else if (currentName.isNotEmpty()) {
                        selectedChild.value = Child(name = "", photoUrl = defaultPhotoUrl, status = initialStatus)
                    }
                }

                Box(
                    modifier = Modifier
                        .width(uiSizes.dropdownWidth)
                        .height(if (uiSizes.isTablet) 56.dp else 48.dp)
                        .semantics { contentDescription = "Select child" }
                ) {
                    OutlinedTextField(
                        value = selectedChild.value.name,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select child",
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { childExpanded.value = !childExpanded.value }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            fontSize = if (uiSizes.isTablet) 18.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF800080),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    DropdownMenu(
                        expanded = childExpanded.value,
                        onDismissRequest = { childExpanded.value = false },
                        modifier = Modifier.width(uiSizes.dropdownWidth)
                    ) {
                        // FIX: Loop over all consolidated names for full coverage
                        allChildrenNamesForDisplay.forEach { displayFullName ->
                            // Use the live map to get the photo URL if available, otherwise use default
                            val key = childrenKeys.find { childrenDisplayMap[it] == displayFullName }
                            val livePhotoUrl = if (key != null) childrenPhotoMap[key] else defaultPhotoUrl

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        displayFullName,
                                        fontSize = if (uiSizes.isTablet) 14.sp else 12.sp,
                                        color = Color.Black
                                    )
                                },
                                onClick = {
                                    // Update selectedChild with the display name and the best available photo URL
                                    selectedChild.value = selectedChild.value.copy(
                                        name = displayFullName,
                                        photoUrl = livePhotoUrl ?: defaultPhotoUrl // Ensure default if livePhotoUrl is null
                                    )
                                    childExpanded.value = false
                                }
                            )
                        }
                    }
                }

               Spacer(modifier = Modifier.height(12.dp))

                // ETA text state
                var etaText by remember { mutableStateOf("Loading...") }

// FIX: Use the normalized key from childrenKeys that matches the selected child
                val selectedChildKey: String? = remember(selectedChild.value.name, childrenKeys) {
                    if (selectedChild.value.name.isEmpty()) return@remember null

                    // Normalize the selected name to match Firebase key format
                    val normalizedSelectedName = selectedChild.value.name.trim().lowercase()
                        .replace(Regex("[^a-z0-9]"), "_")

                    // Find the key in childrenKeys that matches this normalized name
                    childrenKeys.find { key ->
                        // Compare normalized forms
                        val normalizedKey = key.lowercase()
                        normalizedKey == normalizedSelectedName ||
                                // Also check if any display name matches (from childrenDisplayMap collected earlier)
                                childrenDisplayMap[key]?.equals(selectedChild.value.name, ignoreCase = true) == true
                    }
                }

// LaunchedEffect to collect ETA flow when we have a valid key
                LaunchedEffect(selectedChildKey) {
                    if (selectedChildKey != null) {
                        viewModel.getEtaFlowByName(selectedChildKey).collect { eta ->
                            etaText = eta
                        }
                    } else {
                        // Show loading or default message
                        etaText = "Loading..."
                    }
                }

// Also update when childrenDisplayMap updates to catch late arrivals
                LaunchedEffect(childrenDisplayMap, selectedChild.value.name) {
                    if (selectedChildKey == null && selectedChild.value.name.isNotEmpty()) {
                        // Try to find the key one more time when display map updates
                        val foundKey = childrenDisplayMap.entries.find {
                            it.value.equals(selectedChild.value.name, ignoreCase = true)
                        }?.key

                        if (foundKey != null && etaText == "Child data not found") {
                            // Retry with found key
                            viewModel.getEtaFlowByName(foundKey).collect { eta ->
                                etaText = eta
                            }
                        }
                    }
                }

// Dedicated LaunchedEffect to collect photo URL when selectedChildKey changes
                LaunchedEffect(selectedChildKey) {
                    if (selectedChildKey != null) {
                        viewModel.getPhotoUrlFlow(selectedChildKey).collect { url ->
                            // Update photoUrl only if it's new
                            if (url != selectedChild.value.photoUrl) {
                                selectedChild.value = selectedChild.value.copy(
                                    photoUrl = url
                                )
                            }
                        }
                    }
                }

                Log.d("ImageCheck", "Loading image for: ${selectedChild.value.name}")

                // Photo URL is still managed by the selectedChild state (updated by Fix B)
                val currentPhotoUrl = selectedChild.value.photoUrl

                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(if (currentPhotoUrl.isNotBlank()) currentPhotoUrl else null)
                            .crossfade(true)
                            .build(),
                        placeholder = painterResource(R.drawable.defaultchild),
                        error = painterResource(R.drawable.defaultchild)
                    ),
                    contentDescription = "Child photo for ${selectedChild.value.name}",
                    modifier = Modifier
                        .size(uiSizes.photoSize)
                        .padding(top = uiSizes.verticalSpacing)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = etaText,
                    fontSize = if (uiSizes.isTablet) 18.sp else 16.sp,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = uiSizes.verticalSpacing)
                        .semantics { contentDescription = "Bus ETA: $etaText" },
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = { },
                    modifier = Modifier
                        .width(if (uiSizes.isTablet) 140.dp else 120.dp)
                        .height(if (uiSizes.isTablet) 56.dp else 48.dp)
                        .align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (selectedStatus.value) {
                            "Bus to Pickup" -> Color(15, 255, 80)
                            "Boarded" -> Color(255, 235, 59)
                            "Dropped" -> Color(0, 0, 255)
                            "Absent" -> Color(128, 128, 128)
                            else -> Color(120, 120, 120)
                        }
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.status_bus),
                            contentDescription = "Child status: ${selectedStatus.value}",
                            modifier = Modifier.size(if (uiSizes.isTablet) 20.dp else 16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedStatus.value,
                            color = when (selectedStatus.value) {
                                "On Route", "Boarded" -> Color.Black
                                "Absent", "Dropped" -> Color.White
                                else -> Color.White
                            },
                            fontSize = if (uiSizes.isTablet) 16.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(uiSizes.verticalSpacing))

// Live bus location from Firebase (real-time)
                val busLocation by viewModel.getBusFlow("busLocation").collectAsState(initial = LatLng(-1.2921, 36.8219))

// Initial position set immediately (uses GMS CameraPosition import)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(busLocation, 15f)
                }

// Automatically follow the bus when location changes
                LaunchedEffect(busLocation) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(busLocation, 15f),
                        1000  // durationMs is optional; this works in v4.4.1
                    )
                }

                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(uiSizes.mapHeight)
                        .clip(RoundedCornerShape(12.dp)),
                    cameraPositionState = cameraPositionState
                    // Temporarily comment these out as their imports were deleted
                    // properties = MapProperties(isMyLocationEnabled = false),
                    // uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true)
                ) {
                    Marker(
                        state = MarkerState(position = busLocation),
                        title = "School Bus",
                        snippet = "Live Location",
                        // Direct GMS BitmapDescriptor for green icon (no rememberMarkerIcon needed)
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

              Spacer(modifier = Modifier.height(8.dp)) // small gap from map

                Box(
                    modifier = Modifier
                        .width(uiSizes.dropdownWidth)
                        .height(if (uiSizes.isTablet) 56.dp else 50.dp)
                        .align(Alignment.Start) // far left
                        .semantics {
                            contentDescription =
                                "Select Contact Driver, Contact School, or Report Issue"
                        }
                ) {
                    OutlinedTextField(
                        value = selectedAction.value,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Open action options",
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        quickActionExpanded.value = !quickActionExpanded.value
                                    }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            fontSize = if (uiSizes.isTablet) 18.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF800080),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    DropdownMenu(
                        expanded = quickActionExpanded.value,
                        onDismissRequest = { quickActionExpanded.value = false },
                        modifier = Modifier.width(uiSizes.dropdownWidth)
                    ) {
                        listOf(
                            "Contact Driver",
                            "Contact School",
                            "Report Issue"
                        ).forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        action,
                                        fontSize = if (uiSizes.isTablet) 14.sp else 12.sp,
                                        color = Color.Black
                                    )
                                },
                                onClick = {
                                    selectedAction.value = action
                                    showTextBox.value = true
                                    quickActionExpanded.value = false
                                }
                            )
                        }
                    }
                }

                // --- Quick Actions & Message Box (moved here so it appears under the dropdown) ---
                if (showTextBox.value) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = uiSizes.verticalSpacing,
                                start = if (uiSizes.isTablet) 24.dp else 16.dp,
                                end = if (uiSizes.isTablet) 24.dp else 16.dp
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (uiSizes.isTablet) 200.dp else 160.dp)
                                .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .onGloballyPositioned { coordinates ->
                                    scope.launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                        ) {
                            BasicTextField(
                                value = textInput.value,
                                onValueChange = { newValue ->
                                    if (newValue.length <= 300) {
                                        val autoCapitalized = buildString {
                                            append(newValue)
                                            if (isNotEmpty() && this[0].isLowerCase()) {
                                                setCharAt(0, this[0].uppercaseChar())
                                            }
                                            var i = 0
                                            while (i < length - 2) {
                                                if ((this[i] == '.' || this[i] == '!' || this[i] == '?') &&
                                                    this[i + 1] == ' ' &&
                                                    this[i + 2].isLowerCase()
                                                ) {
                                                    setCharAt(i + 2, this[i + 2].uppercaseChar())
                                                }
                                                i++
                                            }
                                        }
                                        textInput.value = autoCapitalized
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (uiSizes.isTablet) 120.dp else 100.dp)
                                    .align(Alignment.TopStart)
                                    .semantics { contentDescription = "Message input, max 300 characters" },
                                textStyle = TextStyle(
                                    color = Color.Black,
                                    fontSize = if (uiSizes.isTablet) 16.sp else 14.sp
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    keyboardType = KeyboardType.Text
                                ),
                                decorationBox = { innerTextField ->
                                    if (textInput.value.isEmpty()) {
                                        Text(
                                            text = when (selectedAction.value) {
                                                "Contact Driver" -> "Message to the driver, max 300 characters..."
                                                "Contact School" -> "Message to the school, max 300 characters..."
                                                else -> "Type your issue, max 300 characters..."
                                            },
                                            color = Color.Gray,
                                            fontSize = if (uiSizes.isTablet) 16.sp else 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            Button(
                                onClick = {
                                    if (textInput.value.isNotBlank() && !textInput.value.endsWith(".")) {
                                        textInput.value = textInput.value.trim() + "."
                                    }
                                    val normalizedKey =
                                        selectedChild.value.name.trim().lowercase()
                                            .replace(Regex("[^a-z0-9]"), "_")
                                    if (normalizedKey.isNotEmpty()) {
                                        database.child("children").child(normalizedKey)
                                            .child("messages").push().setValue(
                                                mapOf(
                                                    "action" to selectedAction.value,
                                                    "message" to textInput.value
                                                )
                                            )
                                    }
                                    textInput.value = ""
                                    showTextBox.value = false
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(if (uiSizes.isTablet) 100.dp else 80.dp)
                                    .height(if (uiSizes.isTablet) 36.dp else 28.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF800080)
                                )
                            ) {
                                Text(
                                    text = "Send",
                                    color = Color.White,
                                    fontSize = if (uiSizes.isTablet) 14.sp else 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.call_icon),
                                contentDescription = when (selectedAction.value) {
                                    "Contact Driver" -> "Call driver"
                                    "Contact School" -> "Call school"
                                    else -> "Call support"
                                },
                                modifier = Modifier.size(if (uiSizes.isTablet) 20.dp else 16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Call",
                                color = Color.Black,
                                fontSize = if (uiSizes.isTablet) 16.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { /* Placeholder: Initiate call */ }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close message box",
                                modifier = Modifier
                                    .size(if (uiSizes.isTablet) 20.dp else 16.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showTextBox.value = false }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Close",
                                color = Color.Black,
                                fontSize = if (uiSizes.isTablet) 16.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showTextBox.value = false }
                            )
                        }
                    }
                }
            }
        }
    )
}


