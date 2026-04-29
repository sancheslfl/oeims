using Daemon.Abstractions;
using Daemon.Monitors;
using Daemon.Mitigators;

namespace Daemon;

internal static class ServiceCollectionExtensions
{
    public static IServiceCollection AddMonitors(this IServiceCollection services)
    {
        services.AddSingleton<IMonitor, FocusMonitor>();
        services.AddSingleton<IMonitor, ProcessMonitor>();
        services.AddSingleton<IMonitor, NetworkMonitor>();

        return services;
    }

    public static IServiceCollection AddMitigators(this IServiceCollection services)
    {
        services.AddSingleton<IMitigator, ClipboardBlocker>();
        services.AddSingleton<IMitigator, ProcessBlocker>();

        return services;
    }
}