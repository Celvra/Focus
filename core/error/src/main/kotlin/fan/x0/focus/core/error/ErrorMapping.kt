package fan.x0.focus.core.error

// Presentation mapping for failures that reach a ViewModel from any layer.
fun Throwable.toAppError(): AppError = when (this) {
    is NetworkException -> this.toAppError()
    is StorageException -> AppError(AppErrorType.Storage)
    else -> AppError(AppErrorType.Generic, serverMessage = message ?: javaClass.simpleName)
}
