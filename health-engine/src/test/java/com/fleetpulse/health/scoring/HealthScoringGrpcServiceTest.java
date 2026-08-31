package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;
import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthScoringGrpcServiceTest {

    @Mock
    private StreamObserver<HealthScoreResponse> responseObserver;

    private final HealthScoringGrpcService service = new HealthScoringGrpcService(new RiskScorer());

    @Test
    void scoresARequestAndCompletesTheCall() {
        HealthScoreRequest request = HealthScoreRequest.newBuilder()
                .setVehicleId("FLEET-001")
                .setEngineTempC(130)
                .setVibrationMmS(8)
                .setBrakeWearPct(100)
                .setFaultCodeCount(3)
                .build();

        service.scoreVehicle(request, responseObserver);

        ArgumentCaptor<HealthScoreResponse> captor = ArgumentCaptor.forClass(HealthScoreResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        HealthScoreResponse response = captor.getValue();
        assertThat(response.getVehicleId()).isEqualTo("FLEET-001");
        assertThat(response.getRiskScore()).isEqualTo(1.0);
        assertThat(response.getDecision()).isEqualTo(HealthDecision.SERVICE_NOW);
        assertThat(response.getUsedFallback()).isTrue();
    }
}
