using Daemon;
using Daemon.Platform.Windows;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.AddWindowsService(options =>
{
    options.ServiceName = "oeims";
});

builder.Services.AddWindowsPlatform();

builder.Services.AddMonitors();
builder.Services.AddMitigators();

builder.Services.AddHostedService<Worker>();

builder.Logging.AddEventLog();

var host = builder.Build();
host.Run();
