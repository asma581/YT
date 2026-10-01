package com.focustube.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustube.app.R
import com.focustube.app.ui.components.VideoCard

private val SUGGESTED_TOPICS = listOf(
    "Algebra", "Calculus", "Physics", "Chemistry", "Biology", "World history",
    "Python programming", "English grammar", "Economics", "Drawing basics",
)

@Composable
fun LearnScreen(
    onVideoClick: (videoId: String) -> Unit,
    viewModel: LearnViewModel = viewModel(factory = LearnViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LearnContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onLoadMore = viewModel::loadMore,
        onVideoClick = onVideoClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearnContent(
    state: LearnUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = { query: String ->
        focusManager.clearFocus()
        onSearch(query)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.learn_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(state.query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        when {
            state.error != null && state.videos.isEmpty() -> Message(state.error)

            state.isLoading && state.videos.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.searchedQuery == null -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.learn_intro), style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    SUGGESTED_TOPICS.forEach { topic ->
                        AssistChip(onClick = { submit(topic) }, label = { Text(topic) })
                    }
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.hiddenCount > 0) {
                    item {
                        Text(
                            pluralStringResource(R.plurals.learn_hidden_count, state.hiddenCount, state.hiddenCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.videos.isEmpty()) {
                    item { Message(stringResource(R.string.learn_no_results)) }
                }
                items(state.videos, key = { it.id }) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video.id) })
                }
                item {
                    when {
                        state.isLoading -> Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
                        state.error != null -> Message(state.error)
                        state.nextPageToken != null -> OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.learn_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}
