using Daemon;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.AddExamMonitoringService(
    builder.Configuration,
    builder.Environment);

if (builder.Environment.IsProduction())
    builder.Logging.AddEventLog();

var host = builder.Build();

if (builder.Environment.IsProduction())
    host.Services.GetRequiredService<SingleInstanceGuard>();

host.Run();