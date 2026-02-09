package net.dom53.inkita.ui.people

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.PersonDto
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.ui.common.DownloadState
import net.dom53.inkita.ui.common.DownloadStateBadge
import net.dom53.inkita.ui.common.DownloadStateResolver
import net.dom53.inkita.ui.common.personCoverUrl
import net.dom53.inkita.ui.common.seriesCoverUrl
import net.dom53.inkita.ui.seriesdetail.ChapterListV2

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PersonDetailScreen(
    personDetailScreen: PersonScreen,
    cacheManager: CacheManager,
    appPreferences: AppPreferences,
    onOpenSeries: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: PersonDetailViewModel =
        viewModel(
            factory =
                PersonDetailViewModel.provideFactory(personDetailScreen.personName, cacheManager, appPreferences),
        )
    val uiState by viewModel.state.collectAsState()
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )

    val hasDetail = uiState.detail != null
    val isRefreshing = uiState.isLoading && hasDetail
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = { viewModel.reload(forceRefresh = true) },
        )
    var selectedChapter by remember { mutableStateOf<ChapterDto?>(null) }
    var selectedChapterIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
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
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pullRefresh(pullRefreshState),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val detail = uiState.detail
                            if (detail != null) {
                                if (detail.person != null) {
                                    PersonBasicInfo(detail.person, config)
                                }
                                if (detail.seriesKnownFor != null) {
                                    PersonSeriesKnownFor(
                                        seriesKnownFor = detail.seriesKnownFor,
                                        downloadStates = uiState.seriesDownloadStates,
                                        config = config,
                                        appPreferences = appPreferences,
                                        onOpenSeries = onOpenSeries,
                                    )
                                }
                                if (detail.chaptersByRole != null) {
                                    PersonChaptersByRole(
                                        chaptersByRole = detail.chaptersByRole,
                                        config = config,
                                        onChapterClick = { chapter, index ->
                                            selectedChapter = chapter
                                            selectedChapterIndex = index
                                        },
                                    )
                                }
                            }
                        }
                        PullRefreshIndicator(
                            refreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonBasicInfo(
    person: PersonDto,
    config: AppConfig,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(200.dp)
                    .clip(CircleShape),
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
        Column(modifier = Modifier.animateContentSize()) {
            var expanded by remember { mutableStateOf(false) }
            var isOverflowing by remember { mutableStateOf(false) }
            Text(
                text = person.description ?: "No Description",
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    if (!expanded) {
                        isOverflowing = it.hasVisualOverflow
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isOverflowing || expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            text = if (expanded) stringResource(R.string.general_less) else stringResource(R.string.general_more),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonSeriesKnownFor(
    seriesKnownFor: List<SeriesDto>,
    downloadStates: Map<Int, DownloadState>?,
    config: AppConfig,
    appPreferences: AppPreferences,
    onOpenSeries: (Int) -> Unit,
) {
    val showDownloadBadges by appPreferences.showDownloadBadgesFlow.collectAsState(initial = true)
    Row(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Known For",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(seriesKnownFor) { series ->
                    SeriesCard(
                        series = series,
                        config = config,
                        showDownloadBadges = showDownloadBadges,
                        downloadState = downloadStates?.get(series.id),
                        onOpenSeries = onOpenSeries,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonChaptersByRole(
    chaptersByRole: Map<Int, List<ChapterDto>>,
    config: AppConfig,
    onChapterClick: (ChapterDto, Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column {
            chaptersByRole.forEach { role ->
                if (role.value.isNotEmpty()) {
                    Text(
                        text = "As a ${convertRoleToText(role.key)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    PersonChaptersForRole(
                        chapterList = role.value,
                        config = config,
                        onChapterClick = onChapterClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonChaptersForRole(
    chapterList: List<ChapterDto>,
    config: AppConfig,
    onChapterClick: (ChapterDto, Int) -> Unit,
) {
    val context = LocalContext.current
    val downloadDao = remember(context.applicationContext) { InkitaDatabase.getInstance(context.applicationContext).downloadV2Dao() }
    val chapterDownloadStates = remember { mutableStateMapOf<Int, DownloadState>() }

    LaunchedEffect(chapterList) {
        chapterDownloadStates.clear()
        chapterList.forEach { chapter ->
            launch {
                downloadDao.observeItemsForChapter(chapter.id).collect { items ->
                    chapterDownloadStates[chapter.id] =
                        DownloadStateResolver.resolveChapterState(
                            format = Format.fromId(chapter.format),
                            chapter = chapter,
                            items = items,
                        )
                }
            }
        }
    }

    ChapterListV2(
        chapters = chapterList,
        config = config,
        downloadStates = chapterDownloadStates,
        onChapterClick = onChapterClick,
        onChapterLongPress = { _, _ -> },
    )
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

@Composable
private fun SeriesCard(
    series: SeriesDto,
    config: AppConfig,
    showDownloadBadges: Boolean,
    downloadState: DownloadState?,
    onOpenSeries: (Int) -> Unit,
) {
    val imageUrl = seriesCoverUrl(config, series.id)

    Column(
        modifier =
            Modifier
                .width(120.dp)
                .padding(bottom = 4.dp)
                .clickable { onOpenSeries(series.id) },
    ) {
        Box {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            if (showDownloadBadges) {
                DownloadStateBadge(
                    state = downloadState ?: DownloadState.None,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 10.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = series.name ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun convertRolesToText(roles: List<Int>): String = roles.joinToString(", ", transform = ::convertRoleToText)

private fun convertRoleToText(role: Int): String =
    when (role) {
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
