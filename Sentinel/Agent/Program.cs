using Contracts;
using OEIMS.Sentinel.Agent;
using OEIMS.Sentinel.Agent.Domain;
using OEIMS.Sentinel.Agent.Mitigators;
using OEIMS.Sentinel.Agent.Monitors;
using OEIMS.Sentinel.Agent.Platform.Windows;

var builder = Host.CreateApplicationBuilder(args);

builder.Logging.ClearProviders();
builder.Logging.AddConsole();
builder.Logging.AddDebug();

builder.Services.AddHostedService<Worker>();

builder.Services.AddSingleton<IActiveWindowSource, WinActiveWindowSource>();
builder.Services.AddSingleton<IClipboardSource, WinClipboardSource>();

builder.Services.AddSingleton<IMonitor, FocusMonitor>();
builder.Services.AddSingleton<IMitigator, ClipboardBlocker>();


await builder.Build().RunAsync();