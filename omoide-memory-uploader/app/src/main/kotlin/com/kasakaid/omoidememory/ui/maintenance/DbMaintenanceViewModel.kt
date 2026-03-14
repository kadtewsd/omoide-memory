package com.kasakaid.omoidememory.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryDao
import com.kasakaid.omoidememory.data.UploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DbMaintenanceViewModel
    @Inject
    constructor(
        private val omoideMemoryDao: OmoideMemoryDao,
    ) : ViewModel() {
        private val _rows = MutableStateFlow<List<OmoideMemory>>(emptyList())
        val rows: StateFlow<List<OmoideMemory>> = _rows.asStateFlow()

        private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
        val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _filterState = MutableStateFlow<UploadState?>(null)
        val filterState: StateFlow<UploadState?> = _filterState.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            viewModelScope.launch {
                _isRefreshing.value = true
                val filter = _filterState.value
                _rows.value =
                    if (filter == null) {
                        omoideMemoryDao.getAll()
                    } else {
                        omoideMemoryDao.findBy(filter)
                    }
                _isRefreshing.value = false
            }
        }

        fun setFilterState(state: UploadState?) {
            _filterState.value = state
            reload()
        }

        fun toggleSelect(id: Long) {
            val current = _selectedIds.value
            _selectedIds.value =
                if (current.contains(id)) {
                    current - id
                } else {
                    current + id
                }
        }

        fun selectAll() {
            _selectedIds.value = _rows.value.map { it.id }.toSet()
        }

        fun clearSelection() {
            _selectedIds.value = emptySet()
        }

        fun updateState(state: UploadState) {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return

            viewModelScope.launch {
                omoideMemoryDao.update(ids, state)
                reload()
                clearSelection()
            }
        }

        fun deleteSelected() {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return

            viewModelScope.launch {
                omoideMemoryDao.delete(ids)
                reload()
                clearSelection()
            }
        }
    }
