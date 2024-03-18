package org.kie.trustyai.service.endpoints.explainers.local;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.explainability.model.*;
import org.kie.trustyai.explainability.utils.models.TestModels;
import org.kie.trustyai.service.data.metadata.Metadata;
import org.kie.trustyai.service.endpoints.explainers.ExplainersEndpointTestProfile;
import org.kie.trustyai.service.endpoints.explainers.GrpcMockServer;
import org.kie.trustyai.service.mocks.MockDatasource;
import org.kie.trustyai.service.mocks.MockMemoryStorage;
import org.kie.trustyai.service.payloads.explainers.LocalExplanationRequest;
import org.kie.trustyai.service.payloads.explainers.ModelConfig;
import org.kie.trustyai.service.payloads.explainers.SaliencyExplanationResponse;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ExplainersEndpointTestProfile.class)
@TestHTTPEndpoint(LimeEndpoint.class)
class LimeEndpointTest {

    private static final String MODEL_ID = "example1";
    private static final int N_SAMPLES = 100;
    @Inject
    Instance<MockDatasource> datasource;
    @Inject
    Instance<MockMemoryStorage> storage;
    private GrpcMockServer mockServer;

    @BeforeEach
    void populateStorage() throws IOException {
        storage.get().emptyStorage();
        final Dataframe dataframe = datasource.get().generateRandomDataframe(N_SAMPLES);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);
        mockServer = new GrpcMockServer(TestModels.getSumSkipModel(1));
        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        storage.get().emptyStorage();
        mockServer.stop();
    }

    @Test
    void get() {
        when().get()
                .then()
                .statusCode(Response.Status.METHOD_NOT_ALLOWED.getStatusCode())
                .body(is(""));
    }

    @Test
    void postWithoutKserve() throws JsonProcessingException {
        datasource.get().reset();
        Dataframe dataframe = datasource.get().getDataframe(MODEL_ID);
        List<PredictionInput> predictionInputs = dataframe.asPredictionInputs();
        String id = String.valueOf(predictionInputs.get(0).hashCode());
        final LocalExplanationRequest payload = new LocalExplanationRequest();
        payload.setModelConfig(new ModelConfig("", MODEL_ID, ""));
        payload.setPredictionId(id);

        given().contentType(ContentType.JSON).body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    @DisplayName("Test LIME request with default input tensor name")
    void testInputTensorName() throws JsonProcessingException {
        datasource.get().reset();
        final Dataframe dataframe = datasource.get().getDataframe(MODEL_ID);
        final Random random = new Random();
        int randomIndex = random.nextInt(dataframe.getIds().size());
        final String id = dataframe.getIds().get(randomIndex);
        final LocalExplanationRequest payload = new LocalExplanationRequest();
        payload.setModelConfig(new ModelConfig("localhost:" + mockServer.getPort(), MODEL_ID, ""));
        payload.setPredictionId(id);

        final SaliencyExplanationResponse response = given().contentType(ContentType.JSON).body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().body().as(SaliencyExplanationResponse.class);

        assertEquals(3, response.getSaliencies().get("income").size());

        final Metadata metadata = datasource.get().getMetadata(MODEL_ID);
        final Set<String> inputNames = Set.of("gender", "race", "age");
        final Set<String> outputNames = Set.of("income");
        assertEquals(inputNames, metadata.getInputSchema().getItems().keySet().stream().collect(Collectors.toUnmodifiableSet()));
        assertEquals(outputNames, metadata.getOutputSchema().getItems().keySet().stream().collect(Collectors.toUnmodifiableSet()));

        final Dataframe storedDataframe = datasource.get().getDataframe(MODEL_ID);

        assertEquals(DataframeMetadata.DEFAULT_INPUT_TENSOR_NAME, metadata.getInputTensorName());
        assertEquals(DataframeMetadata.DEFAULT_OUTPUT_TENSOR_NAME, metadata.getOutputTensorName());
        assertEquals(storedDataframe.getInputTensorName(), metadata.getInputTensorName());
        assertEquals(storedDataframe.getOutputTensorName(), metadata.getOutputTensorName());
        assertEquals(inputNames, new HashSet<>(storedDataframe.getInputNames()));
        assertEquals(outputNames, new HashSet<>(storedDataframe.getOutputNames()));
    }

    @Test
    @DisplayName("Test LIME request with custom input tensor name")
    void testInputCustomTensorName() throws JsonProcessingException {
        datasource.get().reset();
        storage.get().emptyStorage();
        final Dataframe _dataframe = datasource.get().generateRandomDataframe(N_SAMPLES);
        final Metadata _metadata = datasource.get().createMetadata(_dataframe);
        final String INPUT_NAME = "custom-input-a";
        final String OUTPUT_NAME = "custom-output-a";
        _metadata.setInputTensorName(INPUT_NAME);
        _metadata.setOutputTensorName(OUTPUT_NAME);
        datasource.get().saveDataframe(_dataframe, MODEL_ID);
        datasource.get().saveMetadata(_metadata, MODEL_ID);

        final Dataframe dataframe = datasource.get().getDataframe(MODEL_ID);
        final Random random = new Random();
        int randomIndex = random.nextInt(dataframe.getIds().size());
        final String id = dataframe.getIds().get(randomIndex);
        final LocalExplanationRequest payload = new LocalExplanationRequest();
        payload.setModelConfig(new ModelConfig("localhost:" + mockServer.getPort(), MODEL_ID, ""));
        payload.setPredictionId(id);

        final SaliencyExplanationResponse response = given().contentType(ContentType.JSON).body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().body().as(SaliencyExplanationResponse.class);

        assertEquals(3, response.getSaliencies().get("income").size());

        final Metadata metadata = datasource.get().getMetadata(MODEL_ID);
        final Set<String> inputNames = Set.of("gender", "race", "age");
        final Set<String> outputNames = Set.of("income");
        assertEquals(inputNames, metadata.getInputSchema().getItems().keySet().stream().collect(Collectors.toUnmodifiableSet()));
        assertEquals(outputNames, metadata.getOutputSchema().getItems().keySet().stream().collect(Collectors.toUnmodifiableSet()));

        final Dataframe storedDataframe = datasource.get().getDataframe(MODEL_ID);

        assertEquals(INPUT_NAME, metadata.getInputTensorName());
        assertEquals(OUTPUT_NAME, metadata.getOutputTensorName());
        assertEquals(INPUT_NAME, storedDataframe.getInputTensorName());
        assertEquals(OUTPUT_NAME, storedDataframe.getOutputTensorName());
        assertEquals(inputNames, new HashSet<>(storedDataframe.getInputNames()));
        assertEquals(outputNames, new HashSet<>(storedDataframe.getOutputNames()));
    }

    @Test
    @DisplayName("Test LIME request should not add observations to bias data")
    void testInputDataFilter() throws JsonProcessingException {
        datasource.get().reset();

        final Dataframe dataframe = datasource.get().getDataframe(MODEL_ID);

        final int initialSize = dataframe.getRowDimension();

        final Random random = new Random();
        int randomIndex = random.nextInt(dataframe.getIds().size());
        final String id = dataframe.getIds().get(randomIndex);
        final LocalExplanationRequest payload = new LocalExplanationRequest();
        payload.setModelConfig(new ModelConfig("localhost:" + mockServer.getPort(), MODEL_ID, ""));
        payload.setPredictionId(id);

        final SaliencyExplanationResponse response = given().contentType(ContentType.JSON).body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().body().as(SaliencyExplanationResponse.class);

        assertEquals(3, response.getSaliencies().get("income").size());

        final Dataframe storedDataframe = datasource.get().getDataframe(MODEL_ID);

        assertEquals(initialSize, storedDataframe.getRowDimension());

    }

}
