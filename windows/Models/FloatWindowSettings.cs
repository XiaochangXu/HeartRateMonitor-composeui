using CommunityToolkit.Mvvm.ComponentModel;
using HeartRate.Helpers;
using HeartRate.Services;

namespace HeartRate.Models;

/// <summary>
/// 悬浮窗外观与位置设置。JSON 序列化到 %LOCALAPPDATA%\HeartRate\settings.json。
/// 除位置外均为 ObservableProperty，UI 直接双向绑定；属性变化即触发防抖保存。
/// </summary>
public partial class FloatWindowSettings : ObservableObject
{
    // ── 位置（由 FloatWindow 直接写，保存时机由它控制）─────────────────────
    public bool HasPosition { get; set; }
    public int PositionX { get; set; }
    public int PositionY { get; set; }

    // ── 主窗口位置与大小（由 MainWindow 保存/恢复）────────────────────────
    public bool HasWindowBounds { get; set; }
    public int WindowX { get; set; }
    public int WindowY { get; set; }
    public int WindowWidth { get; set; }
    public int WindowHeight { get; set; }

    // ── 网络传输（HTTP/WebSocket 服务 + Webhook）──────────────────────────
    // 旧 settings.json 没有该字段时反序列化保留默认实例，向后兼容。
    public NetworkSettings Network { get; set; } = new();

    // ── 局域网传输（mDNS 广播 + 配对 HTTP 服务 + WS 客户端）────────────────
    // 旧 settings.json 没有该字段时反序列化保留默认实例，向后兼容。
    public LanTransferSettings LanTransfer { get; set; } = new();

    // ── 主题与语言 ─────────────────────────────────────────────────────────
    // ThemeMode: Default=跟随系统 / Light / Dark，默认 Light。
    // Language: "zh-CN" 或 "en-US"，默认 "zh-CN"；语言切换需重启进程生效。
    [ObservableProperty]
    private ElementTheme _themeMode = ElementTheme.Light;

    [ObservableProperty]
    private string _language = "zh-CN";

    // ── 外观 ──────────────────────────────────────────────────────────────
    // 默认外观值：用于字段初始化与「重置为默认值」。
    public const bool DefaultShowBpmText = true;
    public const bool DefaultShowHeart = true;
    public const double DefaultWindowScale = 1.6;
    public const double DefaultHeartSize = 50;
    public const string DefaultHeartColor = "#EB3C50";

    [ObservableProperty]
    private bool _showBpmText = DefaultShowBpmText;

    [ObservableProperty]
    private bool _showHeart = DefaultShowHeart;

    [ObservableProperty]
    private double _windowScale = DefaultWindowScale;

    [ObservableProperty]
    private double _heartSize = DefaultHeartSize;

    [ObservableProperty]
    private string _heartColor = DefaultHeartColor;

    // ── 心跳动画 ──────────────────────────────────────────────────────────
    public const bool DefaultShowHeartbeatAnimation = false;

    /// <summary>开启后悬浮窗爱心按当前 bpm 节拍缩放（心跳动画）。</summary>
    [ObservableProperty]
    private bool _showHeartbeatAnimation = DefaultShowHeartbeatAnimation;

    /// <summary>
    /// 将外观相关设置重置为默认值。仅重置外观（缩放/图标大小/颜色/显示项/心跳动画），
    /// 不影响触摸穿透与热键。
    /// </summary>
    public void ResetAppearanceToDefaults()
    {
        WindowScale = DefaultWindowScale;
        HeartSize = DefaultHeartSize;
        HeartColor = DefaultHeartColor;
        ShowHeart = DefaultShowHeart;
        ShowBpmText = DefaultShowBpmText;
        ShowHeartbeatAnimation = DefaultShowHeartbeatAnimation;
    }

    // ── 触摸穿透（锁定）──────────────────────────────────────────────────
    // 开启后悬浮窗不接收鼠标（WM_NCHITTEST 返回 HTTRANSPARENT），
    // 鼠标点击穿透到下方窗口，悬浮窗固定在原位不可拖拽。
    [ObservableProperty]
    private bool _clickThroughEnabled = false;

    // 全局热键字符串，格式 "Ctrl+Shift+T"。空字符串表示未设置热键。
    [ObservableProperty]
    private string _clickThroughHotKey = "Ctrl+T";

    partial void OnShowBpmTextChanged(bool value) => SettingsService.Save();
    partial void OnShowHeartChanged(bool value) => SettingsService.Save();
    partial void OnShowHeartbeatAnimationChanged(bool value) => SettingsService.Save();

    partial void OnThemeModeChanged(ElementTheme value)
    {
        SettingsService.Save();
        ThemeHelper.ApplyTheme(value);
    }

    partial void OnLanguageChanged(string value) => SettingsService.Save();

    partial void OnClickThroughEnabledChanged(bool value) => SettingsService.Save();
    partial void OnClickThroughHotKeyChanged(string value) => SettingsService.Save();

    partial void OnWindowScaleChanged(double value)
    {
        WindowScale = Math.Clamp(value, 0.5, 2.0);
        SettingsService.Save();
    }

    partial void OnHeartSizeChanged(double value)
    {
        HeartSize = Math.Clamp(value, 12, 64);
        SettingsService.Save();
    }

    partial void OnHeartColorChanged(string value)
    {
        HeartColor = string.IsNullOrWhiteSpace(value) ? "#EB3C50" : value;
        SettingsService.Save();
    }
}
