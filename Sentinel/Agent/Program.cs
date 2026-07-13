using OEIMS.Sentinel.Agent;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.AddAgent();

await builder.Build().RunAsync();