package net.dom53.inkita.ui.people

import kotlinx.serialization.Serializable
import net.dom53.inkita.data.api.dto.PersonDto

@Serializable
data class PersonScreen(
    val personName: String,
)

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: PersonDetail? = null,
)

data class PersonDetail(
    val person: PersonDto?,
)
