using System.Runtime.InteropServices;
using HeartRate.Helpers;
using HeartRate.Services;
using Microsoft.UI.Dispatching;
using Windows.Win32;
using Windows.Win32.Foundation;
using Windows.Win32.Graphics.Direct2D;
using Windows.Win32.Graphics.Direct2D.Common;
using Windows.Win32.Graphics.Direct3D;
using Windows.Win32.Graphics.Direct3D11;
using Windows.Win32.Graphics.Dxgi;
using Windows.Win32.Graphics.DirectComposition;
using Windows.Win32.Graphics.DirectWrite;
using Windows.Win32.Graphics.Dxgi.Common;
using Windows.Win32.Graphics.Gdi;
using Windows.Win32.UI.WindowsAndMessaging;

namespace HeartRate.Windows;

/// <summary>
/// 真正透明的悬浮窗：纯 Win32 窗口 + DirectComposition 合成。
/// DComp surface 本身携带逐像素 alpha，由 DWM 直接合成，天然支持真透明；
/// 内容用 Direct2D（IDCompositionSurface::BeginDraw 提供的 device context）绘制，
/// 文字用 DirectWrite 渲染。完全绕过 WinUI 3 XAML 渲染管线
/// （其合成表面无 alpha，无法透明）与 GDI+/UpdateLayeredWindow。
/// COM 接口由 Microsoft.Windows.CsWin32 生成（托管 RCW 风格，失败抛 COMException）。
/// </summary>
public sealed class FloatWindow
{
    // ── 消息常量（CsWin32 不生成 #define）──
    private const uint WM_NCHITTEST = 0x0084;
    private const uint WM_MOUSEMOVE = 0x0200;
    private const uint WM_LBUTTONDOWN = 0x0201;
    private const uint WM_LBUTTONUP = 0x0202;
    private const uint WM_LBUTTONDBLCLK = 0x0203;
    private const uint WM_RBUTTONDOWN = 0x0204;
    private const uint WM_DESTROY = 0x0002;
    private const uint WM_CAPTURECHANGED = 0x0215;
    private const uint WM_DPICHANGED = 0x02E0;

    private const uint D3D11_SDK_VERSION = 7;
    private const int BaseWidth = 140;
    private const int BaseHeight = 64;
    private const string PropName = "HeartRate_Float";

    private static readonly WNDPROC _wndProc = WndProc;

    // ── 窗口状态 ──────────────────────────────────────────────────────────
    private nint _hwnd;
    private bool _closed;
    private bool _dragging;
    private bool _mouseDown;
    private int _downX, _downY;
    private int _dragOffsetX, _dragOffsetY;
    private int _posX, _posY;
    private bool _hasPosition;

    // 区分“点击”与“拖拽”：移动超过该阈值（像素）才视为拖拽
    private const int DragThreshold = 3;

    // ── 设置缓存 ──────────────────────────────────────────────────────────
    private string _bpmText = "--";
    private bool _showBpmText = true;
    private bool _showHeart = true;
    // 尺寸公式与安卓端 FloatingWindowService.updateWindowAppearance 对齐：
    // 整体比例 = FloatingSize/100；图标比例 = FloatingIconSize/100；
    // 爱心 = 基础 22 × 整体 × 图标；bpm 数字 = 16 × 整体；"bpm" = 12 × 整体；
    // 内边距 = 8 × 整体；爱心-数字间距 = 4 × 整体；"bpm" 前间距 = 2 × 整体。
    private float _scale = 1f;
    private float _iconScale = 1f;
    private int _heartColorR = 235, _heartColorG = 60, _heartColorB = 80;
    private bool _clickThrough;
    private bool _showHeartbeatAnimation;

    // ── 心跳动画 ──────────────────────────────────────────────────────────
    // 相位积分器设计：_phase 为连续累计的周期数（波形只取其小数部分）。
    // bpm 变化仅改变积分速率、从不重置相位，因此心率快速刷新/波动时
    // 节拍连续、无"跳拍"，观感平滑。动画 bpm 经低通渐变到目标值。
    private float _animScale = 1f;
    private int _animBpm;
    private double _phase;
    private double _animBpmSmooth;
    private DateTimeOffset? _lastTick;
    private readonly DispatcherQueue? _uiDispatcher;
    private DispatcherQueueTimer? _animTimer;

    // ── DComp / D2D / DWrite（RCW，GC 自动释放）─────────────────────────
    private IDCompositionDesktopDevice? _device;
    private IDCompositionTarget? _target;
    private IDCompositionVisual2? _visual;
    private IDCompositionSurface? _surface;
    private uint _surfaceW, _surfaceH;
    private IDWriteFactory? _dwrite;
    private IDWriteTextFormat? _heartFormat;
    private IDWriteTextFormat? _bpmFormat;
    private IDWriteTextFormat? _bpmLabelFormat;
    private ID2D1PathGeometry? _heartGeometry;
    private float _heartGeometrySize = -1f;

    public FloatWindow()
    {
        // FloatWindow 在 UI 线程构造（由 MainWindow 的可见性请求触发），
        // 捕获 DispatcherQueue 供心跳动画 DispatcherQueueTimer 使用。
        _uiDispatcher = DispatcherQueue.GetForCurrentThread();
        LoadSettings();
        CreateFloatWindow();
    }


