package org.kie.trustyai.service.data.storage;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.Logger;
import org.kie.trustyai.service.config.ServiceConfig;
import org.kie.trustyai.service.config.storage.StorageConfig;
import org.kie.trustyai.service.data.exceptions.StorageReadException;
import org.kie.trustyai.service.data.exceptions.StorageWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static org.kie.trustyai.service.data.DataSource.INTERNAL_DATA_FILENAME;

@LookupIfProperty(name = "service.storage.format", stringValue = "MEMORY")
@ApplicationScoped
public class MemoryStorage extends Storage {

    private static final Logger LOG = Logger.getLogger(MemoryStorage.class);

    protected final Map<String, String> data = new ConcurrentHashMap<>();
    private final String dataFilename;
    private final int batchSize;

    public MemoryStorage(ServiceConfig serviceConfig, StorageConfig config) {
        this.dataFilename = config.dataFilename();
        this.batchSize = serviceConfig.batchSize().getAsInt();
    }

    @Override
    public ByteBuffer readData(final String modelId) throws StorageReadException {
        LOG.debug("Cache miss. Reading data for " + modelId);
        return readData(modelId, this.batchSize);
    }

    @Override
    public ByteBuffer readData(String modelId, int batchSize) throws StorageReadException {
        LOG.debug("Cache miss. Reading data for " + modelId);
        final String key = getDataFilename(modelId);
        if (data.containsKey(key)) {
            final String[] lines = data.get(key).split("\n");
            final int size = lines.length;
            if (size <= batchSize) {
                return ByteBuffer.wrap(data.get(key).getBytes());
            } else {
                final String lastLines = String.join("\n", Arrays.asList(lines).subList(size - batchSize, size));
                return ByteBuffer.wrap(lastLines.getBytes());
            }
        } else {
            throw new StorageReadException("Data file '" + key + "' not found");
        }
    }

    @Override
    public Pair<ByteBuffer, ByteBuffer> readDataWithTags(String modelId, Set<String> tags) throws StorageReadException {
        return readDataWithTags(modelId, this.batchSize, tags);
    }

    @Override
    public Pair<ByteBuffer, ByteBuffer> readDataWithTags(String modelId, int batchSize, Set<String> tags) throws StorageReadException {
        final List<String> dataLines = new ArrayList<>();
        final List<String> metadataLines = new ArrayList<>();

        final String dataKey = getDataFilename(modelId);
        final String metadataKey = getInternalDataFilename(modelId);

        if (data.containsKey(dataKey) && data.containsKey(metadataKey)) {
            String dataContent = data.get(dataKey);
            String metadataContent = data.get(metadataKey);
            String[] dataContentLines = dataContent.split("\n");

            try (CSVParser parser = CSVParser.parse(metadataContent, CSVFormat.DEFAULT.withTrim())) {
                int index = 0;
                for (CSVRecord record : parser) {
                    if (index >= dataContentLines.length) {
                        // Ensuring we do not go out of bounds if metadata lines are more than data lines
                        break;
                    }
                    String metadataLine = record.get(0); // Assuming the tag is in the first column
                    if (tags.contains(metadataLine)) {
                        metadataLines.add(String.join(",", record));
                        dataLines.add(dataContentLines[index]);
                    }
                    index++;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Apply batch size limit
            if (dataLines.size() > batchSize) {
                final String dataLinesString = String.join("\n", dataLines.subList(dataLines.size() - batchSize, dataLines.size()));
                final String metadataLinesString = String.join("\n", metadataLines.subList(metadataLines.size() - batchSize, metadataLines.size()));
                return Pair.of(ByteBuffer.wrap(dataLinesString.getBytes()), ByteBuffer.wrap(metadataLinesString.getBytes()));
            } else {
                final String dataLinesString = String.join("\n", dataLines);
                final String metadataLinesString = String.join("\n", metadataLines);

                return Pair.of(ByteBuffer.wrap(dataLinesString.getBytes()), ByteBuffer.wrap(metadataLinesString.getBytes()));
            }
        } else {
            throw new StorageReadException("Data or Metadata file not found for modelId: " + modelId);
        }
    }

    @Override
    public boolean dataExists(String modelId) throws StorageReadException {
        return data.containsKey(getDataFilename(modelId));
    }

    @Override
    public void save(ByteBuffer data, String location) throws StorageWriteException {
        final String stringData = new String(data.array(), StandardCharsets.UTF_8);
        LOG.debug("Saving data to " + location);
        this.data.put(location, stringData);
    }

    @Override
    public void append(ByteBuffer data, String location) throws StorageWriteException {
        final String value = this.data.computeIfPresent(location, (key, existing) -> existing + new String(data.array(), StandardCharsets.UTF_8));
        LOG.debug("Appending data to " + location);
        if (value == null) {
            throw new StorageWriteException("Destination does not exist: " + location);
        }
    }

    @Override
    public void appendData(ByteBuffer data, String modelId) throws StorageWriteException {
        append(data, getDataFilename(modelId));
    }

    @Override
    public ByteBuffer read(String location) throws StorageReadException {
        if (data.containsKey(location)) {
            return ByteBuffer.wrap(data.get(location).getBytes());
        } else {
            throw new StorageReadException("File not found: " + location);
        }

    }

    /**
     * Read {@link ByteBuffer} from the memory storage, for a given filename and batch size.
     *
     * @param location  The filename to read
     * @param batchSize The batch size
     * @return A {@link ByteBuffer} containing the data
     * @throws StorageReadException If an error occurs while reading the data
     */
    @Override
    public ByteBuffer read(String location, int batchSize) throws StorageReadException {
        if (data.containsKey(location)) {
            final String content = data.get(location);
            final String[] lines = content.split("\n");

            final int start = Math.max(0, lines.length - batchSize);

            final StringBuilder lastLines = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                lastLines.append(lines[i]);
                if (i < lines.length - 1) {
                    lastLines.append("\n");
                }
            }
            return ByteBuffer.wrap(lastLines.toString().getBytes());
        } else {
            throw new StorageReadException("File not found: " + location);
        }
    }

    @Override
    public void saveData(ByteBuffer data, String modelId) throws StorageWriteException {
        save(data, getDataFilename(modelId));
    }

    @Override
    public boolean fileExists(String location) throws StorageReadException {
        return data.containsKey(location);
    }

    @Override
    public String getDataFilename(String modelId) {
        return modelId + "-" + this.dataFilename;
    }

    @Override
    public String getInternalDataFilename(String modelId) {
        return modelId + "-" + INTERNAL_DATA_FILENAME;
    }

    @Override
    public Path buildDataPath(String modelId) {
        return Path.of(getDataFilename(modelId));
    }

    @Override
    public Path buildInternalDataPath(String modelId) {
        return Path.of(getInternalDataFilename(modelId));
    }

    @Override
    public long getLastModified(final String modelId) {
        final Checksum crc32 = new CRC32();
        crc32.update(readData(modelId));
        return crc32.getValue();
    }
}
