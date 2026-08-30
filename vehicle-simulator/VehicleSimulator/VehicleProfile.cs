namespace VehicleSimulator;

/// <summary>
/// Baseline characteristics a simulated vehicle starts from. TelemetryGenerator
/// drifts away from these over time rather than sampling around them forever.
/// </summary>
public sealed record VehicleProfile(
    string VehicleId,
    double BaselineEngineTempC,
    double BaselineVibrationMmS,
    int BaselineRpm,
    double StartingFuelLevelPct,
    double StartingBrakeWearPct,
    double StartingOdometerKm
)
{
    public static VehicleProfile NewVehicle(string vehicleId) => new(
        VehicleId: vehicleId,
        BaselineEngineTempC: 88,
        BaselineVibrationMmS: 1.8,
        BaselineRpm: 1400,
        StartingFuelLevelPct: 90,
        StartingBrakeWearPct: 5,
        StartingOdometerKm: 500
    );

    public static VehicleProfile AgingVehicle(string vehicleId) => new(
        VehicleId: vehicleId,
        BaselineEngineTempC: 94,
        BaselineVibrationMmS: 2.6,
        BaselineRpm: 1450,
        StartingFuelLevelPct: 70,
        StartingBrakeWearPct: 42,
        StartingOdometerKm: 180_000
    );
}
