package org.kie.trustyai.service.data.storage;

import java.nio.file.Path;

public abstract class Storage implements StorageInterface {

    public abstract String getDataFilename(String modelId);

    /**
     * Get the internal data filename for a given model ID.
     *
     * @param modelId The model ID
     * @return The internal data filename
     */
    public abstract String getInternalDataFilename(String modelId);

    public abstract Path buildDataPath(String modelId);

    /**
     * Build the internal data path for a given model ID.
     *
     * @param modelId The model ID
     * @return The internal data path
     */
    public abstract Path buildInternalDataPath(String modelId);

}
