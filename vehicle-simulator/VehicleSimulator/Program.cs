using Fleetpulse.Telemetry.V1;
using Grpc.Core;
using Grpc.Net.Client;
using VehicleSimulator;

string serverAddress = Environment.GetEnvironmentVariable("TELEMETRY_SERVICE_ADDRESS") ?? "http://localhost:8082";
int vehicleCount = int.TryParse(Environment.GetEnvironmentVariable("VEHICLE_COUNT"), out int vc) ? vc : 5;
double intervalSeconds = double.TryParse(Environment.GetEnvironmentVariable("TELEMETRY_INTERVAL_SECONDS"), out double iv) ? iv : 2.0;

Console.WriteLine($"vehicle-simulator: {vehicleCount} vehicles -> {serverAddress}, every {intervalSeconds}s");

using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) =>
{
    e.Cancel = true;
    cts.Cancel();
};

using var channel = GrpcChannel.ForAddress(serverAddress);
var client = new TelemetryIngestion.TelemetryIngestionClient(channel);

var profiles = BuildFleet(vehicleCount);
var interval = TimeSpan.FromSeconds(intervalSeconds);

var tasks = profiles.Select(profile => RunVehicleWithRetryAsync(client, profile, interval, cts.Token));
await Task.WhenAll(tasks);

return;

static List<VehicleProfile> BuildFleet(int count)
{
    var profiles = new List<VehicleProfile>(count);
    for (int i = 1; i <= count; i++)
    {
        string id = $"FLEET-{i:D3}";
        // every third vehicle in the simulated fleet is older and closer to needing maintenance
        profiles.Add(i % 3 == 0 ? VehicleProfile.AgingVehicle(id) : VehicleProfile.NewVehicle(id));
    }
    return profiles;
}

static async Task RunVehicleWithRetryAsync(
    TelemetryIngestion.TelemetryIngestionClient client,
    VehicleProfile profile,
    TimeSpan interval,
    CancellationToken cancellationToken)
{
    var generator = new TelemetryGenerator(profile, new Random(profile.VehicleId.GetHashCode()));
    var vehicle = new SimulatedVehicle(client, generator, profile.VehicleId, interval);
    var backoff = TimeSpan.FromSeconds(2);

    while (!cancellationToken.IsCancellationRequested)
    {
        try
        {
            Console.WriteLine($"[{profile.VehicleId}] connecting...");
            await vehicle.RunAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            break;
        }
        catch (RpcException ex)
        {
            Console.WriteLine($"[{profile.VehicleId}] stream failed ({ex.StatusCode}: {ex.Status.Detail}), retrying in {backoff.TotalSeconds:F0}s");
            try
            {
                await Task.Delay(backoff, cancellationToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            backoff = TimeSpan.FromSeconds(Math.Min(backoff.TotalSeconds * 2, 30));
        }
    }
}
