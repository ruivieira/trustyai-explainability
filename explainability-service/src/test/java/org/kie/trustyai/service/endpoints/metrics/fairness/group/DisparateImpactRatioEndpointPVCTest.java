package org.kie.trustyai.service.endpoints.metrics.fairness.group;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.kie.trustyai.service.mocks.MockPVCStorage;
import org.kie.trustyai.service.profiles.PVCTestProfile;

import java.io.IOException;

@QuarkusTest
@TestProfile(PVCTestProfile.class)
@TestHTTPEndpoint(DisparateImpactRatioEndpoint.class)
class DisparateImpactRatioEndpointPVCTest extends DisparateImpactRatioEndpointBaseTest {
    @Inject
    Instance<MockPVCStorage> storage;

    @BeforeEach
    void reset() throws IOException {

        storage.get().emptyStorage("/tmp/" + MODEL_ID + "-data.csv");
        storage.get().emptyStorage("/tmp/" + MODEL_ID + "-internal_data.csv");
        storage.get().emptyStorage("/tmp/" + MODEL_ID + "-metadata.json");
    }
}
