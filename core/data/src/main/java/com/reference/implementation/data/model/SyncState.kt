package com.reference.implementation.data.model

/**
 * Represents the rigid, isolated operational states of background cloud synchronization tasks.
 * This is an internal data-layer state tracker used to isolate network disruptions
 * from Room's active local cache collection flows.
 */
sealed interface SyncState {

    /**
     * The synchronization engine is completely idle or has successfully finished its task.
     * No background network transactions are currently active.
     */
    object Idle: SyncState

    /**
     * An on-demand cloud synchronization check is currently in flight.
     * The repository is actively pinging the network layer or processing retryIO backoffs.
     */
    object Loading: SyncState

    /**
     * A background network check encountered a structural validation error, timeout, or failure.
     *
     * @property exception The raw root cause of the sync failure
     *                     (e.g., [retrofit2.HttpException] or [java.io.IOException]).
     */
    data class Error(val exception:Throwable): SyncState

}