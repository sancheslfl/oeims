using Daemon.Domain;
using Daemon.Mitigators;
using Daemon.Monitors;
using Daemon.Platform.Windows;
using Daemon.ServerConnection;

namespace Daemon;

internal static class ServiceCollectionExtensions
{
    internal static IServiceCollection AddExamMonitoringService(
        this IServiceCollection services,
        IConfiguration configuration,
        IHostEnvironment environment)
    {
        if (environment.IsProduction())
        {
            services.AddWindowsService(options =>
            {
                options.ServiceName = "oeims";
            });

            services.AddSingleton<SingleInstanceGuard>();
        }

        services.AddWindowsPlatform();

        services.AddMonitors();
        services.AddMitigators();

        services.AddSingleton(GetApplicationConfig(configuration));
        services.AddSingleton(GetServerConfig(configuration));

        services.AddSingleton<DaemonWebSocketClient>();
        services.AddHttpClient<HeartbeatSender>();

        services.AddHostedService<Worker>();

        return services;
    }

    private static IServiceCollection AddMonitors(this IServiceCollection services)
    {
        services.AddSingleton<IMonitor, FocusMonitor>();
        services.AddSingleton<IMonitor, ProcessMonitor>();
        services.AddSingleton<IMonitor, NetworkMonitor>();

        return services;
    }

    private static IServiceCollection AddMitigators(this IServiceCollection services)
    {
        services.AddSingleton<IMitigator, ClipboardBlocker>();
        services.AddSingleton<IMitigator, ProcessBlocker>();

        return services;
    }

    private static ApplicationConfig GetApplicationConfig(IConfiguration configuration)
    {
        return configuration
            .GetSection("Application")
            .Get<ApplicationConfig>() ?? new ApplicationConfig();
    }

    private static ServerConfig GetServerConfig(IConfiguration configuration)
    {
        return configuration
            .GetSection("Server")
            .Get<ServerConfig>() ?? new ServerConfig();
    }
}