    private void LoadSettings()
    {
        var s = SettingsService.Current;
        _showBpmText = s.ShowBpmText;
        _showHeart = s.ShowHeart;
        _showHeartbeatAnimation = s.ShowHeartbeatAnimation;
        _scale = s.FloatingSize / 100f;
        _iconScale = s.FloatingIconSize / 100f;
        var hc = ColorUtil.Parse(s.HeartColor);
        _heartColorR = hc.R; _heartColorG = hc.G; _heartColorB = hc.B;
        _clickThrough = s.ClickThroughEnabled;
        _hasPosition = s.HasPosition;
        _posX = s.PositionX; _posY = s.PositionY;
    }

    /// <summary>
    /// 创建 Win32 窗口并初始化 DComp 合成。任何一步失败都抛异常：
    /// 由 MainWindow 捕获后置 _floatWindow=null，避免留下不可见但拦截点击的悬空窗口。
    /// Close() 销毁窗口后（WM_DESTROY → Cleanup 会退订事件）可再次调用本方法重建。
    /// </summary>
    private void CreateFloatWindow()
    {
        SettingsService.Changed += OnSettingsChanged;

        var hInst = PInvoke.GetModuleHandle(default(PCWSTR));
        var wc = new WNDCLASSW
        {
            lpfnWndProc = _wndProc,
            hInstance = hInst,
        };
        unsafe
        {
            fixed (char* pCls = "HeartRate_Float")
            {
                wc.lpszClassName = new PCWSTR(pCls);
                PInvoke.RegisterClass(wc); // 类已存在时返回 0（ERROR_CLASS_ALREADY_EXISTS），可忽略
            }
        }

        var w = Math.Max(1, (int)(BaseWidth * _scale));
        var h = Math.Max(1, (int)(BaseHeight * _scale));

        var hwnd = CreateWindowNative("HeartRate_Float", w, h, hInst);
        _hwnd = (nint)(IntPtr)hwnd;
        if (_hwnd == 0) throw new InvalidOperationException("CreateWindowEx failed for float window");

        var gc = GCHandle.ToIntPtr(GCHandle.Alloc(this));
        unsafe
        {
            fixed (char* p = PropName)
                PInvoke.SetProp(hwnd, new PCWSTR(p), new HANDLE(gc));
        }

        try
        {
            InitComposition(); // 失败抛异常
        }
        catch
        {
            // 销毁窗口触发 WM_DESTROY → Cleanup → 释放 GCHandle 并退订事件
            PInvoke.DestroyWindow(hwnd);
            throw;
        }

        // PerMonitorV2 下 CreateWindowEx 尺寸即物理像素；surface 按 dpi 放大，
        // 若不校正会导致高 DPI 下内容被窗口裁剪。
        ApplySizeFromDpi();
        Render();
        PlaceOnScreen();
    }

    private static unsafe HWND CreateWindowNative(string cls, int w, int h, HINSTANCE hInst)
    {
        fixed (char* pCls = cls)
        {
            return PInvoke.CreateWindowEx(
                WINDOW_EX_STYLE.WS_EX_TOOLWINDOW | WINDOW_EX_STYLE.WS_EX_NOACTIVATE | WINDOW_EX_STYLE.WS_EX_TOPMOST,
                new PCWSTR(pCls), default,
                WINDOW_STYLE.WS_POPUP | WINDOW_STYLE.WS_VISIBLE,
                0, 0, w, h,
                HWND.Null, HMENU.Null, hInst, null);
        }
    }

    // ── DirectComposition 初始化 ─────────────────────────────────────────

    private void InitComposition()
    {
        var iid = typeof(IDCompositionDesktopDevice).GUID;
        unsafe
        {
            // 1) D3D11 设备：D2D1CreateDevice 需要其 IDXGIDevice
            ID3D11Device d3dDevice;
            ID3D11DeviceContext d3dContext;
            var hr = PInvoke.D3D11CreateDevice(null,
                D3D_DRIVER_TYPE.D3D_DRIVER_TYPE_HARDWARE,
                default, D3D11_CREATE_DEVICE_FLAG.D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                null, 0, D3D11_SDK_VERSION,
                out d3dDevice, null, out d3dContext);
            if (hr.Failed) throw new InvalidOperationException($"D3D11CreateDevice failed: hr=0x{hr.Value:X8}");

            // 2) IDXGIDevice：显式 QI（D3D11 设备实现 IDXGIDevice）
            var giid = typeof(IDXGIDevice).GUID;
            nint pUnk = Marshal.GetIUnknownForObject(d3dDevice);
            int qr = Marshal.QueryInterface(pUnk, ref giid, out nint pDxgi);
            Marshal.Release(pUnk);
            if (qr != 0) throw new InvalidOperationException($"QueryInterface IDXGIDevice failed: hr=0x{qr:X8}");
            var dxgiDevice = (IDXGIDevice)Marshal.GetObjectForIUnknown(pDxgi);
            Marshal.Release(pDxgi);

            // 3) ID2D1Device：DComp 设备传 null 无法 CreateSurface，必须绑定渲染设备
            ID2D1Device d2dDevice;
            hr = PInvoke.D2D1CreateDevice(dxgiDevice, (D2D1_CREATION_PROPERTIES*)null, out d2dDevice);
            if (hr.Failed) throw new InvalidOperationException($"D2D1CreateDevice failed: hr=0x{hr.Value:X8}");

            // 4) DComp 设备绑定 D2D 设备（CreateSurface 可用，BeginDraw 返回 ID2D1DeviceContext）
            hr = PInvoke.DCompositionCreateDevice2(d2dDevice, &iid, out var devObj);
            if (hr.Failed) throw new InvalidOperationException($"DCompositionCreateDevice2 failed: hr=0x{hr.Value:X8}");
            _device = (IDCompositionDesktopDevice)devObj;

            _device.CreateTargetForHwnd(new HWND(_hwnd), new BOOL(1), out _target);
            _device.CreateVisual(out _visual);
            _target.SetRoot(_visual);

            PInvoke.DWriteCreateFactory(DWRITE_FACTORY_TYPE.DWRITE_FACTORY_TYPE_SHARED, out IDWriteFactory dw);
            _dwrite = dw;
        }
        CreateFormats(GetDpiScale());
    }

