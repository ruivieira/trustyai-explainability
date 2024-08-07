package org.kie.trustyai.service.endpoints.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.kie.trustyai.service.mocks.MockPrometheusScheduler;
import org.kie.trustyai.service.mocks.flatfile.MockCSVDatasource;
import org.kie.trustyai.service.mocks.flatfile.MockMemoryStorage;

import io.quarkus.test.junit.QuarkusTestProfile;

import static org.kie.trustyai.service.data.storage.DataFormat.CSV;
import static org.kie.trustyai.service.data.storage.StorageFormat.MEMORY;

public class MetricsEndpointTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        final Map<String, String> overrides = new HashMap<>();
        overrides.put("service.storage-format", String.valueOf(MEMORY));
        overrides.put("service.data-format", String.valueOf(CSV));
        overrides.put("service.metrics-schedule", "5s");
        overrides.put("storage.data-filename", "data.csv");
        overrides.put("storage.data-folder", "/inputs");
        overrides.put("service.batch-size", "5000");
        overrides.put("quarkus.http.ssl.certificate.files", "");
        overrides.put("quarkus.http.ssl.certificate.key-files", "");
        return overrides;
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockCSVDatasource.class, MockMemoryStorage.class, MockPrometheusScheduler.class);

    }

}
