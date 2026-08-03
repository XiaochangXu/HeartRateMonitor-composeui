namespace HeartRate.Helpers;

/// <summary>
/// Strongly-typed accessors for resource strings. XAML uses <c>x:Uid</c> on the
/// same keys (resw entries for XAML carry a property suffix like <c>.Text</c>).
/// </summary>
public static class L
{
    // ── MainWindow ──────────────────────────────────────────────────────────
    public static string MainWindow_Title      => Loc.GetString("MainWindow_Title");
    public static string MainWindow_ToggleFloat => Loc.GetString("MainWindow_ToggleFloat");

    // ── DeviceList ──────────────────────────────────────────────────────────
    public static string DeviceList_HeaderText    => Loc.GetString("DeviceList_HeaderText");
    public static string DeviceList_StartScan     => Loc.GetString("DeviceList_StartScan");
    public static string DeviceList_StopScan      => Loc.GetString("DeviceList_StopScan");
    public static string DeviceList_Clear         => Loc.GetString("DeviceList_Clear");
    public static string DeviceList_UnknownDevice => Loc.GetString("DeviceList_UnknownDevice");
    public static string DeviceList_EmptyHint     => Loc.GetString("DeviceList_EmptyHint");
    public static string DeviceList_FoundDevice   => Loc.GetString("DeviceList_FoundDevice");
    public static string DeviceList_BluetoothUnavailable => Loc.GetString("DeviceList_BluetoothUnavailable");
    public static string DeviceList_ScanAutoStopped => Loc.GetString("DeviceList_ScanAutoStopped");
    public static string DeviceList_AutoConnectTimeout => Loc.GetString("DeviceList_AutoConnectTimeout");

    // ── HeartRate ───────────────────────────────────────────────────────────
    public static string HeartRate_HeaderText    => Loc.GetString("HeartRate_HeaderText");
    public static string HeartRate_Connect       => Loc.GetString("HeartRate_Connect");
    public static string HeartRate_Disconnect    => Loc.GetString("HeartRate_Disconnect");
    public static string HeartRate_NotConnected  => Loc.GetString("HeartRate_NotConnected");
    public static string HeartRate_Connecting    => Loc.GetString("HeartRate_Connecting");
    public static string HeartRate_Reconnecting  => Loc.GetString("HeartRate_Reconnecting");
    public static string HeartRate_ReconnectFailed => Loc.GetString("HeartRate_ReconnectFailed");
    public static string HeartRate_ConnectedName => Loc.GetString("HeartRate_ConnectedName");
    public static string HeartRate_ConnectFailed => Loc.GetString("HeartRate_ConnectFailed");
    public static string HeartRate_Disconnected  => Loc.GetString("HeartRate_Disconnected");
    public static string HeartRate_NoDevice      => Loc.GetString("HeartRate_NoDevice");
    public static string HeartRate_DeviceName    => Loc.GetString("HeartRate_DeviceName");
    public static string HeartRate_ConnectionMode => Loc.GetString("HeartRate_ConnectionMode");
    public static string HeartRate_ConnModeBluetooth => Loc.GetString("HeartRate_ConnModeBluetooth");
    public static string HeartRate_ConnModeLan   => Loc.GetString("HeartRate_ConnModeLan");
    public static string HeartRate_Address       => Loc.GetString("HeartRate_Address");
    public static string HeartRate_HasHrService  => Loc.GetString("HeartRate_HasHrService");
    public static string HeartRate_Yes           => Loc.GetString("HeartRate_Yes");
    public static string HeartRate_No            => Loc.GetString("HeartRate_No");
    public static string HeartRate_FloatLabel    => Loc.GetString("HeartRate_FloatLabel");
    public static string HeartRate_Bpm           => Loc.GetString("HeartRate_Bpm");
    public static string HeartRate_BluetoothBlockedTitle => Loc.GetString("HeartRate_BluetoothBlockedTitle");
    public static string HeartRate_BluetoothBlockedBody => Loc.GetString("HeartRate_BluetoothBlockedBody");
    public static string HeartRate_DeviceNotInRangeTitle => Loc.GetString("HeartRate_DeviceNotInRangeTitle");
    public static string HeartRate_DeviceNotInRangeBody => Loc.GetString("HeartRate_DeviceNotInRangeBody");
    public static string Dialog_Ok               => Loc.GetString("Dialog_Ok");

    // ── NetworkTransfer ────────────────────────────────────────────────────
    public static string Network_Disabled => Loc.GetString("Network_Disabled");
    public static string Network_Stopped  => Loc.GetString("Network_Stopped");
    public static string PortConflict_Title => Loc.GetString("PortConflict_Title");
    public static string PortConflict_Body  => Loc.GetString("PortConflict_Body");

    public static string Webhook_DeleteConfirmTitle => Loc.GetString("Webhook_DeleteConfirmTitle");
    public static string Webhook_Delete             => Loc.GetString("Webhook_Delete");
    public static string Webhook_Cancel             => Loc.GetString("Webhook_Cancel");

    // ── LanTransfer (局域网传输) ─────────────────────────────────────────────
    public static string Lan_Disabled     => Loc.GetString("Lan_Disabled");
    public static string Lan_Stopped      => Loc.GetString("Lan_Stopped");
    public static string Lan_PairTitle    => Loc.GetString("Lan_PairTitle");
    public static string Lan_PairApprove  => Loc.GetString("Lan_PairApprove");
    public static string Lan_PairReject   => Loc.GetString("Lan_PairReject");
    public static string Lan_PortInUse_Title => Loc.GetString("Lan_PortInUse_Title");
    public static string Lan_PortInUse_Keep  => Loc.GetString("Lan_PortInUse_Keep");
    public static string Lan_PortUnavailable_Title => Loc.GetString("Lan_PortUnavailable_Title");

    // ── Appearance (主题和语言) ────────────────────────────────────────────
    public static string Appearance_Header       => Loc.GetString("Appearance_Header.Text");
    public static string Appearance_RestartHint  => Loc.GetString("Appearance_RestartHint.Text");

    // ── VersionInfo (版本与信息 / 检查更新) ────────────────────────────────
    public static string VersionInfo_CheckingUpdate   => Loc.GetString("VersionInfo_CheckingUpdate");
    public static string VersionInfo_UpdateCheckTitle => Loc.GetString("VersionInfo_UpdateCheckTitle");
    public static string VersionInfo_ReleaseNotesTitle => Loc.GetString("VersionInfo_ReleaseNotesTitle");
    public static string VersionInfo_NoReleaseNotes    => Loc.GetString("VersionInfo_NoReleaseNotes");
    public static string VersionInfo_GoUpdate          => Loc.GetString("VersionInfo_GoUpdate");
    public static string VersionInfo_Cancel            => Loc.GetString("VersionInfo_Cancel");
}
