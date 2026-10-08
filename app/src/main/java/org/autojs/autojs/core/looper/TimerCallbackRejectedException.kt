package org.autojs.autojs.core.looper

/** Handler rejected a callback because its Looper is quitting; other timer failures remain distinct. */
class TimerCallbackRejectedException : IllegalStateException(
    "Unable to schedule a timer callback because its looper is exiting",
)
