using Microsoft.AspNetCore.Builder;
using OEIMS.Sentinel.Service;

// Sentinel Service entry point.
// Runs as a Windows Service in production and owns the local exam-integrity flow:
// pre-exam validation, local mitigations, Service-side monitors, Agent IPC, heartbeat,
// and realtime communication with the OEIMS server.
var builder = WebApplication.CreateBuilder(args);

builder.AddExamMonitoringService();

var app = builder.Build();

app.UseExamMonitoringService();

app.Run();