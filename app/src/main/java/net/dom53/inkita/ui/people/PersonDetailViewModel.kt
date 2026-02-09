package net.dom53.inkita.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.ui.common.DownloadState
import net.dom53.inkita.ui.common.DownloadStateResolver
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2
import retrofit2.Response

class PersonDetailViewModel(
    val personName: String,
    private val cacheManager: CacheManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(PersonDetailUiState())
    val state: StateFlow<PersonDetailUiState> = _state
    private var latestConfig: net.dom53.inkita.core.storage.AppConfig? = null
    private val downloadDao =
        InkitaDatabase.getInstance(appPreferences.appContext).downloadV2Dao()
    private var lastDownloadedItems: List<DownloadedItemV2Entity> = emptyList()

    init {
        load()
    }

    fun reload(forceRefresh: Boolean) {
        load(forceRefresh)
    }

    private fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d(
                    "PersonDetail",
                    "Load person=$personName",
                )
            }
            val config = appPreferences.configFlow.first()
            latestConfig = config
            if (!config.isConfigured) {
                _state.update { it.copy(isLoading = false, error = "Not configured") }
                return@launch
            }

            try {
                val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
                val errors = mutableListOf<String>()

                val personDeferred = async { api.getPerson(personName) }
                val personResponse = personDeferred.await()
                val person = personResponse.extract("person", errors)

                val seriesKnownForDeferred = async { api.getPersonSeriesKnownFor(person?.id ?: 0) }
                val seriesKnownForResponse = seriesKnownForDeferred.await()
                val seriesKnownFor = seriesKnownForResponse.extract("seriesKnownFor", errors)

                val chaptersByRole =
                    person?.roles?.associateBy(keySelector = { it }) { role ->
                        val chaptersForRoleDeferred = async { api.getPersonChaptersByRole(person.id, role) }
                        val chaptersForRoleResponse = chaptersForRoleDeferred.await()
                        val chaptersForRole = chaptersForRoleResponse.extract("chaptersForRole ($role)", errors)
                        chaptersForRole.orEmpty()
                    }

                _state.update {
                    it.copy(
                        isLoading = false,
                        error = errors.firstOrNull(),
                        detail =
                            PersonDetail(
                                person = person,
                                seriesKnownFor = seriesKnownFor,
                                chaptersByRole = chaptersByRole,
                            ),
                    )
                }
            } catch (e: Exception) {
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.e("PersonDetail", "Load failed person=$personName", e)
                }
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load person detail") }
            }
            observeDownloadStates()
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadDao
                .observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
                .collectLatest { items ->
                    lastDownloadedItems = items
                    updateDownloadStates(items)
                }
        }
    }

    private suspend fun updateDownloadStates(items: List<DownloadedItemV2Entity>) {
        val seriesInfoById = buildSeriesInfoMap(state.value.detail?.seriesKnownFor)
        if (seriesInfoById.isEmpty()) {
            _state.update { it.copy(seriesDownloadStates = emptyMap()) }
            return
        }
        val cacheIds =
            seriesInfoById.values
                .map { it.id }
                .distinct()
                .sorted()
        val cachedDetails =
            withContext(Dispatchers.IO) {
                val result = mutableMapOf<Int, InkitaDetailV2>()
                cacheIds.forEach { id ->
                    cacheManager.getCachedSeriesDetailV2(id)?.let { result[id] = it }
                }
                result
            }
        val states =
            buildSeriesDownloadStates(
                seriesInfoById = seriesInfoById,
                downloadedItems = items,
                cachedDetails = cachedDetails,
            )
        _state.update { it.copy(seriesDownloadStates = states) }
    }

    private data class SeriesDownloadInfo(
        val id: Int,
        val format: Format?,
        val pages: Int?,
    )

    private fun buildSeriesInfoMap(seriesList: List<SeriesDto>?): Map<Int, SeriesDownloadInfo> {
        val map = mutableMapOf<Int, SeriesDownloadInfo>()
        seriesList?.forEach { series ->
            map[series.id] =
                SeriesDownloadInfo(
                    id = series.id,
                    format = Format.fromId(series.format),
                    pages = series.pages,
                )
        }
        return map
    }

    private fun buildSeriesDownloadStates(
        seriesInfoById: Map<Int, SeriesDownloadInfo>,
        downloadedItems: List<DownloadedItemV2Entity>,
        cachedDetails: Map<Int, InkitaDetailV2>,
    ): Map<Int, DownloadState> {
        val itemsBySeries =
            downloadedItems
                .filter { it.seriesId != null }
                .groupBy { it.seriesId!! }
        val result = mutableMapOf<Int, DownloadState>()
        seriesInfoById.forEach { (id, info) ->
            val items = itemsBySeries[id].orEmpty()
            val detail = cachedDetails[id]
            val format = info.format ?: Format.fromId(detail?.series?.format)
            result[id] =
                DownloadStateResolver.resolveSeriesState(
                    format = format,
                    pagesHint = info.pages,
                    detail = detail?.detail,
                    items = items,
                )
        }
        return result
    }

    private fun <T> Response<T>.extract(
        label: String,
        errors: MutableList<String>,
    ): T? {
        if (!isSuccessful) {
            errors.add("$label: HTTP ${code()} ${message()}")
            return null
        }
        return body()
    }

    companion object {
        fun provideFactory(
            personName: String,
            cacheManager: CacheManager,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return PersonDetailViewModel(personName, cacheManager, appPreferences) as T
                }
            }
    }
}
