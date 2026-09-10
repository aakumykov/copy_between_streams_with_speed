package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

abstract class BasicStreamCopier: StreamCopier {

    override val progressFlow: SharedFlow<Long> get() = _progressFlow

    private val _progressFlow: MutableSharedFlow<Long> = MutableSharedFlow()

    protected suspend fun publishProgress(value: Long) {
        _progressFlow.emit(value)
    }
}