package com.fleetpulse.telemetry.ingest;

import com.fleetpulse.proto.telemetry.v1.TelemetryAck;
import com.fleetpulse.proto.telemetry.v1.TelemetryIngestionGrpc;
import com.fleetpulse.proto.telemetry.v1.TelemetryRecord;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@GrpcService
public class TelemetryIngestionGrpcService extends TelemetryIngestionGrpc.TelemetryIngestionImplBase {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionGrpcService.class);
    private static final int ACK_EVERY_N_RECORDS = 5;

    private final TelemetryReadingRepository readingRepository;
    private final TelemetryEventPublisher eventPublisher;

    public TelemetryIngestionGrpcService(TelemetryReadingRepository readingRepository, TelemetryEventPublisher eventPublisher) {
        this.readingRepository = readingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public StreamObserver<TelemetryRecord> streamTelemetry(StreamObserver<TelemetryAck> responseObserver) {
        return new StreamObserver<>() {

            private final AtomicInteger recordsReceived = new AtomicInteger(0);
            private volatile String vehicleId = "unknown";

            @Override
            public void onNext(TelemetryRecord record) {
                vehicleId = record.getVehicleId();
                try {
                    TelemetryReading reading = toReading(record);
                    readingRepository.save(reading);
                    eventPublisher.publish(reading);

                    int count = recordsReceived.incrementAndGet();
                    if (count % ACK_EVERY_N_RECORDS == 0) {
                        responseObserver.onNext(ackFor(count));
                    }
                } catch (Exception ex) {
                    // one bad record shouldn't kill the stream for the rest of this vehicle's session
                    log.error("failed to process telemetry record from {}", vehicleId, ex);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("stream from {} closed with error: {}", vehicleId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(ackFor(recordsReceived.get()));
                responseObserver.onCompleted();
            }

            private TelemetryAck ackFor(int count) {
                return TelemetryAck.newBuilder()
                        .setVehicleId(vehicleId)
                        .setRecordsReceived(count)
                        .build();
            }
        };
    }

    private TelemetryReading toReading(TelemetryRecord record) {
        return TelemetryReading.builder()
                .vehicleId(record.getVehicleId())
                .recordedAt(toInstant(record.getRecordedAt()))
                .engineTempC(record.getEngineTempC())
                .vibrationMmS(record.getVibrationMmS())
                .rpm(record.getRpm())
                .fuelLevelPct(record.getFuelLevelPct())
                .brakeWearPct(record.getBrakeWearPct())
                .odometerKm(record.getOdometerKm())
                .faultCodes(new ArrayList<>(record.getFaultCodesList()))
                .build();
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
