using OEIMS.Sentinel.Agent;
using OEIMS.Sentinel.Agent.Domain;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.AddAgentCore();

builder.Services.AddSingleton<IActiveWindowSource, WinActiveWindowSource>();
builder.Services.AddSingleton<IClipboardSource, WinClipboardSource>();
builder.Services.AddSingleton<IExamIdentityCodeOverlay, WinExamIdentityCodeOverlay>();

var host = builder.Build();

await host.RunAsync();