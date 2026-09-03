package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;
import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthScoringGrpcServiceTest {

    @Mock
    private RiskScoringOrchestrator orchestrator;

    @Mock
    private StreamObserver<HealthScoreResponse> responseObserver;

    private HealthScoringGrpcService service;

    @BeforeEach
    void setUp() {
        service = new HealthScoringGrpcService(orchestrator);
    }

    @Test
    void delegatesScoringToTheOrchestratorAndCompletesTheCall() {
        HealthScoreRequest request = HealthScoreRequest.newBuilder().setVehicleId("FLEET-001").build();
        HealthScoreResponse expectedResponse = HealthScoreResponse.newBuilder()
                .setVehicleId("FLEET-001")
                .setRiskScore(0.85)
                .setDecision(HealthDecision.SERVICE_NOW)
                .setUsedFallback(false)
                .build();
        when(orchestrator.score(request)).thenReturn(expectedResponse);

        service.scoreVehicle(request, responseObserver);

        verify(responseObserver).onNext(expectedResponse);
        verify(responseObserver).onCompleted();
    }

    @Test
    void doesNotScoreAnythingItself() {
        // this test exists to document intent: HealthScoringGrpcService should
        // stay a thin adapter, all the actual scoring/fallback logic belongs
        // in RiskScoringOrchestrator, not duplicated here
        HealthScoreRequest request = HealthScoreRequest.newBuilder().build();
        when(orchestrator.score(any())).thenReturn(HealthScoreResponse.getDefaultInstance());

        service.scoreVehicle(request, responseObserver);

        verify(orchestrator).score(request);
    }
}
