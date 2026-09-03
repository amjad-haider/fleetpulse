package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import com.fleetpulse.proto.health.v1.HealthScoringGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class HealthScoringGrpcService extends HealthScoringGrpc.HealthScoringImplBase {

    private final RiskScoringOrchestrator orchestrator;

    public HealthScoringGrpcService(RiskScoringOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void scoreVehicle(HealthScoreRequest request, StreamObserver<HealthScoreResponse> responseObserver) {
        responseObserver.onNext(orchestrator.score(request));
        responseObserver.onCompleted();
    }
}
