package com.fleetpulse.telemetry.ingest;

import com.fleetpulse.proto.telemetry.v1.TelemetryAck;
import com.fleetpulse.proto.telemetry.v1.TelemetryRecord;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionGrpcServiceTest {

    @Mock
    private TelemetryReadingRepository readingRepository;

    @Mock
    private TelemetryEventPublisher eventPublisher;

    @Mock
    private StreamObserver<TelemetryAck> responseObserver;

    private TelemetryIngestionGrpcService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryIngestionGrpcService(readingRepository, eventPublisher);
    }

    private static TelemetryRecord record(String vehicleId) {
        Instant now = Instant.now();
        return TelemetryRecord.newBuilder()
                .setVehicleId(vehicleId)
                .setRecordedAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build())
                .setEngineTempC(90.5)
                .setVibrationMmS(2.1)
                .setRpm(1500)
                .setFuelLevelPct(80)
                .setBrakeWearPct(10)
                .setOdometerKm(1000)
                .build();
    }

    @Test
    void everyRecordIsPersistedAndPublished() {
        StreamObserver<TelemetryRecord> requestObserver = service.streamTelemetry(responseObserver);

        requestObserver.onNext(record("FLEET-001"));
        requestObserver.onNext(record("FLEET-001"));

        verify(readingRepository, times(2)).save(any(TelemetryReading.class));
        verify(eventPublisher, times(2)).publish(any(TelemetryReading.class));
    }

    @Test
    void savedReadingMatchesTheIncomingRecord() {
        StreamObserver<TelemetryRecord> requestObserver = service.streamTelemetry(responseObserver);

        requestObserver.onNext(record("FLEET-002"));

        ArgumentCaptor<TelemetryReading> captor = ArgumentCaptor.forClass(TelemetryReading.class);
        verify(readingRepository).save(captor.capture());
        assertThat(captor.getValue().getVehicleId()).isEqualTo("FLEET-002");
        assertThat(captor.getValue().getEngineTempC()).isEqualTo(90.5);
    }

    @Test
    void doesNotAckBeforeTheFifthRecord() {
        StreamObserver<TelemetryRecord> requestObserver = service.streamTelemetry(responseObserver);

        for (int i = 0; i < 4; i++) {
            requestObserver.onNext(record("FLEET-003"));
        }

        verify(responseObserver, never()).onNext(any());
    }

    @Test
    void acksOnEveryFifthRecord() {
        StreamObserver<TelemetryRecord> requestObserver = service.streamTelemetry(responseObserver);

        for (int i = 0; i < 5; i++) {
            requestObserver.onNext(record("FLEET-004"));
        }

        ArgumentCaptor<TelemetryAck> captor = ArgumentCaptor.forClass(TelemetryAck.class);
        verify(responseObserver).onNext(captor.capture());
        assertThat(captor.getValue().getRecordsReceived()).isEqualTo(5);
        assertThat(captor.getValue().getVehicleId()).isEqualTo("FLEET-004");
    }

    @Test
    void completingTheStreamSendsAFinalAckAndCompletes() {
        StreamObserver<TelemetryRecord> requestObserver = service.streamTelemetry(responseObserver);

        requestObserver.onNext(record("FLEET-005"));
        requestObserver.onNext(record("FLEET-005"));
        requestObserver.onCompleted();

        ArgumentCaptor<TelemetryAck> captor = ArgumentCaptor.forClass(TelemetryAck.class);
        verify(responseObserver).onNext(captor.capture());
        assertThat(captor.getValue().getRecordsReceived()).isEqualTo(2);
        verify(responseObserver).onCompleted();
    }

    @Test
    void aFailureSavingOneRecordDoesNotBreakTheStream() {
        org.mockito.Mockito.doThrow(new RuntimeException("db is down"))
                .when(readingRepository).save(any(TelemetryReading.class));

        StreamObserver<TelemetryRecord> requestObserver = service.streamTelemetry(responseObserver);

        requestObserver.onNext(record("FLEET-006"));

        verify(responseObserver, never()).onError(any());
    }
}
