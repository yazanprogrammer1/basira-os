package com.basira.os.core.common.coroutines

import javax.inject.Qualifier

/** App-lifetime CoroutineScope (SupervisorJob + Default dispatcher) for state that outlives screens. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
