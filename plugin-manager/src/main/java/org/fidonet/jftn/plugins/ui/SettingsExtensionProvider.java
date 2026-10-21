package org.fidonet.jftn.plugins.ui;

import java.util.List;

/** Optional settings owned by an active service, independent of script registration. */
public interface SettingsExtensionProvider {
    List<SettingsExtension> getSettingsExtensions();
}