    private float GetDpiScale() => _hwnd == 0 ? 1f : PInvoke.GetDpiForWindow(new HWND(_hwnd)) / 96f;

    /// <summary>
    /// 按内容计算窗口物理尺寸（对齐安卓端 Compose 自适应布局）：
    /// 宽 = 内边距×2 + [爱心 + 间距] + bpm 数字宽 + ["bpm" + 间距]，高 = 内边距×2 + max(行高, 爱心)。
    /// DWrite 未就绪时回退旧的固定 BaseWidth×scale（仅窗口创建首帧）。
    /// </summary>
    private (float wPx, float hPx) ComputeContentSize(float dpi)
    {
        var pad = 8f * _scale * dpi;
        var heartSize = 22f * _scale * _iconScale * dpi;
        var numLineHeight = 16f * _scale * _iconScale * dpi * 1.4f;

        if (_bpmFormat is null || _bpmLabelFormat is null)
            return (Math.Max(1f, BaseWidth * _scale * dpi), Math.Max(1f, BaseHeight * _scale * dpi));

        float w = pad * 2;
        if (_showHeart)
        {
            w += heartSize + 4f * _scale * dpi; // bpmNumberMarginStart（仅爱心显示时）
        }
        w += MeasureTextWidth(_bpmText, _bpmFormat) + 2f; // +2px 缓冲：字形 advance 舍入
        if (_showBpmText)
        {
            w += 2f * _scale * dpi + MeasureTextWidth("bpm", _bpmLabelFormat);
        }

        float contentH = Math.Max(numLineHeight, _showHeart ? heartSize : 0f);
        float h = pad * 2 + contentH;
        return (Math.Max(1f, w), Math.Max(1f, h));
    }

    /// <summary>窗口物理尺寸与内容不符时更新（设置/DPI/数字变化时调用）。</summary>
    private void UpdateWindowSize()
    {
        if (_hwnd == 0) return;
        var dpi = GetDpiScale();
        var (wPx, hPx) = ComputeContentSize(dpi);
        int w = Math.Max(1, (int)Math.Ceiling(wPx));
        int h = Math.Max(1, (int)Math.Ceiling(hPx));
        unsafe
        {
            RECT r;
            PInvoke.GetWindowRect(new HWND(_hwnd), &r);
            if (r.right - r.left != w || r.bottom - r.top != h)
            {
                PInvoke.SetWindowPos(new HWND(_hwnd), new HWND(-1), 0, 0, w, h,
                    SET_WINDOW_POS_FLAGS.SWP_NOMOVE | SET_WINDOW_POS_FLAGS.SWP_NOACTIVATE | SET_WINDOW_POS_FLAGS.SWP_FRAMECHANGED);
            }
        }
    }

    /// <summary>按当前 DPI 与内容校正窗口物理尺寸（与 DComp surface 尺寸保持一致）。</summary>
    private void ApplySizeFromDpi() => UpdateWindowSize();

    private void CreateFormats(float dpi)
    {
        ReleaseFormats();
        if (_dwrite is null) return;
        _heartFormat = CreateTextFormat("Segoe MDL2 Assets",
            DWRITE_FONT_WEIGHT.DWRITE_FONT_WEIGHT_NORMAL, 22f * _scale * _iconScale * dpi);
        // 文字与爱心同比例：图标大小滑块同时缩放爱心与文字（用户偏好，区别于安卓端）
        _bpmFormat = CreateTextFormat("Segoe UI",
            DWRITE_FONT_WEIGHT.DWRITE_FONT_WEIGHT_BOLD, 16f * _scale * _iconScale * dpi);
        _bpmLabelFormat = CreateTextFormat("Segoe UI",
            DWRITE_FONT_WEIGHT.DWRITE_FONT_WEIGHT_NORMAL, 12f * _scale * _iconScale * dpi);
    }

