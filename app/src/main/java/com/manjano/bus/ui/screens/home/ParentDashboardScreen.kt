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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.google.firebase.database.FirebaseDatabase
import com.manjano.bus.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState


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
    parentName: String,
    childrenNames: String,
    initialStatus: String = "On Route",
    viewModel: ParentDashboardViewModel = hiltViewModel()
) {
    val database = FirebaseDatabase.getInstance().reference

    val selectedAction = remember { mutableStateOf("Contact Driver") }
    val showTextBox = remember { mutableStateOf(false) }
    val textInput = remember { mutableStateOf("") }
    val quickActionExpanded = remember { mutableStateOf(false) }
    val selectedStatus = remember { mutableStateOf(initialStatus) }
    val sortedNames = childrenNames.split(",").map { it.trim() }.sorted()

// Create children list once
    val children = sortedNames.map { name ->
        Child(
            name = name,
            photoUrl = defaultPhotoUrl,
            status = if (name == sortedNames.firstOrNull()) initialStatus else "On Route"
        )
    }.distinctBy { it.name }

// Create selectedChild state once
    val selectedChild = remember {
        mutableStateOf(
            children.firstOrNull() ?: Child("", defaultPhotoUrl, "On Route")
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

            // 🧩 Fix photo links in Realtime Database
            viewModel.fetchAndRepairChildImages(storageFiles)
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

                Text(
                    text = "Hello ${parentName.split(" ").firstOrNull() ?: parentName}",
                    fontSize = if (uiSizes.isTablet) 24.sp else 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .semantics {
                            contentDescription = "Greeting: Hello ${
                                parentName.split(" ").firstOrNull() ?: parentName
                            }"
                        },
                    textAlign = TextAlign.Center
                )

                val firstNames = childrenNames.split(",")
                    .map { it.trim().split(" ").firstOrNull() ?: it.trim() }
                val trackingText = when (firstNames.size) {
                    0 -> ""
                    1 -> "Tracking ${firstNames[0]}"
                    2 -> "Tracking ${firstNames[0]} & ${firstNames[1]}"
                    else -> "Tracking " + firstNames.dropLast(1)
                        .joinToString(", ") + " & ${firstNames.last()}"
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

                val childrenKeys by viewModel.childrenKeys.collectAsState()
                val sortedChildrenKeys = childrenKeys.sorted()
                val childrenDisplayMap = remember { mutableStateMapOf<String, String>() }
                val childrenPhotoMap = remember { mutableStateMapOf<String, String>() }

                LaunchedEffect(childrenKeys, storageFiles) {
                    childrenKeys.forEach { key ->
                        // Collect displayName (existing behavior)
                        scope.launch {
                            viewModel.getDisplayNameFlow(key).collect { displayName ->
                                if (displayName != "Loading...") {
                                    childrenDisplayMap[key] = displayName
                                    // If currently selected child matches this key, update its display name
                                    if (selectedChild.value.name.trim().lowercase() == key) {
                                        selectedChild.value =
                                            selectedChild.value.copy(name = displayName)
                                    }
                                }
                            }
                        }

                        scope.launch {
                            viewModel.getPhotoUrlFlow(key).collect { url ->
                                val safeUrl = url.ifBlank { defaultPhotoUrl }

                                if (childrenPhotoMap[key] != safeUrl) {
                                    childrenPhotoMap[key] = safeUrl
                                    Log.d("🖼️ childrenPhotoMap", "Updated $key -> $safeUrl")

                                    val selectedKey = selectedChild.value.name.trim().lowercase()
                                    if (selectedKey == key) {
                                        // immediate UI update for selected child
                                        selectedChild.value =
                                            selectedChild.value.copy(photoUrl = safeUrl)
                                    }
                                }
                            }
                        }
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
                        childrenKeys.forEach { key ->
                            val displayName = childrenDisplayMap[key] ?: return@forEach
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        displayName,
                                        fontSize = if (uiSizes.isTablet) 14.sp else 12.sp,
                                        color = Color.Black
                                    )
                                },
                                onClick = {
                                    // Grab latest photo URL from the live map, fallback to default
                                    val url = childrenPhotoMap[key]?.takeIf { it.isNotBlank() } ?: selectedChild.value.photoUrl
                                    selectedChild.value = selectedChild.value.copy(
                                        name = displayName,
                                        photoUrl = url
                                    )
                                    childExpanded.value = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                var etaText by remember { mutableStateOf("Loading...") }

                LaunchedEffect(selectedChild.value.name, storageFiles) {

                    // --- STATUS FLOW ---
                    launch {
                        viewModel.getStatusFlow(selectedChild.value.name)
                            .collectLatest { status: String ->
                                selectedStatus.value = status
                            }
                    }

                    // --- ETA FLOW ---
                    launch {
                        viewModel.getEtaFlowByName(selectedChild.value.name)
                            .collectLatest { eta: String ->
                                etaText = eta
                            }
                    }

                    // --- PHOTO URL FLOW (NEW) ---
                    launch {
                        viewModel.getPhotoUrlFlow(selectedChild.value.name)
                            .collectLatest { url ->
                                val safeUrl = url.ifBlank { defaultPhotoUrl }

                                // Only update if the URL has actually changed and it's not the default flipping
                                if (selectedChild.value.photoUrl != safeUrl &&
                                    (safeUrl != defaultPhotoUrl || selectedChild.value.photoUrl == defaultPhotoUrl)
                                ) {
                                    selectedChild.value =
                                        selectedChild.value.copy(photoUrl = safeUrl)
                                    Log.d(
                                        "🖼️ SelectedChild",
                                        "Updated ${selectedChild.value.name} -> $safeUrl"
                                    )
                                }
                            }
                    }
                }

                Log.d("🖼️ ImageCheck", "Loading image: ${selectedChild.value.photoUrl}")

                // --- CHILD PHOTO ---
                val childPhotoUrl = selectedChild.value.photoUrl.ifBlank { defaultPhotoUrl }

                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(childPhotoUrl)
                        .memoryCacheKey(childPhotoUrl) // stable key
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    )

                Image(
                    painter = painter,
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
                val busLocation = remember { mutableStateOf(LatLng(-1.2921, 36.8219)) }

                val cameraPositionState = rememberCameraPositionState {
                    position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(busLocation.value, 15f)
                }

                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(uiSizes.mapHeight),
                    cameraPositionState = cameraPositionState
                ) {
                    Marker(
                        state = MarkerState(position = busLocation.value),
                        title = "Bus",
                        snippet = "Current Location"
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

