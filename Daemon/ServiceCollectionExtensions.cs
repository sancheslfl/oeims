using Daemon.Domain;
using Daemon.Mitigators;
using Daemon.Monitors;
using Daemon.Platform.Windows;

namespace Daemon;

internal static class ServiceCollectionExtensions
{
    public static IServiceCollection AddMonitors(this IServiceCollection services)
    {
        services.AddSingleton<IActiveWindowSource, WinActiveWindowSource>();
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