    private unsafe IDWriteTextFormat? CreateTextFormat(string family, DWRITE_FONT_WEIGHT weight, float size)
    {
        if (_dwrite is null) return null;
        fixed (char* pFamily = family)
        fixed (char* pLocale = "en-US")
        {
            _dwrite.CreateTextFormat(new PCWSTR(pFamily), null, weight,
                DWRITE_FONT_STYLE.DWRITE_FONT_STYLE_NORMAL,
                DWRITE_FONT_STRETCH.DWRITE_FONT_STRETCH_NORMAL,
                size, new PCWSTR(pLocale), out var textFormat);
            textFormat.SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT.DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
            // 悬浮窗文本恒为单行：禁用自动换行。否则矩形宽度与文本测量宽度
            // 相等时，像素舍入会让末字符折到下一行（表现为数字"74"竖排）。
            textFormat.SetWordWrapping(DWRITE_WORD_WRAPPING.DWRITE_WORD_WRAPPING_NO_WRAP);
            return textFormat;
        }
    }

    private void ReleaseFormats()
    {
        _heartFormat = null; _bpmFormat = null; _bpmLabelFormat = null;
    }

    // ── 渲染：D2D 画到 DComp surface，DWM 直接合成（含逐像素 alpha）──

    private void EnsureSurface(uint w, uint h)
    {
        if (_device is null) return;
        if (_surface is not null && _surfaceW == w && _surfaceH == h) return;
        _device.CreateSurface(w, h,
            DXGI_FORMAT.DXGI_FORMAT_B8G8R8A8_UNORM,
            DXGI_ALPHA_MODE.DXGI_ALPHA_MODE_PREMULTIPLIED, out var surface);
        _surface = surface;
        _surfaceW = w; _surfaceH = h;
        if (_visual is not null) _visual.SetContent(_surface);
    }

    private unsafe float MeasureTextWidth(string text, IDWriteTextFormat format)
    {
        if (_dwrite is null) return 0f;
        fixed (char* p = text)
        {
            _dwrite.CreateTextLayout(new PCWSTR(p), (uint)text.Length, format, 10000f, 10000f, out var layout);
            var m = new DWRITE_TEXT_METRICS();
            layout.GetMetrics(&m);
            return m.width;
        }
    }

