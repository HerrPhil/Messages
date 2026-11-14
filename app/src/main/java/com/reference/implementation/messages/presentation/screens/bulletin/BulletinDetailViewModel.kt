package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reference.implementation.domain.use_case.GetBulletinByIdUseCase
import com.reference.implementation.domain.use_case.Resource
import com.reference.implementation.messages.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BulletinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getBulletinByIdUseCase: GetBulletinByIdUseCase
) : ViewModel() {

    // Automatically extracts the 'id' from the strongly-typed BulletinDetail route!
    private val bulletinId: Int = checkNotNull(savedStateHandle.toRoute<Route.BulletinDetail>().id)

    val uiState: StateFlow<BulletinDetailUiState> =
        // Simply map the database/resource cache stream
        getBulletinByIdUseCase(bulletinId).map { bulletinResource ->
            when (bulletinResource) {
                is Resource.Loading -> BulletinDetailUiState.Loading
                is Resource.Error -> BulletinDetailUiState.Error(bulletinResource.message)
                is Resource.Success -> {
                    val bulletin = bulletinResource.data
                    if (bulletin != null) {
                        BulletinDetailUiState.Success(data = bulletin.toBulletinUiDetail())
                    } else {
                        // THE TRAP-DOOR RESOLUTION: If the row is deleted, pass this
                        // clean state token so your Composable pops back safely without flashing a red error screen!
                        BulletinDetailUiState.BulletinDeletedBackNavigate
                    }
                }
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = BulletinDetailUiState.Loading
            )
}