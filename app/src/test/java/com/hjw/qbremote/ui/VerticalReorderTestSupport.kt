package com.hjw.qbremote.ui

// Test fixtures for VerticalReorderDragState (production code builds it via the controller).

internal fun <T> createVerticalReorderDragState(
    session: VerticalReorderSession<T>,
    item: T,
): VerticalReorderDragState<T> {
    require(session.items.getOrNull(session.startIndex) == item) {
        "Dragged item must match the session start index item."
    }
    return VerticalReorderDragState(
        item = item,
        session = session,
        offsetY = 0f,
        targetIndex = session.startIndex,
    )
}

internal fun <T> applyVerticalReorderDragDelta(
    state: VerticalReorderDragState<T>,
    deltaY: Float,
): VerticalReorderDragState<T> {
    val nextOffsetY = (state.offsetY + deltaY)
        .coerceIn(state.session.minOffset, state.session.maxOffset)
    val nextTargetIndex = resolveVerticalReorderTargetIndexWithHysteresis(
        session = state.session,
        dragOffsetY = nextOffsetY,
        previousTargetIndex = state.targetIndex,
    )
    return state.copy(
        offsetY = nextOffsetY,
        targetIndex = nextTargetIndex,
    )
}
