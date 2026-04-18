package cock.crest.purrfectsnap.lite.ui.manager

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
fun rememberRouteScrollState(key: String): ScrollState {
    val scrollState = remember(key) {
        ScrollState(RouteStateCache.scrollOffsets[key] ?: 0)
    }
    LaunchedEffect(key, scrollState.value) {
        RouteStateCache.scrollOffsets[key] = scrollState.value
    }
    DisposableEffect(key, scrollState) {
        onDispose {
            RouteStateCache.scrollOffsets[key] = scrollState.value
        }
    }
    return scrollState
}

@Composable
fun rememberRouteLazyListState(key: String): LazyListState {
    val savedState = RouteStateCache.lazyListOffsets[key]
    val listState = remember(key) {
        LazyListState(
            firstVisibleItemIndex = savedState?.first ?: 0,
            firstVisibleItemScrollOffset = savedState?.second ?: 0
        )
    }
    LaunchedEffect(key, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        RouteStateCache.lazyListOffsets[key] =
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    DisposableEffect(key, listState) {
        onDispose {
            RouteStateCache.lazyListOffsets[key] =
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
    }
    return listState
}

private object RouteStateCache {
    val scrollOffsets = mutableMapOf<String, Int>()
    val lazyListOffsets = mutableMapOf<String, Pair<Int, Int>>()
}
