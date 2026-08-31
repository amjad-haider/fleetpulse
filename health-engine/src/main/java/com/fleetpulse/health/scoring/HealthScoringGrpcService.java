package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import com.fleetpulse.proto.health.v1.HealthScoringGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class HealthScoringGrpcService extends HealthScoringGrpc.HealthScoringImplBase {

    private final RiskScorer riskScorer;

    public HealthScoringGrpcService(RiskScorer riskScorer) {
        this.riskScorer = riskScorer;
    }

    @Override
    public void scoreVehicle(HealthScoreRequest request, StreamObserver<HealthScoreResponse> responseObserver) {
        RiskScorer.ScoreResult result = riskScorer.score(new RiskScorer.RiskInput(
                request.getEngineTempC(),
                request.getVibrationMmS(),
                request.getBrakeWearPct(),
                request.getFaultCodeCount()
        ));

        HealthScoreResponse response = HealthScoreResponse.newBuilder()
                .setVehicleId(request.getVehicleId())
                .setRiskScore(result.riskScore())
                .setDecision(result.decision())
                .setUsedFallback(true) // no trained model wired up yet, this is always the rule engine for now
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
