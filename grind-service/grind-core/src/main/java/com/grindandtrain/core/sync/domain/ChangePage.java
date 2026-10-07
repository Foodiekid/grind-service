package com.grindandtrain.core.sync.domain;

import java.util.List;

/**
 * One page of changes for sync-down, oldest first. {@code nextCursor} continues after the last change.
 *
 * @author Dheeraj_Edupuganti
 */
public record ChangePage(List<RecordChange> changes, String nextCursor, boolean hasMore) {
}