    private void Render()
    {
        if (_hwnd == 0 || _device is null) return;

        var dpi = GetDpiScale();
        var (wPx, hPx) = ComputeContentSize(dpi);
        EnsureSurface((uint)Math.Ceiling(wPx), (uint)Math.Ceiling(hPx));
        if (_surface is null) return;

        try
        {
            unsafe
            {
                var iid = typeof(ID2D1DeviceContext).GUID;
            System.Drawing.Point offset;
            _surface.BeginDraw(null, &iid, out var updateObj, &offset);
            var ctx = (ID2D1DeviceContext)updateObj;
            if (offset.X != 0 || offset.Y != 0)
            {
                var t = new D2D_MATRIX_3X2_F { m11 = 1f, m22 = 1f, dx = offset.X, dy = offset.Y };
                ctx.SetTransform(t);
            }
            try
            {
                var clear = new D2D1_COLOR_F { r = 0f, g = 0f, b = 0f, a = 0f };
                ctx.Clear(&clear);

                var rr = new D2D1_ROUNDED_RECT
                {
                    rect = new D2D_RECT_F { left = 0f, top = 0f, right = wPx, bottom = hPx },
                    radiusX = 12f * _scale * dpi,
                    radiusY = 12f * _scale * dpi,
                };

                // 边框：未锁定（可交互）时显示蓝色加粗边框作为状态指示；
                // 锁定（穿透）时无边框，恢复安静状态。背景始终透明。
                if (!_clickThrough)
                {
                    var bc = new D2D1_COLOR_F { r = 0x3B / 255f, g = 0x82 / 255f, b = 0xF6 / 255f, a = 1f };
                    ctx.CreateSolidColorBrush(&bc, null, out var brush);
                    ctx.DrawRoundedRectangle(&rr, brush, 2.5f, null);
                }

                // 文字（内边距 = 8 × 整体比例，对齐安卓 basePaddingDp）
                var padX = 8f * _scale * dpi;
                var padY = 8f * _scale * dpi;
                float cx = padX;

                if (_showHeart)
                {
                    var hc = new D2D1_COLOR_F
                    { r = _heartColorR / 255f, g = _heartColorG / 255f, b = _heartColorB / 255f, a = 1f };
                    ctx.CreateSolidColorBrush(&hc, null, out var brush);

                    // 爱心 = 基础 22 × 整体比例 × 图标比例（对齐安卓 iconSize 公式）
                    var heartSize = 22f * _scale * _iconScale * dpi;
                    var hx = cx;

                    // 爱心路径几何缓存：仅大小变化时重建（960 空间坐标 × size/960）
                    if (_heartGeometry is null || Math.Abs(_heartGeometrySize - heartSize) > 0.5f)
                    {
                        _heartGeometry = null;
                        ctx.GetFactory(out ID2D1Factory factory);
                        factory.CreatePathGeometry(out ID2D1PathGeometry geo);
                        geo.Open(out ID2D1GeometrySink sink);
                        sink.SetFillMode(D2D1_FILL_MODE.D2D1_FILL_MODE_ALTERNATE);
                        float s = heartSize / 960f;

                        // 爱心轮廓：Material Design "favorite" 图标，与 obs_heartrate.html
                        // 页面同款（24 空间 SVG ×40 → 960 空间）。
                        // 实际填充范围 x 80..880、y 120..854。
                        // 直线用三点重合的退化贝塞尔段表示（与本项目既有做法一致）。
                        sink.BeginFigure(new D2D_POINT_2F { x = 480.0f * s, y = 854.0f * s }, D2D1_FIGURE_BEGIN.D2D1_FIGURE_BEGIN_FILLED);
                        sink.AddBeziers(stackalloc D2D1_BEZIER_SEGMENT[]
                        {
                            // 底尖左侧（直线）
                            new() { point1 = new D2D_POINT_2F { x = 422.0f * s, y = 801.2f * s }, point2 = new D2D_POINT_2F { x = 422.0f * s, y = 801.2f * s }, point3 = new D2D_POINT_2F { x = 422.0f * s, y = 801.2f * s } },
                            // 左下大弧 → 左侧
                            new() { point1 = new D2D_POINT_2F { x = 216.0f * s, y = 614.4f * s }, point2 = new D2D_POINT_2F { x = 80.0f * s, y = 491.2f * s }, point3 = new D2D_POINT_2F { x = 80.0f * s, y = 340.0f * s } },
                            // 左瓣顶
                            new() { point1 = new D2D_POINT_2F { x = 80.0f * s, y = 216.8f * s }, point2 = new D2D_POINT_2F { x = 176.8f * s, y = 120.0f * s }, point3 = new D2D_POINT_2F { x = 300.0f * s, y = 120.0f * s } },
                            // 左瓣 → 中心凹口
                            new() { point1 = new D2D_POINT_2F { x = 369.6f * s, y = 120.0f * s }, point2 = new D2D_POINT_2F { x = 436.4f * s, y = 152.4f * s }, point3 = new D2D_POINT_2F { x = 480.0f * s, y = 203.6f * s } },
                            // 凹口 → 右瓣顶
                            new() { point1 = new D2D_POINT_2F { x = 523.6f * s, y = 152.4f * s }, point2 = new D2D_POINT_2F { x = 590.4f * s, y = 120.0f * s }, point3 = new D2D_POINT_2F { x = 660.0f * s, y = 120.0f * s } },
                            // 右瓣顶 → 右侧
                            new() { point1 = new D2D_POINT_2F { x = 783.2f * s, y = 120.0f * s }, point2 = new D2D_POINT_2F { x = 880.0f * s, y = 216.8f * s }, point3 = new D2D_POINT_2F { x = 880.0f * s, y = 340.0f * s } },
                            // 右侧 → 右下大弧 → 底尖右侧
                            new() { point1 = new D2D_POINT_2F { x = 880.0f * s, y = 491.2f * s }, point2 = new D2D_POINT_2F { x = 744.0f * s, y = 614.4f * s }, point3 = new D2D_POINT_2F { x = 538.0f * s, y = 801.6f * s } },
                            // 底尖闭合（直线）
                            new() { point1 = new D2D_POINT_2F { x = 480.0f * s, y = 854.0f * s }, point2 = new D2D_POINT_2F { x = 480.0f * s, y = 854.0f * s }, point3 = new D2D_POINT_2F { x = 480.0f * s, y = 854.0f * s } },
                        });
                        sink.EndFigure(D2D1_FIGURE_END.D2D1_FIGURE_END_CLOSED);
                        sink.Close();
                        _heartGeometry = geo;
                        _heartGeometrySize = heartSize;
                    }

                    if (_heartGeometry is not null)
                    {
                        // 垂直居中：实际填充范围 y 120..854，视觉中心 ~487
                        float visualCenter = 487f;
                        float sy = heartSize / 960f;
                        var hy = padY + (heartSize / 2f - visualCenter * sy);

                        // 在当前 transform（可能含 BeginDraw offset）基础上叠加爱心平移
                        // 与心跳动画缩放（围绕爱心中心 C=(heartSize/2,heartSize/2)，k=1 时不缩放）。
                        ctx.GetTransform(out var saved);
                        var k = _animScale;
                        var Cx = heartSize / 2f;
                        var Cy = heartSize / 2f;
                        var a = hx + Cx * (1f - k);
                        var b = hy + Cy * (1f - k);
                        var t = new D2D_MATRIX_3X2_F
                        {
                            m11 = k * saved.m11, m12 = k * saved.m12,
                            m21 = k * saved.m21, m22 = k * saved.m22,
                            dx = a * saved.m11 + b * saved.m21 + saved.dx,
                            dy = a * saved.m12 + b * saved.m22 + saved.dy,
                        };
                        ctx.SetTransform(t);
                        ctx.FillGeometry(_heartGeometry, brush);
                        ctx.SetTransform(saved);
                    }
                    // 爱心-数字间距 = bpmNumberMarginStart（4 × 整体比例，仅爱心显示时）
                    cx += heartSize + 4f * _scale * dpi;
                }

                // 心率数值：始终绘制；_showBpmText 仅控制其后的「bpm」单位标签
                if (_bpmFormat is not null)
                {
                    var wt = new D2D1_COLOR_F
                    { r = _heartColorR / 255f, g = _heartColorG / 255f, b = _heartColorB / 255f, a = 1f };
                    ctx.CreateSolidColorBrush(&wt, null, out var brush);

                    var numW = MeasureTextWidth(_bpmText, _bpmFormat);
                    // right 留 2px 余量：矩形恰好等于测量宽度时，舍入可能截断末字符
                    var numRect = new D2D_RECT_F { left = cx, top = padY, right = cx + numW + 2f, bottom = hPx - padY };
                    fixed (char* p = _bpmText)
                    {
                        ctx.DrawText(new PCWSTR(p), (uint)_bpmText.Length, _bpmFormat, &numRect, brush,
                            D2D1_DRAW_TEXT_OPTIONS.D2D1_DRAW_TEXT_OPTIONS_NONE,
                            DWRITE_MEASURING_MODE.DWRITE_MEASURING_MODE_NATURAL);
                    }

                    // 「bpm」单位标签：仅当开启显示 bpm 文字时绘制（前置间距 2 × 整体比例）
                    if (_showBpmText && _bpmLabelFormat is not null)
                    {
                        var labelLeft = cx + numW + 2f * _scale * dpi;
                        var labelRect = new D2D_RECT_F { left = labelLeft, top = padY, right = wPx - padX, bottom = hPx - padY };
                        string label = "bpm";
                        fixed (char* p = label)
                        {
                            ctx.DrawText(new PCWSTR(p), (uint)label.Length, _bpmLabelFormat, &labelRect, brush,
                                D2D1_DRAW_TEXT_OPTIONS.D2D1_DRAW_TEXT_OPTIONS_NONE,
                                DWRITE_MEASURING_MODE.DWRITE_MEASURING_MODE_NATURAL);
                        }
                    }
                }
            }
            finally
            {
                _surface.EndDraw();
            }
            _device.Commit();
            }
        }
        catch
        {
        }
    }

