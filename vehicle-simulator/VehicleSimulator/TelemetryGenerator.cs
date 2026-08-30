using Fleetpulse.Telemetry.V1;
using Google.Protobuf.WellKnownTypes;

namespace VehicleSimulator;

/// <summary>
/// Produces one TelemetryRecord per call, drifting a vehicle's readings around
/// its baseline and occasionally injecting a spike + fault code so the eventual
/// health-engine has something worth scoring.
/// </summary>
public sealed class TelemetryGenerator
{
    private const double AnomalyChance = 0.03;

    private static readonly string[] FaultCodes =
    [
        "P0128", // coolant thermostat
        "P0301", // cylinder misfire
        "P0442", // evap system leak
        "P0500", // vehicle speed sensor
        "P0562"  // system voltage low
    ];

    private readonly VehicleProfile _profile;
    private readonly Random _random;

    private double _odometerKm;
    private double _brakeWearPct;
    private double _fuelLevelPct;

    public TelemetryGenerator(VehicleProfile profile, Random random)
    {
        _profile = profile;
        _random = random;
        _odometerKm = profile.StartingOdometerKm;
        _brakeWearPct = profile.StartingBrakeWearPct;
        _fuelLevelPct = profile.StartingFuelLevelPct;
    }

    public TelemetryRecord NextRecord(DateTime utcNow)
    {
        bool anomaly = _random.NextDouble() < AnomalyChance;

        double engineTemp = _profile.BaselineEngineTempC + GaussianNoise(2.0) + (anomaly ? _random.NextDouble() * 25 : 0);
        double vibration = Math.Max(0, _profile.BaselineVibrationMmS + GaussianNoise(0.3) + (anomaly ? _random.NextDouble() * 3.5 : 0));
        int rpm = Math.Max(0, _profile.BaselineRpm + (int)GaussianNoise(150));

        _odometerKm += _random.NextDouble() * 2.2;
        _brakeWearPct = Math.Min(100, _brakeWearPct + _random.NextDouble() * 0.015);
        _fuelLevelPct = _fuelLevelPct - _random.NextDouble() * 0.4;
        if (_fuelLevelPct < 15)
        {
            _fuelLevelPct = 95; // refuel
        }

        var record = new TelemetryRecord
        {
            VehicleId = _profile.VehicleId,
            RecordedAt = Timestamp.FromDateTime(DateTime.SpecifyKind(utcNow, DateTimeKind.Utc)),
            EngineTempC = Math.Round(engineTemp, 1),
            VibrationMmS = Math.Round(vibration, 2),
            Rpm = rpm,
            FuelLevelPct = Math.Round(_fuelLevelPct, 1),
            BrakeWearPct = Math.Round(_brakeWearPct, 1),
            OdometerKm = Math.Round(_odometerKm, 1)
        };

        if (anomaly)
        {
            record.FaultCodes.Add(FaultCodes[_random.Next(FaultCodes.Length)]);
        }

        return record;
    }

    private double GaussianNoise(double stdDev)
    {
        // Box-Muller transform, since Random has no built-in normal distribution
        double u1 = 1.0 - _random.NextDouble();
        double u2 = _random.NextDouble();
        double standardNormal = Math.Sqrt(-2.0 * Math.Log(u1)) * Math.Sin(2.0 * Math.PI * u2);
        return standardNormal * stdDev;
    }
}
