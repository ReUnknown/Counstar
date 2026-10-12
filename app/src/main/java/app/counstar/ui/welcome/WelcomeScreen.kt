package app.counstar.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.counstar.BuildConfig
import app.counstar.ui.home.CounstarLogo

@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onPreview: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CounstarLogo(size = 88.dp)
        Spacer(Modifier.height(32.dp))
        Text("Counstar", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your YouTube, nicer to look at.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onPreview, modifier = Modifier.padding(top = 8.dp)) { Text("Look around without signing in") }
        }
    }
}