    // ── 消息处理 ──────────────────────────────────────────────────────────

    private static unsafe FloatWindow? GetSelf(HWND hwnd)
    {
        fixed (char* p = PropName)
        {
            var h = PInvoke.GetProp(hwnd, new PCWSTR(p));
            if (h.IsNull) return null;
            return GCHandle.FromIntPtr((nint)h.Value).Target as FloatWindow;
        }
    }

    private static LRESULT WndProc(HWND hwnd, uint msg, WPARAM wParam, LPARAM lParam)
    {
        if (msg == WM_NCHITTEST)
        {
            // 开启触摸穿透时返回 HTTRANSPARENT，系统把鼠标事件转发给 Z 序下方窗口，
            // 悬浮窗不接收后续 WM_LBUTTONDOWN/MOUSEMOVE → 不可拖拽，固定在原位。
            var hitSelf = GetSelf(hwnd);
            return (hitSelf is not null && hitSelf._clickThrough)
                ? new LRESULT((nint)PInvoke.HTTRANSPARENT)
                : new LRESULT((nint)PInvoke.HTCLIENT);
        }

        var self = GetSelf(hwnd);
        if (self is null) return PInvoke.DefWindowProc(hwnd, msg, wParam, lParam);

        unsafe
        {
            switch (msg)
            {
                case WM_LBUTTONDOWN:
                    // 仅记录按下起点，不立即进入拖拽；移动超过阈值才拖拽，
                    // 避免单纯点击被当作拖拽起点而误触发位置保存/重绘。
                    self._mouseDown = true;
                    self._dragging = false;
                    self._downX = (short)(lParam.Value & 0xFFFF);
                    self._downY = (short)((lParam.Value >> 16) & 0xFFFF);
                    self._dragOffsetX = self._downX;
                    self._dragOffsetY = self._downY;
                    PInvoke.SetCapture(hwnd);
                    break;
                case WM_MOUSEMOVE:
                    if (!self._mouseDown) break;
                    var mx = (short)(lParam.Value & 0xFFFF);
                    var my = (short)((lParam.Value >> 16) & 0xFFFF);
                    // 仅当移动超过阈值才真正开始拖拽
                    if (!self._dragging &&
                        (Math.Abs(mx - self._downX) > DragThreshold ||
                         Math.Abs(my - self._downY) > DragThreshold))
                    {
                        self._dragging = true;
                    }
                    if (!self._dragging) break;
                    System.Drawing.Point pt;
                    PInvoke.GetCursorPos(&pt);
                    self._posX = pt.X - self._dragOffsetX;
                    self._posY = pt.Y - self._dragOffsetY;
                    PInvoke.SetWindowPos(hwnd, new HWND(-1), self._posX, self._posY, 0, 0,
                        SET_WINDOW_POS_FLAGS.SWP_NOSIZE | SET_WINDOW_POS_FLAGS.SWP_NOACTIVATE);
                    break;
                case WM_LBUTTONUP:
                    // 只有真正发生过拖拽才保存位置；单纯点击不触发任何状态/重绘
                    if (self._dragging) self.SavePosition();
                    self._dragging = false;
                    self._mouseDown = false;
                    PInvoke.ReleaseCapture();
                    break;
                case WM_LBUTTONDBLCLK:
                    break;
                case WM_RBUTTONDOWN:
                    self.Close();
                    break;
                case WM_CAPTURECHANGED:
                    // 捕获被系统剥夺（Alt+Tab、其他窗口抢焦点等）时复位拖拽状态，
                    // 避免 _mouseDown 悬挂导致无按键的"幽灵拖拽"
                    self._dragging = false;
                    self._mouseDown = false;
                    break;
                case WM_DPICHANGED:
                    // 跨 DPI 显示器移动：按系统建议位置 + 内容计算尺寸重建字体/surface
                    self.CreateFormats(self.GetDpiScale());
                    self._heartGeometry = null;
                    self._heartGeometrySize = -1f;
                    var rect = *(RECT*)lParam.Value;
                    var suggested = self.ComputeContentSize(self.GetDpiScale());
                    PInvoke.SetWindowPos(hwnd, new HWND(-1), rect.left, rect.top,
                        Math.Max(1, (int)Math.Ceiling(suggested.wPx)),
                        Math.Max(1, (int)Math.Ceiling(suggested.hPx)),
                        SET_WINDOW_POS_FLAGS.SWP_NOACTIVATE | SET_WINDOW_POS_FLAGS.SWP_FRAMECHANGED);
                    self.Render();
                    break;
                case WM_DESTROY:
                    self.Cleanup();
                    break;
            }
        }
        return PInvoke.DefWindowProc(hwnd, msg, wParam, lParam);
    }

