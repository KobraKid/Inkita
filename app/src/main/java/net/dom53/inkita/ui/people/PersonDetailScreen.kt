package net.dom53.inkita.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.PersonDto
import net.dom53.inkita.ui.common.personCoverUrl

@Composable
fun PersonDetailScreen(
    personDetailScreen: PersonScreen,
    appPreferences: AppPreferences,
) {
    val viewModel: PersonDetailViewModel =
        viewModel(
            factory =
                PersonDetailViewModel.provideFactory(personDetailScreen.personName, appPreferences),
        )
    val uiState by viewModel.state.collectAsState()
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )

    val hasDetail = uiState.detail != null

    when {
        uiState.isLoading && !hasDetail -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(id = net.dom53.inkita.R.string.general_loading),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        uiState.error != null && !hasDetail -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error ?: stringResource(id = net.dom53.inkita.R.string.general_error),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        else -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val detail = uiState.detail
                val person = detail?.person
                if (person != null) {
                    PersonBasicInfo(person, config)
                    PersonWorks(person)
                }
            }
        }
    }
}

@Composable
fun PersonBasicInfo(
    person: PersonDto,
    config: AppConfig,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.width(200.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val imageUrl = personCoverUrl(config, person.id)
            val imageModifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            if (imageUrl == null) {
                PersonPlaceholder(imageModifier)
            } else {
                SubcomposeAsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = imageModifier,
                    loading = { PersonPlaceholder(imageModifier) },
                    error = { PersonPlaceholder(imageModifier) },
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleLarge,
            )
            if (person.aliases.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Also known as",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = person.aliases.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (person.roles != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Roles",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = convertRolesToText(person.roles),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(person.description ?: "No Description")
    }
}

@Composable
fun PersonWorks(person: PersonDto) {
    Row {
        Column {
            Text(
                text = "Known For",
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(emptyList<Unit>()) {
                }
            }
        }
    }
}

@Composable
private fun PersonPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}

private fun convertRolesToText(roles: List<Int>): String =
    roles.joinToString(", ") {
        when (it) {
            3 -> "Writer"
            4 -> "Penciller"
            5 -> "Inker"
            6 -> "Colorist"
            7 -> "Letterer"
            8 -> "Artist"
            9 -> "Editor"
            10 -> "Publisher"
            11 -> "Character"
            12 -> "Translator"
            13 -> "Imprint"
            14 -> "Team"
            15 -> "Location"
            else -> "Other"
        }
    }
