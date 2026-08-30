using VehicleSimulator;

namespace VehicleSimulator.Tests;

public class TelemetryGeneratorTests
{
    [Fact]
    public void RecordCarriesTheVehicleIdFromTheProfile()
    {
        var profile = VehicleProfile.NewVehicle("FLEET-001");
        var generator = new TelemetryGenerator(profile, new Random(1));

        var record = generator.NextRecord(DateTime.UtcNow);

        Assert.Equal("FLEET-001", record.VehicleId);
    }

    [Fact]
    public void ReadingsNeverGoNegative()
    {
        var profile = VehicleProfile.NewVehicle("FLEET-001");
        var generator = new TelemetryGenerator(profile, new Random(42));

        for (int i = 0; i < 500; i++)
        {
            var record = generator.NextRecord(DateTime.UtcNow);
            Assert.True(record.Rpm >= 0, $"rpm went negative: {record.Rpm}");
            Assert.True(record.VibrationMmS >= 0, $"vibration went negative: {record.VibrationMmS}");
            Assert.True(record.BrakeWearPct is >= 0 and <= 100, $"brake wear out of range: {record.BrakeWearPct}");
        }
    }

    [Fact]
    public void OdometerNeverDecreases()
    {
        var profile = VehicleProfile.NewVehicle("FLEET-001");
        var generator = new TelemetryGenerator(profile, new Random(7));

        double previous = profile.StartingOdometerKm;
        for (int i = 0; i < 200; i++)
        {
            var record = generator.NextRecord(DateTime.UtcNow);
            Assert.True(record.OdometerKm >= previous, "odometer should only ever increase");
            previous = record.OdometerKm;
        }
    }

    [Fact]
    public void AnomalousReadingsRunHotterOnAverageThanNormalReadings()
    {
        // both the temp spike and the fault code come from the same anomaly branch in
        // NextRecord, but the spike itself is randomized (0-25C) so it can't be asserted
        // per-record — only that anomalous ticks run hotter on average across many samples
        var profile = VehicleProfile.NewVehicle("FLEET-001");
        var generator = new TelemetryGenerator(profile, new Random(99));

        var anomalyTemps = new List<double>();
        var normalTemps = new List<double>();

        for (int i = 0; i < 2000; i++)
        {
            var record = generator.NextRecord(DateTime.UtcNow);
            (record.FaultCodes.Count > 0 ? anomalyTemps : normalTemps).Add(record.EngineTempC);
        }

        Assert.NotEmpty(anomalyTemps);
        Assert.True(anomalyTemps.Average() > normalTemps.Average());
    }

    [Fact]
    public void AgingVehicleStartsWithHigherBaselineWearThanNewVehicle()
    {
        var aging = VehicleProfile.AgingVehicle("FLEET-003");
        var fresh = VehicleProfile.NewVehicle("FLEET-001");

        Assert.True(aging.StartingBrakeWearPct > fresh.StartingBrakeWearPct);
        Assert.True(aging.StartingOdometerKm > fresh.StartingOdometerKm);
    }
}