    private void SavePosition()
    {
        unsafe
        {
            RECT r;
            PInvoke.GetWindowRect(new HWND(_hwnd), &r);
            _posX = r.left; _posY = r.top; _hasPosition = true;
        }
        var s = SettingsService.Current;
        s.PositionX = _posX; s.PositionY = _posY; s.HasPosition = true;
        // 仅落盘，不触发 Changed → 不重绘，避免拖拽后悬浮窗“消失”
        SettingsService.SaveWithoutNotify();
    }

    // ── 位置/工作区 ──────────────────────────────────────────────────────

    private void PlaceOnScreen()
    {
        var hwnd = new HWND(_hwnd);
        unsafe
        {
            RECT r;
            PInvoke.GetWindowRect(hwnd, &r);
            var w = r.right - r.left;
            var h = r.bottom - r.top;

            if (!_hasPosition)
            {
                var wa = GetWorkArea();
                _posX = wa.right - w - 24;
                _posY = wa.top + 24;
            }
            else
            {
                var wa = GetWorkArea();
                _posX = Math.Clamp(_posX, wa.left, Math.Max(wa.left, wa.right - w));
                _posY = Math.Clamp(_posY, wa.top, Math.Max(wa.top, wa.bottom - h));
            }

            PInvoke.SetWindowPos(hwnd, new HWND(-1), _posX, _posY, 0, 0,
                SET_WINDOW_POS_FLAGS.SWP_NOSIZE | SET_WINDOW_POS_FLAGS.SWP_NOACTIVATE | SET_WINDOW_POS_FLAGS.SWP_FRAMECHANGED);
        }
    }

    private unsafe RECT GetWorkArea()
    {
        var pt = new System.Drawing.Point(_posX, _posY);
        var hmon = PInvoke.MonitorFromPoint(pt, MONITOR_FROM_FLAGS.MONITOR_DEFAULTTONEAREST);
        var mi = new MONITORINFO { cbSize = (uint)Marshal.SizeOf<MONITORINFO>() };
        PInvoke.GetMonitorInfo(hmon, &mi);
        return mi.rcWork;
    }

    // ── 对外接口 ──────────────────────────────────────────────────────────

    public void Show()
    {
        // Close() 已销毁窗口（DestroyWindow），重新显示时按最新设置重建
        if (_hwnd == 0)
        {
            LoadSettings();
            CreateFloatWindow();
        }
        if (_hwnd == 0) return;
        _closed = false;
        PInvoke.ShowWindow(new HWND(_hwnd), SHOW_WINDOW_CMD.SW_SHOW);
        PlaceOnScreen();
        StartAnimIfNeeded();
        // 强制重绘：隐藏期间（窗口已销毁）的设置变更未渲染，surface 需与窗口尺寸同步
        Render();
    }

    public void UpdateHeartRate(int bpm)
    {
        _bpmText = bpm.ToString();
        _animBpm = bpm;
        // 不重置动画相位：相位由 OnAnimTick 积分推进，bpm 只改变速率，
        // 心率快速刷新时节拍保持连续（此前每次刷新重置起点导致跳拍混乱）。
        StartAnimIfNeeded();
        UpdateWindowSize(); // 数字位数变化 → 宽度自适应（对齐安卓 Compose 自测内容）
        Render();
    }

