using Microsoft.Windows.ApplicationModel.Resources;

namespace HeartRate.Helpers;

/// <summary>
/// Thin wrapper over WinAppSDK's <see cref="ResourceLoader"/>. The default
/// constructor resolves to the "Resources" resource map (which maps to
/// <c>Strings/{lang}/Resources.resw</c>). Cached once at static init; values
/// are frozen at the locale active when this class is first touched, so any
/// language change requires a process restart.
/// </summary>
public static class Loc
{
    private static readonly ResourceLoader _loader = new();

    public static string GetString(string key) => _loader.GetString(key);

    public static string Format(string key, params object?[] args) =>
        string.Format(_loader.GetString(key), args);
}
