namespace OEIMS.Sentinel.Agent.Domain;

internal interface IExamIdentityCodeOverlay
{
    Task ShowAsync(string code, CancellationToken ct = default);
}
