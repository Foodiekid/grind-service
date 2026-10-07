package com.grindandtrain.core.sync.domain;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * A short-lived link to download one record's encrypted blob straight from storage.
 *
 * @author Dheeraj_Edupuganti
 */
public record DownloadLink(URI url, OffsetDateTime expiresAt) {
}
