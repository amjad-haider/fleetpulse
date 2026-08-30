using Fleetpulse.Telemetry.V1;
using Grpc.Core;

namespace VehicleSimulator;

/// <summary>
/// Drives one vehicle's gRPC bidi stream: writes a telemetry record on an
/// interval and logs whatever acks come back, until cancelled.
/// </summary>
public sealed class SimulatedVehicle
{
    private readonly TelemetryIngestion.TelemetryIngestionClient _client;
    private readonly TelemetryGenerator _generator;
    private readonly string _vehicleId;
    private readonly TimeSpan _interval;

    public SimulatedVehicle(
        TelemetryIngestion.TelemetryIngestionClient client,
        TelemetryGenerator generator,
        string vehicleId,
        TimeSpan interval)
    {
        _client = client;
        _generator = generator;
        _vehicleId = vehicleId;
        _interval = interval;
    }

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        using var call = _client.StreamTelemetry(cancellationToken: cancellationToken);

        var readAcksTask = ReadAcksAsync(call.ResponseStream, cancellationToken);

        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var record = _generator.NextRecord(DateTime.UtcNow);
                await call.RequestStream.WriteAsync(record);
                await Task.Delay(_interval, cancellationToken);
            }
        }
        finally
        {
            await call.RequestStream.CompleteAsync();
            await readAcksTask;
        }
    }

    private async Task ReadAcksAsync(IAsyncStreamReader<TelemetryAck> responseStream, CancellationToken cancellationToken)
    {
        await foreach (var ack in responseStream.ReadAllAsync(cancellationToken))
        {
            Console.WriteLine($"[{_vehicleId}] ack: telemetry-service has received {ack.RecordsReceived} records");
        }
    }
}
