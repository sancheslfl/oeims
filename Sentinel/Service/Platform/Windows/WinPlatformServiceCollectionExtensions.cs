using OEIMS.Sentinel.Service.Domain.Platform;

namespace OEIMS.Sentinel.Service.Platform.Windows;

public static class WinPlatformServiceCollectionExtensions
{
    public static IServiceCollection AddWindowsPlatform(this IServiceCollection services)
    {
        services.AddSingleton<IProcessSource, WindowsProcessSource>();

        return services;
    }
}