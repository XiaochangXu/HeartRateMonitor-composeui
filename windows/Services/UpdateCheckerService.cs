using System.Text.Json;
using HeartRate.Helpers;

namespace HeartRate.Services;

/// <summary>
/// Gitee Release 检查更新工具（与 Android 端 UpdateChecker 逻辑保持一致）。
///
/// 端点：`https://gitee.com/api/v5/repos/{owner}/{repo}/releases?page=1&per_page=100`
///
/// 注意：不使用 `/releases/latest`，因为 Gitee 的 latest 标记可能延迟或不准，
/// 改为拉取列表后按语义化版本比较取最高版本，跳过 prerelease。
/// </summary>
public static class UpdateCheckerService
{
    private const string Owner = "xiaochang-xu";
    private const string Repo = "heart-rate-monitor-windows";
    private const string ApiUrl = $"https://gitee.com/api/v5/repos/{Owner}/{Repo}/releases?page=1&per_page=100";
    private const string ReleasePageUrl = $"https://gitee.com/{Owner}/{Repo}/releases/latest";

    /// <summary>检查结果（与 Android 端 UpdateChecker.Result 对应）。</summary>
    public abstract record Result
    {
        /// <summary>发现新版本。[NewVersion] 已去除 'v' 前缀，[ReleaseNotes] 为 Release body（可能为空），[HtmlUrl] 为 Release 页 URL。</summary>
        public sealed record UpdateAvailable(string NewVersion, string ReleaseNotes, string HtmlUrl) : Result;

        /// <summary>当前已是最新版本。[CurrentVersion] 当前版本号。</summary>
        public sealed record UpToDate(string CurrentVersion) : Result;

        /// <summary>检查失败。[Message] 错误描述（用于弹窗显示）。</summary>
        public sealed record Error(string Message) : Result;
    }

    /// <summary>
    /// 异步检查最新版本。
    /// </summary>
    /// <param name="currentVersion">当前应用版本（如 "4.5"）。</param>
    /// <param name="ct">取消令牌。</param>
    public static async Task<Result> CheckAsync(string currentVersion, CancellationToken ct = default)
    {
        using var http = new HttpClient();
        http.Timeout = TimeSpan.FromSeconds(10);
        http.DefaultRequestHeaders.UserAgent.ParseAdd("HeartRateMonitor-Windows-App");
        http.DefaultRequestHeaders.Accept.ParseAdd("application/json");

        try
        {
            using var resp = await http.GetAsync(ApiUrl, ct);
            var code = (int)resp.StatusCode;
            if (code == 404)
                return new Result.Error(Loc.GetString("VersionInfo_NoRelease"));
            if (code == 403)
                return new Result.Error(Loc.GetString("VersionInfo_ApiRateLimit"));
            if (!resp.IsSuccessStatusCode)
                return new Result.Error(Loc.Format("VersionInfo_ApiError", code));
            var body = await resp.Content.ReadAsStringAsync(ct);
            return FindLatestRelease(body, currentVersion);
        }
        catch (Exception e)
        {
            // 消息可能含 { }，先转义再格式化，避免 string.Format 抛 FormatException。
            var safeMsg = e.Message.Replace("{", "{{").Replace("}", "}}");
            return new Result.Error(Loc.Format("VersionInfo_NetworkError", safeMsg));
        }
    }

    /// <summary>
    /// 从 Gitee releases 列表中找版本号最高的 release，与当前版本比较。
    /// 跳过 prerelease，取 tag_name 按语义化版本比较取最大值。
    /// </summary>
    private static Result FindLatestRelease(string body, string currentVersion)
    {
        using var doc = JsonDocument.Parse(body);
        var root = doc.RootElement;
        if (root.ValueKind != JsonValueKind.Array || root.GetArrayLength() == 0)
            return new Result.Error(Loc.GetString("VersionInfo_NoRelease"));

        var bestVersion = string.Empty;
        var bestNotes = string.Empty;
        var bestUrl = ReleasePageUrl;

        foreach (var release in root.EnumerateArray())
        {
            if (release.TryGetProperty("prerelease", out var pre) && pre.ValueKind == JsonValueKind.True)
                continue;
            var tag = release.TryGetProperty("tag_name", out var t) ? t.GetString() ?? string.Empty : string.Empty;
            tag = tag.TrimStart('v', 'V').Trim();
            if (tag.Length == 0)
                continue;
            if (bestVersion.Length == 0 || CompareVersions(tag, bestVersion) > 0)
            {
                bestVersion = tag;
                bestNotes = release.TryGetProperty("body", out var b)
                    ? (b.GetString() ?? string.Empty).Trim()
                    : string.Empty;
                bestUrl = release.TryGetProperty("html_url", out var u)
                    ? u.GetString() ?? ReleasePageUrl
                    : ReleasePageUrl;
            }
        }

        if (bestVersion.Length == 0)
            return new Result.Error(Loc.GetString("VersionInfo_NoValidRelease"));

        return CompareVersions(currentVersion, bestVersion) < 0
            ? new Result.UpdateAvailable(bestVersion, bestNotes, bestUrl)
            : new Result.UpToDate(currentVersion);
    }

    /// <summary>
    /// 语义化版本比较。
    /// 支持 "1.2.3" / "1.2" / "1" 等格式，不足的位补 0；不处理预发布后缀（-alpha/-beta 等）。
    /// </summary>
    /// <returns>负数 = current &lt; remote（有更新）；0 = 相等；正数 = current &gt; remote。</returns>
    private static int CompareVersions(string current, string remote)
    {
        var cur = ParseVersion(current);
        var rem = ParseVersion(remote);
        var maxLen = Math.Max(cur.Length, rem.Length);
        for (var i = 0; i < maxLen; i++)
        {
            var c = i < cur.Length ? cur[i] : 0;
            var r = i < rem.Length ? rem[i] : 0;
            if (c != r)
                return c - r;
        }
        return 0;
    }

    private static int[] ParseVersion(string version) =>
        version.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();
}
