package org.openbenches.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openbenches.data.TodoBench
import org.openbenches.data.TodoBenchStore
import org.openbenches.ui.theme.OpenBenchesHeaderBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoBenchesScreen(
    onBack: () -> Unit,
    onShowOnMap: (TodoBench) -> Unit,
    onTodosChanged: () -> Unit,
    globalBenchCount: Int?,
    globalBenchCountFailed: Boolean,
    authToken: String?,
    authProvider: String?,
    authPictureUrl: String?,
    authUsername: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { TodoBenchStore(context) }
    var todos by remember { mutableStateOf(store.list().sortedByDescending { it.savedAtMs }) }

    fun refresh() {
        todos = store.list().sortedByDescending { it.savedAtMs }
        onTodosChanged()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 0.dp),
            ) {
                OpenBenchesHeaderBanner(
                    globalBenchCount = globalBenchCount,
                    globalBenchCountFailed = globalBenchCountFailed,
                    centerCountOnly = true,
                    authToken = authToken,
                    authProvider = authProvider,
                    authPictureUrl = authPictureUrl,
                    authUsername = authUsername,
                    onClick = onBack,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Text(
                text = "To do benches",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Locations where you saw a bench but could not take a photo. " +
                    "Hold your finger on the map for 3 seconds to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (todos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No to do locations yet",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(todos, key = { it.id }) { todo ->
                        TodoBenchListItem(
                            todo = todo,
                            onShowOnMap = { onShowOnMap(todo) },
                            onDelete = {
                                store.delete(todo.id)
                                refresh()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoBenchListItem(
    todo: TodoBench,
    onShowOnMap: () -> Unit,
    onDelete: () -> Unit,
) {
    val savedLabel = remember(todo.savedAtMs) {
        DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(todo.savedAtMs))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%.5f, %.5f",
                        todo.latitude,
                        todo.longitude,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Saved $savedLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                todo.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            IconButton(onClick = onShowOnMap) {
                Icon(Icons.Filled.Place, contentDescription = "Show on map")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}
