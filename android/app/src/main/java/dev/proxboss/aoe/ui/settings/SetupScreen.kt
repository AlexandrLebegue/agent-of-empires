package dev.proxboss.aoe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SetupScreen(
    viewModel: SettingsViewModel,
    onSaved: () -> Unit,
) {
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "AoE Companion",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Connectez-vous à votre instance Agent of Empires pour piloter vos sessions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                ConnectionForm(
                    baseUrl = baseUrl,
                    token = token,
                    testState = testState,
                    onBaseUrlChange = viewModel::updateBaseUrl,
                    onTokenChange = viewModel::updateToken,
                    onTest = viewModel::testConnection,
                    onSave = { viewModel.saveConnection(onSaved) },
                    saveLabel = "Se connecter",
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Le jeton se trouve dans ~/.config/agent-of-empires/serve.token sur la machine qui héberge AoE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
