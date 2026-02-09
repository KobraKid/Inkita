package net.dom53.inkita.ui.people

import kotlinx.serialization.Serializable
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.PersonDto
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.ui.common.DownloadState

@Serializable
data class PersonScreen(
    val personName: String,
)

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: PersonDetail? = null,
    val seriesDownloadStates: Map<Int, DownloadState>? = null,
)

data class PersonDetail(
    val person: PersonDto?,
    val seriesKnownFor: List<SeriesDto>?,
    val chaptersByRole: Map<Int, List<ChapterDto>>?,
)
