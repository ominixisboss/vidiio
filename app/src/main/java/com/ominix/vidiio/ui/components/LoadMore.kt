package com.ominix.vidiio.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Calls [onLoadMore] when a row scrolls (or a remote's focus moves) to within [threshold] items of
 * its end, so the row keeps growing like a streaming app's. Calls again each time new items arrive
 * and the end is still close; the caller ignores repeats while a page is loading.
 */
@Composable
fun LoadMoreOnScroll(state: LazyListState, itemCount: Int, onLoadMore: (() -> Unit)?, threshold: Int = 6) {
    if (onLoadMore == null) return
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - threshold
        }
    }
    LaunchedEffect(nearEnd, itemCount) {
        if (nearEnd && itemCount > 0) onLoadMore()
    }
}

/** [LoadMoreOnScroll] for grids, which grow downwards. */
@Composable
fun LoadMoreOnScroll(state: LazyGridState, itemCount: Int, onLoadMore: (() -> Unit)?, threshold: Int = 12) {
    if (onLoadMore == null) return
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - threshold
        }
    }
    LaunchedEffect(nearEnd, itemCount) {
        if (nearEnd && itemCount > 0) onLoadMore()
    }
}
