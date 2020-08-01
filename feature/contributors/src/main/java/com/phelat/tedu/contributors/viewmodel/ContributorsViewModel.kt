package com.phelat.tedu.contributors.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phelat.tedu.analytics.ExceptionLogger
import com.phelat.tedu.analytics.di.qualifier.Development
import com.phelat.tedu.androidresource.ResourceProvider
import com.phelat.tedu.androidresource.input.StringArg
import com.phelat.tedu.androidresource.input.StringId
import com.phelat.tedu.androidresource.resource.StringResource
import com.phelat.tedu.contributors.R
import com.phelat.tedu.contributors.di.scope.ContributorsScope
import com.phelat.tedu.contributors.entity.ContributorEntity
import com.phelat.tedu.contributors.request.ContributionsRequest
import com.phelat.tedu.contributors.response.ContributorResponse
import com.phelat.tedu.contributors.state.ContributionsViewState
import com.phelat.tedu.contributors.view.ContributorItem
import com.phelat.tedu.contributors.view.PaginationLoadingItem
import com.phelat.tedu.coroutines.Dispatcher
import com.phelat.tedu.datasource.Readable
import com.phelat.tedu.lifecycle.update
import com.xwray.groupie.Section
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributorsScope
class ContributorsViewModel @Inject constructor(
    private val dataSource: Readable.Suspendable.IO<ContributionsRequest, List<ContributorResponse>>,
    private val dispatcher: Dispatcher,
    @Development private val logger: ExceptionLogger,
    private val stringProvider: ResourceProvider<StringId, StringResource>,
    private val stringArgProvider: ResourceProvider<StringArg, StringResource>
) : ViewModel() {

    private val contributorsSection = Section()

    private val _contributorsObservable = MutableLiveData<List<Section>>(listOf(contributorsSection))
    val contributorsObservable: LiveData<List<Section>> = _contributorsObservable

    private val _viewStateObservable = MutableLiveData(ContributionsViewState())
    val viewStateObservable: LiveData<ContributionsViewState> = _viewStateObservable

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        isPaginationLoading = false
        viewModelScope.launch {
            delay(DELAY_BEFORE_SHOWING_ERROR_IN_MILLIS)
            logger.log(error)
            _viewStateObservable.update {
                copy(isProgressVisible = false, isErrorVisible = true, isListVisible = false)
            }
        }
    }

    private var isPaginationLoading = false

    private val paginationLoadingItem = PaginationLoadingItem()

    private var page = 0

    init {
        viewModelScope.launch(context = exceptionHandler) {
            _viewStateObservable.update { copy(isProgressVisible = true) }
            fetchContributions().apply(contributorsSection::addAll)
            _viewStateObservable.update { copy(isProgressVisible = false, isListVisible = true) }
            _contributorsObservable.value = listOf(contributorsSection)
        }
    }

    fun onRetryButtonClick() {
        viewModelScope.launch(context = exceptionHandler) {
            delay(DELAY_FOR_RETRY_IN_MILLIS)
            _viewStateObservable.update { copy(isErrorVisible = false, isProgressVisible = true) }
            fetchContributions().apply(contributorsSection::addAll)
            _viewStateObservable.update { copy(isProgressVisible = false, isListVisible = true) }
            _contributorsObservable.value = listOf(contributorsSection)
        }
    }

    private suspend fun fetchContributions(): List<ContributorItem> {
        return withContext(context = dispatcher.iO) {
            dataSource.read(ContributionsRequest(page))
                .run(::mapResponseToEntity)
                .map(::ContributorItem)
                .also { page++ }
        }
    }

    private fun mapResponseToEntity(response: List<ContributorResponse>): List<ContributorEntity> {
        val contributors = mutableListOf<ContributorEntity>()
        val bugReportText = stringProvider.getResource(StringId(R.string.contributors_bug_report_text)).resource
        loop@ for (value in response) {
            contributors += ContributorEntity(
                contribution = when (value.contribution) {
                    CONTRIBUTION_BUG_REPORT -> bugReportText
                    else -> continue@loop
                },
                contributionLink = value.contributionLink,
                contributor = value.contributor,
                contributionNumber = stringArgProvider.getResource(
                    StringArg(
                        R.string.general_number_placeholder,
                        value.contributionNumber
                    )
                ).resource
            )
        }
        return contributors
    }

    fun onReachTheEndOfList() {
        if (isPaginationLoading) return
        isPaginationLoading = true
        contributorsSection.setFooter(paginationLoadingItem)
        viewModelScope.launch(context = exceptionHandler) {
            fetchContributions().apply(contributorsSection::addAll)
            contributorsSection.removeFooter()
            _contributorsObservable.value = listOf(contributorsSection)
            isPaginationLoading = false
        }
    }

    companion object {
        private const val DELAY_FOR_RETRY_IN_MILLIS = 200L
        private const val DELAY_BEFORE_SHOWING_ERROR_IN_MILLIS = 500L
        private const val CONTRIBUTION_BUG_REPORT = "bug-report"
    }
}