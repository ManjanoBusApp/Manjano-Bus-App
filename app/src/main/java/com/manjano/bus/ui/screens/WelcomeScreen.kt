package com.manjano.bus.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjano.bus.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

// Lottie imports
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun WelcomeScreen(
    onParentClick: () -> Unit,
    onDriverClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.height(40.dp))

            // App logo (centered, 60.dp)
            Image(
                painter = painterResource(id = R.drawable.ic_bus),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Titles (centered, bold, black)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome To",
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Manjano Bus App",
                    color = Color.Black,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Slight gap before buttons (pushes buttons a little up)
            Spacer(modifier = Modifier.height(36.dp))

            // Role Buttons row (slightly higher since we removed the big image)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                RoleButton(
                    text = "Parent",
                    backgroundColor = Color.Black,
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onParentClick
                )

                RoleButton(
                    text = "Driver",
                    backgroundColor = Color.White,
                    textColor = Color.Black,
                    borderColor = Color.Black,
                    modifier = Modifier.weight(1f),
                    onClick = onDriverClick
                )

                RoleButton(
                    text = "Admin",
                    backgroundColor = Color(0xFF800080),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onAdminClick
                )
            }

            // small spacing after buttons
            Spacer(modifier = Modifier.height(8.dp))

            // Use Spacer with weight to push the Lottie to the lower portion (last quarter-ish)
            Spacer(modifier = Modifier.height(60.dp))

            // Lottie bus animation (make sure res/raw/school_bus.json exists)
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.school_bus)
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(bottom = 12.dp)

            )
        }
    }
}

@Composable
fun RoleButton(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    borderColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var clicked by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (clicked) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100)
    )

    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            clicked = true
            scope.launch {
                delay(100)
                clicked = false
            }
            onClick()
        },
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        border = borderColor?.let { BorderStroke(1.dp, it) },
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp), // Reduce padding to save space
        modifier = modifier
            .height(50.dp)
            .scale(scale)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp, // Slightly smaller to fit small screens
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}
