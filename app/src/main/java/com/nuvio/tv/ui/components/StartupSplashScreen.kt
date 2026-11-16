package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.livetv.branding.LiveTvBranding

@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avatarColor = remember(profileColorHex) {
        profileColorHex?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        } ?: Color(0xFF2374EE)
    }
    val background = remember(avatarColor) {
        Brush.verticalGradient(listOf(
            Color(0xFF080D1A),
            lerp(Color(0xFF101C34), avatarColor, 0.16f),
            Color(0xFF080D1A)
        ))
    }
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF080D1A))) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            val request = remember(context, profileBackgroundUrl, backgroundCacheKey) {
                val imageData: Any = if (profileBackgroundUrl.startsWith("file:")) {
                    java.io.File(java.net.URI(profileBackgroundUrl))
                } else profileBackgroundUrl
                ImageRequest.Builder(context)
                    .data(imageData)
                    .crossfade(false)
                    .apply {
                        if (backgroundCacheKey != null) {
                            memoryCacheKey(backgroundCacheKey)
                            diskCacheKey(backgroundCacheKey)
                            placeholderMemoryCacheKey(backgroundCacheKey)
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color(0xA6080D1A)))
        } else if (!skipGradient) {
            // One static gradient: no offscreen compositing, blur, video or extra launch delay.
            Box(Modifier.fillMaxSize().background(background))
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo_mark),
                contentDescription = null,
                modifier = Modifier.size(112.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = LiveTvBranding.APP_NAME,
                color = Color(0xFFF4F7FF),
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.one_tv_startup_tagline),
                color = Color(0xFFB6C5DD),
                fontSize = 16.sp
            )
            Spacer(Modifier.height(36.dp))
            LoadingIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF34CDFF))
        }
    }
}