    /// <summary>数据源断开时清除心率显示（回到 "--"）并停止心跳动画。</summary>
    public void ClearHeartRate()
    {
        _bpmText = "--";
        _animBpm = 0;
        StartAnimIfNeeded(); // _animBpm==0 → 内部 StopAnim（缩放归 1）
        UpdateWindowSize();
        Render();
    }

    public void Close()
    {
        if (_closed) return;
        _closed = true;
        StopAnim();
        SavePosition();
        // 真正销毁窗口 → WM_DESTROY → Cleanup：释放 GCHandle 并退订
        // SettingsService.Changed，避免隐藏后实例被强根永久持有。
        if (_hwnd != 0)
            PInvoke.DestroyWindow(new HWND(_hwnd));
    }

    private void OnSettingsChanged()
    {
        var oldScale = _scale;
        var oldIconScale = _iconScale;
        LoadSettings();

        var dpi = GetDpiScale();

        // 尺寸相关设置变化才重建字体格式与几何缓存
        if (Math.Abs(_scale - oldScale) > 0.0001f || Math.Abs(_iconScale - oldIconScale) > 0.0001f)
        {
            CreateFormats(dpi);
            _heartGeometry = null;
            _heartGeometrySize = -1f;
        }

        // 窗口尺寸随内容自适应（仅尺寸变化时才 SetWindowPos）
        UpdateWindowSize();

        if (!_closed) Render();
        StartAnimIfNeeded();
    }

    // ── 心跳动画 ──────────────────────────────────────────────────────────

    /// <summary>按需启动/停止动画定时器：仅在开启且 bpm>0 时运行。</summary>
    private void StartAnimIfNeeded()
    {
        if (_closed) return;
        if (!_showHeartbeatAnimation || _animBpm <= 0)
        {
            StopAnim();
            return;
        }
        EnsureAnimTimer();
        if (_animTimer is not null && !_animTimer.IsRunning)
        {
            _lastTick = null;         // 首帧 dt=0，避免停顿期间累积出巨幅相位跳变
            _animBpmSmooth = _animBpm; // 重新起跳时直接用当前 bpm，不从 0 爬升
            _animTimer.Interval = TimeSpan.FromMilliseconds(16);
            _animTimer.Start();
        }
    }

    private void EnsureAnimTimer()
    {
        if (_animTimer is not null || _uiDispatcher is null) return;
        _animTimer = _uiDispatcher.CreateTimer();
        if (_animTimer is null) return;
        _animTimer.Interval = TimeSpan.FromMilliseconds(16);
        _animTimer.Tick += OnAnimTick;
    }

    private void StopAnim()
    {
        if (_animTimer is not null && _animTimer.IsRunning)
            _animTimer.Stop();
        _lastTick = null;
        _animScale = 1f;
    }

    /// <summary>
    /// 定时器回调（UI 线程）：相位积分推进 → 呼吸式正弦波形 → 缩放并重绘。
    /// 整个周期连续地收缩↔舒张（纯正弦，如呼吸般平滑），频率跟随心率；
    /// bpm 经低通渐变，心率波动时节拍平滑过渡、无跳拍。
    /// </summary>
    private void OnAnimTick(DispatcherQueueTimer sender, object args)
    {
        if (!_showHeartbeatAnimation || _animBpm <= 0)
        {
            StopAnim();
            Render();
            return;
        }

        var now = DateTimeOffset.UtcNow;
        double dt = _lastTick is { } t ? (now - t).TotalSeconds : 0;
        _lastTick = now;
        if (dt < 0) dt = 0;
        else if (dt > 0.25) dt = 0.25; // 系统休眠/恢复等异常间隔，钳制防跳变

        // 动画 bpm 低通（τ≈0.5s）：显示数值保持原始 bpm，节拍速率渐变不顿挫
        _animBpmSmooth += (_animBpm - _animBpmSmooth) * (1 - Math.Exp(-dt / 0.5));

        // 相位积分：单位为周期数；bpm 变化只改速率，相位永远连续
        _phase = (_phase + dt * _animBpmSmooth / 60.0) % 1.0;

        // 呼吸式平滑正弦：整周期连续收缩/舒张（±10%），无脉冲、无突变
        const double Amp = 0.10;
        double scale = 1.0 + Amp * Math.Sin(2.0 * Math.PI * _phase);

        _animScale = (float)scale;
        Render();
    }

    private void Cleanup()
    {
        SettingsService.Changed -= OnSettingsChanged;
        StopAnim();
        _animTimer = null;
        unsafe
        {
            fixed (char* p = PropName)
            {
                var h = PInvoke.RemoveProp(new HWND(_hwnd), new PCWSTR(p));
                if (!h.IsNull)
                {
                    var g = GCHandle.FromIntPtr((nint)h.Value);
                    if (g.IsAllocated) g.Free();
                }
            }
        }
        _heartFormat = null; _bpmFormat = null; _bpmLabelFormat = null; _dwrite = null;
        _surface = null; _visual = null; _target = null; _device = null;
        _heartGeometry = null; _heartGeometrySize = -1f;
        _surfaceW = 0; _surfaceH = 0;
        _hwnd = 0;
    }
}
