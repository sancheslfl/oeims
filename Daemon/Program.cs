using Daemon;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.AddWindowsService(options =>
{
    options.ServiceName = "Online Exam Monitor Service";
}); 
builder.Services.AddHostedService<Worker>();

builder.Logging.AddEventLog();

var host = builder.Build();
host.Run();
