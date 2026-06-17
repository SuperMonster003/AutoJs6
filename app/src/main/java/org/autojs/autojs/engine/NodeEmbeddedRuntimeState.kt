package org.autojs.autojs.engine

enum class NodeEmbeddedRuntimeState {
    CREATED,
    STARTING,
    RUNNING,
    DRAINING,
    STOPPING,
    STOPPED,
    FAILED,
    DESTROYED,
    TIMED_OUT,
    CRASHED,
}
