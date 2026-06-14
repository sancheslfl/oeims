using Contracts;
using OEIMS.Sentinel.Service.Mitigators;
using OEIMS.Sentinel.Service.Monitors;
using OEIMS.Sentinel.Service.Platform.Windows;
using OEIMS.Sentinel.Service.ServerConnection;

namespace OEIMS.Sentinel.Service;

internal static class ServiceCollectionExtensions
{
    extension(IServiceCollection services)
    {
        internal IServiceCollection AddExamMonitoringService(
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

        private void AddMonitors()
        {
            services.AddSingleton<IMonitor, ProcessMonitor>();
            services.AddSingleton<IMonitor, NetworkMonitor>();
        }

        private void AddMitigators() => services.AddSingleton<IMitigator, ProcessBlocker>();
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