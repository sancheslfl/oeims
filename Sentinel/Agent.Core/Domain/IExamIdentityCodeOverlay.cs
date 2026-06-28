namespace OEIMS.Sentinel.Agent.Domain;

public interface IExamIdentityCodeOverlay
{
    Task DisplayCodeAsync(string code, CancellationToken ct = default);
}
